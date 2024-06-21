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

import org.apache.commons.compress.archivers.examples.Expander

import java.nio.file.*
import java.sql.SQLException
import java.util.UUID



class HentaiMangaHandler(
  config: AppConfig
) extends moe.karla.handler.Handler:

  
  private val expander = new Expander


  private def configureRequest(url: URL) =
    Request.get(url).addHeader("User-Agent", config.agent)
    
  
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



  def parseAndRetrivePages(meta: MangaMeta): ZIO[zio.http.Client & Scope, Exception, (MangaMeta, List[MangaPage])] =
    for
      client <- ZIO.service[Client]

      _ <- ZIO.log(s"Parsing: '${meta.galleryUri}'")

      url <- ZIO.fromEither(URL.decode(meta.galleryUri))

      body <-
        client.request(configureRequest(url))
          .flatMap(_.body.asString)
          .retry(retryPolicy)
          .mapError(NetworkError(_))
          .map(Jsoup.parse(_).body)

      titleString <- ZIO.fromOption(
        Option(body.selectFirst("div#bodywrap > h2"))
          .map(_.wholeText.filtered)
          .collect { case s: String if s.size > 0 => s }
      ).mapError(_ => ParsingError(s"Extracting title failed while parsing '${meta.galleryUri}'"))


      downloadPage <- ZIO.fromOption(
        Option(body.selectFirst("div.download_btns > a.btn")).map(_.attr("href")).map(path => 
          s"${url.scheme.get.encode}://${url.host.get}${path}"
        )
      ).mapError(_ => ParsingError(s"Extracting download page failed while parsing '${meta.galleryUri}'"))

      parsedMeta = meta.copy(title = Some(titleString), totalPages = 1)

      parsedPages =
        List(
          MangaPage(
            0,
            parsedMeta.id,
            downloadPage,
            1,
            s"${titleString.filtered}",
            MangaPage.State.Pending.code,
          )
        )
      
    yield (parsedMeta, parsedPages)


  def download(page: MangaPage): ZIO[zio.http.Client & Scope, Exception, Option[Long]] = {

    def processFileExtensionName(bytes: List[Byte]): Either[List[Byte], String] =
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

    for
      client <- ZIO.service[Client]
      
      _ <- ZIO.log(s"Parsing: '${page.pageUri}'")

      pageUrl <- ZIO.fromEither(URL.decode(page.pageUri))

      body <-
        client.request(configureRequest(pageUrl))
        .flatMap(_.body.asString)
        .retry(retryPolicy)
        .mapError(NetworkError(_))
        .map(Jsoup.parse(_).body)

      archiveUri <- 
        ZIO.attempt(
          body.select("div#adsbox > a.down_btn").first.attr("href")
        )
        .map(uri => s"https:${uri}")
        .map(uri => uri.substring(0, uri.indexOf("?n=")))
        .mapError(_ => ParsingError(s"Extracting archive uri from page failed while parsing '${page.pageUri}'"))


      ref <- Ref.make(Either.cond[List[Byte], String](true, "unknown", List[Byte]()))

      fireSink = ZSink.collectAllN[Byte](10).map(_.toList).mapZIO(bytes => ref.set(processFileExtensionName(bytes)))

      downloadPath = s"${config.downPath}/${UUID.randomUUID().toString()}.tmp"

      _ <- ZIO.log(s"Downloading: '${archiveUri}'")

      length <- 
        client.request(Request.get(archiveUri))
          .flatMap { resp =>
            if (resp.status.code == 404)
              ZIO.fail(ParsingError(s"404 not found presents while trying to download: '${archiveUri}'"))
            else if (resp.status.code > 500)
              ZIO.fail(ParsingError(s"50x presents while trying to download: '${archiveUri}'"))
            else ZIO.succeed(resp.body.asStream)
          }
          .flatMap(_.timeout(6 hours).tapSink(fireSink).run(ZSink.fromFileName(downloadPath)))
          .tapError(e => ZIO.logError(s"Exception is raised while downloading: '${archiveUri}'."))
          .retry(retryPolicy && Schedule.recurWhile[Throwable] {
            case _: ParsingError => false
            case _ => true
          })
          .mapError:
            _ match
              case b: ParsingError => b
              case error => NetworkError(error)
          .tapError: _ =>
            ZIO.logError(s"Maximum number of retries has been reached while downloading: '${archiveUri}'. abort.") *>
              defaultRemoveFile(downloadPath)


      preExt <- ref.get

      _ <- ZIO.when(preExt.isLeft)(
        ZIO.logWarning(s"Unknown file signature is detected, archive uri: '${archiveUri}', page uri: '${page.pageUri}'")
      )

      ext = preExt.getOrElse("unknown")

      savedPathRef <- Ref.make(s"${config.downPath}/${page.title}.${ext}")

      _ <-
        if (config.hentaiManga.download.decompress) {
          ext match
            case "zip" => 
              ZIO.attemptBlockingIO:
                expander.expand(Paths.get(downloadPath), Paths.get(s"${config.downPath}/${page.title}"))
              .catchAll: _ => 
                ZIO.logWarning(s"Decompress failure presents: '${downloadPath}', fall back to compressed file.") *>
                  savedPathRef.get.flatMap: p =>
                    defaultMoveFile(downloadPath, p)
              .flatMap: _ =>
                ZIO.attemptBlockingIO(Files.deleteIfExists(Paths.get(downloadPath))) *>
                  savedPathRef.set(s"${config.downPath}/${page.title}")
            case _ =>
              ZIO.logWarning(s"Decompress supports only zip archives currently: '${downloadPath}'") *>
                savedPathRef.get.flatMap: p =>
                  defaultMoveFile(downloadPath, p)
        }
        else
          savedPathRef.get.flatMap: p =>
            defaultMoveFile(downloadPath, p)
      
      savedPath <- savedPathRef.get

      _ <- ZIO.log(s"Saved: '${archiveUri}' as '${savedPath}', downloaded size: ${String.format("%.2f", length.toFloat / 1024 / 1024)} MB")
        
      _ <- ZIO.sleep(14 seconds)
      
    yield Some(length)
  }



object HentaiMangaHandlerLive:
  /*
  val layer = 
    ZLayer {
      for
        config <- ZIO.service[AppConfig]
      yield HentaiMangaHandler(config)
    }
  */
  val layer = ZLayer.derive[HentaiMangaHandler]
