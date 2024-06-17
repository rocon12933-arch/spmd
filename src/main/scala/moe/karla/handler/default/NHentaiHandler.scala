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


  private def configureRequest(uri: String): IO[UnsupportedOperationException, Request] =
    if (config.nhentai.cloudflare.strategy == "provided-cookies")
      ZIO.succeed:
        Request.get(uri)
          .addHeader("Cookie", config.nhentai.cloudflare.providedCookies)
          .addHeader("User-Agent", config.agent)
    else
      ZIO.fail:
        UnsupportedOperationException("Current strategy for bypassing cloudflare is not implemented")
      

  extension [A <: String] (str: A)
    private def filtered = 
      str.strip()
        .replace('?', '？')
        .replace('（', '(')
        .replace('）', ')')
        .replace(":", "：")
        .replace("/", "／")
        .replace("\\", "＼")
        .replace("*", "＊")
        .replaceAll("[\\\\/:*?\"<>|]", " ")

  
  private val retryPolicy = 
    Schedule.recurs(4) && 
    Schedule.spaced(1 second) && 
    Schedule.fibonacci(600 millis)

  
  extension [R, E <: Throwable, A] (z: ZIO[R, E, A])
    private def defaultRetry = 
      z.retry(retryPolicy && Schedule.recurWhile[Throwable] {
        case _: BypassError | ParsingError => false
        case _ => true 
      })
      .mapError:
        _ match
          case b: (BypassError | ParsingError) => b
          case error => NetworkError(error)


  private def defaultMoveFile(src: String, to: String) =
    ZIO.attemptBlockingIO(
      Files.move(
        Paths.get(src), 
        Paths.get(to), 
        StandardCopyOption.REPLACE_EXISTING
      )
    )
    .retry(Schedule.recurs(3) && Schedule.spaced(2 seconds))
    .mapError(FileSystemError(_))

  
  private def defaultRemoveFile(path: String) =
    ZIO.attemptBlockingIO(
      Files.deleteIfExists(Paths.get(path))
    )
    .retry(Schedule.recurs(3) && Schedule.spaced(2 seconds))
    .mapError(FileSystemError(_))

  

  def parseAndRetrivePages(meta: MangaMeta) =
    for
      client <- ZIO.service[Client]

      req <- configureRequest(meta.galleryUri)

      _ <- ZIO.log(s"Parsing: '${meta.galleryUri}'")

      body <- 
        client.request(req)
          .flatMap { resp =>
            if (resp.status.code == 403)
              ZIO.fail(BypassError(s"Cloudflare blocking presents while parsing: '${meta.galleryUri}'"))
            else if (resp.status.code == 404)
              ZIO.fail(ParsingError(s"404 not found presents while parsing: '${meta.galleryUri}'"))
            else resp.body.asString
          }
          .defaultRetry
          .map(Jsoup.parse(_).body)
          

      titleString <- ZIO.fromOption(
        Option(body.selectFirst("h2.title")).orElse(Option(body.selectFirst("h1.title"))).map(_.wholeText)
      ).mapError(_ => ParsingError(s"Extracting title failed while parsing '${meta.galleryUri}'"))

      pages <- ZIO.attempt(
        body.select("span.tags > a.tag > span.name").last.text.toInt
      ).mapError(_ => ParsingError(s"Extracting pages failed while parsing '${meta.galleryUri}'"))

      parsedMeta = meta.copy(title = Some(titleString), totalPages = pages)

      parsedPages = (1 to parsedMeta.totalPages).map(p =>

        val u = if (parsedMeta.galleryUri.last == '/') parsedMeta.galleryUri.dropRight(1) else parsedMeta.galleryUri

        MangaPage(
          0,
          parsedMeta.id,
          s"${u}/${p}/",
          p,
          s"${titleString.filtered}",
          MangaPage.State.Pending.code,
        )
      )
      .toList

    yield (parsedMeta, parsedPages)


  def download(page: MangaPage) = {

    def processFileExtensionName(bytes: List[Byte]): Either[List[Byte], String] =
      bytes match
        case -1 :: -40 :: -1 :: _ => Right("jpg")
        // 0x89 = 1000 1001 -> 1111 0110 -> 1111 0110 + 1b = 1111 0111 = -119
        //case -119 :: 80 :: 78 :: 71 :: 13 :: 10 :: 26 :: 10 :: Nil => "png"
        case -119 :: 80 :: 78 :: _ => Right("png")
        // 0x47 = 0100 0111 -> self = 71
        case 71 :: 73 :: 70 :: _ => Right("gif")
        
        case _ => Left(bytes)


    for
      client <- ZIO.service[Client]
      
      _ <- ZIO.log(s"Parsing: '${page.pageUri}'")

      req <- configureRequest(page.pageUri)

      body <- 
        client.request(req)
          .flatMap { resp =>
            if (resp.status.code == 403) 
              ZIO.fail(BypassError(s"Cloudflare blocking presents while parsing: '${page.pageUri}'"))
            else if (resp.status.code == 404)
              ZIO.fail(ParsingError(s"404 not found presents while parsing: '${page.pageUri}'"))
            else resp.body.asString
          }
          .defaultRetry
          .map(Jsoup.parse(_).body)

      imgUri <- ZIO.attempt(
        body.select("section#image-container > a > img").first.attr("src")
      ).mapError(_ => ParsingError(s"Extracting image uri failed while parsing '${page.pageUri}'"))


      ref <- Ref.make(Either.cond[List[Byte], String](true, "invalid", List[Byte]()))

      fireSink = ZSink.collectAllN[Byte](7).map(_.toList).mapZIO(bytes =>
        ref.set:
          if(config.nhentai.download.fileSignatureCheck.enabled)
            processFileExtensionName(bytes)
          else
            Right(imgUri.substring(imgUri.lastIndexOf('.') + 1))
      )

      _ <- ZIO.attemptBlockingIO(Files.createDirectories(Paths.get(s"${config.downPath}/${page.title}")))

      path = s"${config.downPath}/${page.title}/${page.pageNumber}"

      _ <- ZIO.log(s"Downloading: '${imgUri}'")

      _ <- 
        client.request(Request.get(imgUri))
          .flatMap { resp =>
            if (resp.status.code == 403) 
              ZIO.fail(BypassError(s"Cloudflare blocking presents while trying to download: '${imgUri}'"))
            else if (resp.status.code == 404)
              ZIO.fail(ParsingError(s"404 not found presents while trying to download: '${imgUri}'"))
            else ZIO.succeed(resp.body.asStream)
          }
          .flatMap(_.timeout(120 seconds).tapSink(fireSink).run(ZSink.fromFileName(path)))
          .tapError(e => ZIO.logError(s"Exception is raised when downloading: '${imgUri}'."))
          .defaultRetry
          .tapError: e =>
            ZIO.logError(s"Maximum number of retries has been reached while downloading: '${imgUri}'. abort.")


      preExt <- ref.get

      _ <- ZIO.whenCase(preExt):
        case Left(list) =>
          ZIO.logWarning(s"Unknown file signature is detected, " +
            s"signature: '${list.map(String.valueOf(_)).reduce((a, b) => s"${a},${b}")}', " +
            s"page uri: '${page.pageUri}', " +
            s"image uri: '${imgUri}', " +
            s"extension name falls back to '${config.nhentai.download.fileSignatureCheck.fallbackExtensionName}'")

      ext = preExt.getOrElse(config.nhentai.download.fileSignatureCheck.fallbackExtensionName)

      _ <- defaultMoveFile(path, s"${path}.${ext}")

      _ <- ZIO.log(s"Saved: '${imgUri}' as '${path}.${ext}'")
      
    yield page
  }
  

object NHentaiHandlerLive:

  val layer = ZLayer.derive[NHentaiHandler]
