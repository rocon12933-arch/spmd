package moe.karla.handler.default


import moe.karla.config.*
import moe.karla.repo.*
import moe.karla.handler.*

import zio.*
import zio.stream.*
import zio.http.*
import zio.http.netty.ChannelFactories.Client


import io.getquill.SnakeCase
import io.getquill.jdbczio.Quill

import org.jsoup.Jsoup

import java.nio.file.*
import java.sql.SQLException
import java.net.URI




class HentaiMangaHandler(
  config: AppConfig
):


  private val retryPolicy = 
    Schedule.recurs(5) || 
    Schedule.spaced(1 second) || 
    Schedule.fibonacci(200 millis)


  private def configureRequest(uri: String) = 
    Request.get(uri).addHeader("User-Agent", config.agent)

  
  private def fileExtJudge(bytes: List[Byte]) =
    bytes match
      //0x50 = 0101 0000 -> self = 80
      //0x4B = 0100 1011 -> self = 75
      //0x30 = 0011 0000 -> self = 48
      case 80 :: 75 :: 48 :: _ => "zip"
      //0x52 = 0101 0010 -> self = 82
      //0x61 = 0110 0001 -> self = 97
      //0x72 = 0111 0010 -> self = 114
      case 82 :: 97 :: 114 :: _ => "rar"
      //0x37 = 0011 0110 -> self = 55
      //0x7A = 0111 1010 -> self = 122
      //0xBC = 1011 1100 -> 1100 0011 -> 1100 0100 = -68
      //0xAF
      //0x27 
      //0x1C
      case 55 :: 122 :: -68 :: _ => "7z"
      
      case _ => "unknown"

  
  extension [A <: String] (str: A)
    def filtered = 
      str.strip()
        .replace('?', '？')
        .replace('（', '(')
        .replace('）', ')')
        .replace(":", "：")
        .replace("/", "／")
        .replace("\\", "＼")
        .replace("*", "＊")
        .replaceAll("[\\\\/:*?\"<>|]", " ")


  def parseAndRetrivePagesNew(meta: MangaMeta): ZIO[zio.http.Client & Scope, Exception, (MangaMeta, List[MangaPage])] =
    for
      client <- ZIO.service[Client]

      request = configureRequest(meta.galleryUri)

      _ <- ZIO.log(s"Parsing: ${meta.galleryUri}")

      body <-
        client.request(request)
          .flatMap(_.body.asString)
          .retry(retryPolicy)
          .mapError(e => NetworkError(e))
          .map(Jsoup.parse(_).body)

      title <- ZIO.fromOption(
        Option(body.selectFirst("h2.title")).orElse(Option(body.selectFirst("h1.title"))).map(_.wholeText)
      ).mapError(_ => ParsingError(s"Retrive title from meta failed while parsing { ${meta.galleryUri} }"))


      downloadPage <- ZIO.fromOption(
        Option(body.selectFirst("h2.title")).orElse(Option(body.selectFirst("h1.title"))).map(_.wholeText)
      ).mapError(_ => ParsingError(s"Retrive title from meta failed while parsing { ${meta.galleryUri} }"))

      pages = 1

      parsedMeta = meta.copy(state = MangaMeta.State.Parsed.code, title = title, totalPages = pages)

      parsedPages =
        List(
          MangaPage(
            0,
            parsedMeta.id,
            downloadPage,
            1,
            s"${parsedMeta.title.filtered}",
            MangaPage.State.Pending.code,
          )
        )
      
    yield (parsedMeta, parsedPages)


  def download(page: MangaPage): ZIO[zio.http.Client & Scope, Exception, MangaPage] =
    for
      client <- ZIO.service[Client]
      
      _ <- ZIO.log(s"Parsing: ${page.pageUri}")

      body <- 
        client.request(configureRequest(page.pageUri))
        .flatMap(_.body.asString)
        .retry(retryPolicy)
        .mapError(NetworkError(_))
        .map(Jsoup.parse(_).body)

      downloadUri <- ZIO.attempt(
        body.select("section#image-container > a > img").first.attr("src")
      )
      .map(uri => new URI(uri).toURL())
      .map(url => s"https://${url.getHost()}${url.getPath()}")
      .mapError(_ => ParsingError(s"Retrive image from page failed while parsing { ${page.pageUri} }"))

      ref <- FiberRef.make("unknown")

      fireSink = ZSink.collectAllN[Byte](10).map(_.toList).mapZIO(bytes => ref.set(fileExtJudge(bytes)))

      _ <- ZIO.attemptBlockingIO(Files.createDirectories(Paths.get(s"${config.downPath}/${page.path}")))

      path = s"${config.downPath}/${page.path}/${page.pageNumber}"

      _ <- ZIO.log(s"Downloading: ${downloadUri}")

      _ <- client.request(Request.get(downloadUri))
        .map(_.body.asStream)
        .flatMap(_.timeout(6 hours).tapSink(fireSink).run(ZSink.fromFileName(path)))
        .retry(retryPolicy)
        .mapError(NetworkError(_))

      ext <- ref.get

      _ <- ZIO.attemptBlockingIO(
        Files.move(
          Paths.get(path), 
          Paths.get(s"${path}.${ext}"), 
          StandardCopyOption.REPLACE_EXISTING
        )
      )
      .retry(Schedule.recurs(3) || Schedule.spaced(2 seconds))
      .mapError(FileSystemError(_))

      _ <- ZIO.log(s"Saved: {${downloadUri}} as {${path}.${ext}}")
      
    yield page




object HentaiMangaHandlerLive:
  val layer = 
    ZLayer {
      for
        config <- ZIO.service[AppConfig]
      yield HentaiMangaHandler(config)
    }
