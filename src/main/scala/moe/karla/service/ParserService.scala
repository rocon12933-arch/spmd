package moe.karla.service


import moe.karla.config.AppConfig
import moe.karla.handler.*
import moe.karla.repo.*
import moe.karla.handler.default.* 

import zio.*
import zio.stream.*

import io.getquill.SnakeCase
import io.getquill.jdbczio.Quill

import scala.concurrent.duration.DurationInt



class ParserService(
  config: AppConfig,
  metaRepo: MangaMetaRepo,
  pageRepo: MangaPageRepo,
  quill: Quill.H2[SnakeCase],
  nhentaiHandler: NHentaiHandler,
):

  import quill.*


  private def acquire(status: MangaMeta.Status, transform: => MangaMeta.Status) = transaction(
    for
      opt <- metaRepo.getFirstByStatusOption(status)
      _ <- opt match
        case Some(page) => metaRepo.updateStatus(page.id, transform)
        case None => ZIO.unit
    yield opt.map(_.copy(status = transform.code))
  )


  def execute = ZStream.repeatWithSchedule(0, Schedule.spaced(3 seconds)).mapZIOParUnordered(config.parallelGalleries)(_ =>

    import MangaMeta.Status.*

    (
      for
        opt <- acquire(Pending, Parsing)
        result <- opt.map(meta =>
          meta match
            case nhentai if (true) => {
              nhentaiHandler.parseAndRetrivePages(nhentai)
                .catchSome {
                  case e: NetworkError => metaRepo.updateStatus(meta.id, Failed)
                }
            }
            //case u if (u.matches("")) => hentaiMangaHandler.parseAndRetrivePages(m)
            case _ => metaRepo.updateStatus(meta.id, Failed)
        ).getOrElse(ZIO.unit)
        _ <- 
          result match 
            case (parsedMeta: MangaMeta, parsedPages: List[MangaPage]) =>
              quill.transaction(
                metaRepo.update(parsedMeta) *>
                pageRepo.batchCreate(parsedPages)
              ).flatMap(_ => ZIO.log(s"Meta is saved: ${parsedMeta.title}"))
            case _ => ZIO.unit
      yield ()
    )
    .tapError(e => ZIO.logError(s"Error: ${e.getMessage()}")))
    .runDrain



object ParserService:

  def runDaemon = ZIO.serviceWithZIO[ParserService](_.execute.forkDaemon)



object ParserServiceLive:
  
  val layer = 
    ZLayer {
      for
        config <- ZIO.service[AppConfig]
        quill <- ZIO.service[Quill.H2[SnakeCase]]
        metaRepo <- ZIO.service[MangaMetaRepo]
        pageRepo <- ZIO.service[MangaPageRepo]
        nhentai <- ZIO.service[NHentaiHandler]
      yield ParserService(config, metaRepo, pageRepo, quill, nhentai)
    }
