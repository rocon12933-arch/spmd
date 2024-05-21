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
import java.util.UUID




class HentaiMangaHandler(
  config: AppConfig
) extends moe.karla.handler.Handler:


  private val retryPolicy = 
    Schedule.recurs(5) || 
    Schedule.spaced(1 second) || 
    Schedule.fibonacci(200 millis)

  
  private def configureRequest(url: URL) =
    Request.get(url).addHeader("User-Agent", config.agent)


  private def fileExtJudge(bytes: List[Byte]): Either[List[Byte], String] =
    bytes match
      //0x50 = 0101 0000 -> self = 80
      //0x4B = 0100 1011 -> self = 75
      //0x03 = 0000 0011 -> self = 3
      case 80 :: 75 :: 3 :: _ => Right("zip")
      //0x52 = 0101 0010 -> self = 82
      //0x61 = 0110 0001 -> self = 97
      //0x72 = 0111 0010 -> self = 114
      case 82 :: 97 :: 114 :: _ => Right("rar")
      //0x37 = 0011 0110 -> self = 55
      //0x7A = 0111 1010 -> self = 122
      //0xBC = 1011 1100 -> 1100 0011 -> 1100 0100 = -68
      case 55 :: 122 :: -68 :: _ => Right("7z")
      
      case _ => Left(bytes)
    
  
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


  def parseAndRetrivePages(meta: MangaMeta): ZIO[zio.http.Client & Scope, Exception, (MangaMeta, List[MangaPage])] =
    for
      client <- ZIO.service[Client]

      _ <- ZIO.log(s"Parsing: ${meta.galleryUri}")

      url <- ZIO.fromEither(URL.decode(meta.galleryUri))

      body <-
        client.request(configureRequest(url))
          .flatMap(_.body.asString)
          .retry(retryPolicy)
          .mapError(e => NetworkError(e))
          .map(Jsoup.parse(_).body)

      title <- ZIO.fromOption(
        Option(body.selectFirst("div#bodywrap > h2")).map(_.wholeText)
      ).mapError(_ => ParsingError(s"Extracting title failed while parsing { ${meta.galleryUri} }"))


      downloadPage <- ZIO.fromOption(
        Option(body.selectFirst("div#ads > a.btn")).map(_.attr("href")).map(path => 
          s"${url.scheme.get.encode}://${url.host.get}${path}"
        )
      ).mapError(_ => ParsingError(s"Extracting download page failed while parsing { ${meta.galleryUri} }"))

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

      pageUrl <- ZIO.fromEither(URL.decode(page.pageUri))

      body <-
        client.request(configureRequest(pageUrl))
        .flatMap(_.body.asString)
        .retry(retryPolicy)
        .mapError(NetworkError(_))
        .map(Jsoup.parse(_).body)

      archiveUri <- ZIO.attempt(
        body.select("div#adsbox > a.down_btn").first.attr("href")
      )
      .map(uri => s"https:${uri}")
      .map(uri => uri.substring(0, uri.indexOf("?n=")))
      .mapError(_ => ParsingError(s"Extracting archive from page failed while parsing { ${page.pageUri} }"))


      ref <- FiberRef.make(Either.cond[List[Byte], String](true, "unknown", List[Byte]()))

      fireSink = ZSink.collectAllN[Byte](10).map(_.toList).mapZIO(bytes => ref.set(fileExtJudge(bytes)))

      downloadPath = s"${config.downPath}/${UUID.randomUUID().toString()}"

      _ <- ZIO.log(s"Downloading: ${archiveUri}")

      _ <- client.request(Request.get(archiveUri))
        .map(_.body.asStream)
        .flatMap(_.timeout(6 hours).tapSink(fireSink).run(ZSink.fromFileName(downloadPath)))
        .retry(retryPolicy)
        .mapError(NetworkError(_))

      preExt <- ref.get

      _ <- ZIO.when(preExt.isLeft)(
        ZIO.logWarning(s"Unknown file extension is detected, archive uri: { ${archiveUri} }, page uri: { ${page.pageUri} }")
      )

      ext = preExt.getOrElse("unknown")

      preferedPath = s"${config.downPath}/${page.title}.${ext}"

      _ <- ZIO.attemptBlockingIO(
        Files.move(
          Paths.get(downloadPath), 
          Paths.get(preferedPath), 
          StandardCopyOption.REPLACE_EXISTING
        )
      )
      .retry(Schedule.recurs(3) || Schedule.spaced(2 seconds))
      .mapError(FileSystemError(_))

      _ <- ZIO.log(s"Saved: { ${archiveUri} } as { ${preferedPath} }")
      
    yield page




object HentaiMangaHandlerLive:
  val layer = 
    ZLayer {
      for
        config <- ZIO.service[AppConfig]
      yield HentaiMangaHandler(config)
    }
