package moe.karla.handler.default


import moe.karla.config.*
import moe.karla.repo.*
import moe.karla.handler.*

import zio.*
import zio.http.*
import zio.stream.*
import zio.http.netty.ChannelFactories.Client

import io.getquill.SnakeCase
import io.getquill.jdbczio.Quill

import org.jsoup.Jsoup

import java.nio.file.*
import java.sql.SQLException
import java.io.IOException



class NHentaiHandler(
  config: AppConfig,
) extends moe.karla.handler.Handler:


  private val retryPolicy = 
    Schedule.recurs(4) && 
    Schedule.spaced(1 second) &&
    Schedule.fibonacci(600 millis)

  /* 
  private def fileExtJudge(bytes: List[Byte]) =
    bytes match
      case -119 :: 80 :: 78 :: 71 :: 13 :: 10 :: 26 :: 10 :: Nil => "png"
      case -1 :: -40 :: -1 :: _ => "jpg"
      case _ => "unknown"
   */

  private def fileExtJudge(bytes: List[Byte]) =
    bytes match
      case -1 :: -40 :: -1 :: _ => "jpg"
      // 0x89 = 1000 1001 -> 1111 0110 -> 1111 0110 + 1b = 1111 0111 = -119
      //case -119 :: 80 :: 78 :: 71 :: 13 :: 10 :: 26 :: 10 :: Nil => "png"
      case -119 :: 80 :: 78 :: _ => "png"
      // 0x47 = 0100 0111 -> self = 71
      case 71 :: 79 :: 70 :: _ => "gif"
      

      case _ => "unknown"


  private def configureRequest(uri: String) = 
    Request.get(uri)
      .addHeader("Cookie", config.nhentai.cloudflare.cookies)
      .addHeader("User-Agent", config.agent)
      

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

  

  def parseAndRetrivePages(meta: MangaMeta) =
    for
      client <- ZIO.service[Client]

      request = configureRequest(meta.galleryUri)

      _ <- ZIO.log(s"Parsing: ${meta.galleryUri}")

      body <- 
        client.request(request)
          .flatMap { resp =>
            if (resp.status.code == 403) ZIO.fail(BypassError(s"A cloudflare blocking presents while parsing: ${meta.galleryUri}"))
            else resp.body.asString
          }
          .retry(retryPolicy && Schedule.recurWhile[Throwable] {
            case _: BypassError => false
            case _ => true 
          })
          .mapError(e =>
            e match
              case b: BypassError => b
              case _ => NetworkError(e) 
          )
          .map(Jsoup.parse(_).body)
          

      title <- ZIO.fromOption(
        Option(body.selectFirst("h2.title")).orElse(Option(body.selectFirst("h1.title"))).map(_.wholeText)
      ).mapError(_ => ParsingError(s"Retrive title from meta failed while parsing { ${meta.galleryUri} }"))

      pages <- ZIO.attempt(
        body.select("span.tags > a.tag > span.name").last.text.toInt
      ).mapError(_ => ParsingError(s"Retrive pages from meta failed while parsing { ${meta.galleryUri} }"))

      parsedMeta = meta.copy(state = 2, title = title, totalPages = pages)

      parsedPages = (1 to parsedMeta.totalPages).map(p =>

        val u = if (parsedMeta.galleryUri.last == '/') parsedMeta.galleryUri.dropRight(1) else parsedMeta.galleryUri

        MangaPage(
          0,
          parsedMeta.id,
          s"${u}/${p}/",
          p,
          s"${parsedMeta.title.filtered}",
          MangaPage.State.Pending.code,
        )
      )
      .toList

    yield (parsedMeta, parsedPages)


  def download(page: MangaPage) = 
    for
      client <- ZIO.service[Client]
      
      _ <- ZIO.log(s"Parsing: ${page.pageUri}")

      body <- 
        client.request(configureRequest(page.pageUri))
        .flatMap(_.body.asString)
        .retry(retryPolicy)
        .mapError(NetworkError(_))
        .map(Jsoup.parse(_).body)

      imgUri <- ZIO.attempt(
        body.select("section#image-container > a > img").first.attr("src")
      ).mapError(_ => ParsingError(s"Retrive image from page failed while parsing { ${page.pageUri} }"))

      ref <- FiberRef.make("unknown")

      fireSink = ZSink.collectAllN[Byte](10).map(_.toList).mapZIO(bytes => ref.set(fileExtJudge(bytes)))

      _ <- ZIO.attemptBlockingIO(Files.createDirectories(Paths.get(s"${config.downPath}/${page.path}")))

      path = s"${config.downPath}/${page.path}/${page.pageNumber}"

      _ <- ZIO.log(s"Downloading: ${imgUri}")

      _ <- client.request(Request.get(imgUri))
        .map(_.body.asStream)
        .flatMap(_.timeout(90 seconds).tapSink(fireSink).run(ZSink.fromFileName(path)))
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

      _ <- ZIO.log(s"Saved: {${imgUri}} as {${path}.${ext}}")
      
    yield page


object NHentaiHandlerLive:
  val layer = 
    ZLayer {
      for
        config <- ZIO.service[AppConfig]
      yield NHentaiHandler(config)
    }
