package moe.karla.service



import moe.karla.config.AppConfig
import moe.karla.repo.*
import moe.karla.handler.*
import moe.karla.handler.default.*


import zio.*
import zio.stream.*

import io.getquill.SnakeCase
import io.getquill.jdbczio.Quill

import scala.concurrent.duration.DurationInt



class DownloaderService(
  config: AppConfig,
  metaRepo: MangaMetaRepo,
  pageRepo: MangaPageRepo,
  quill: Quill.H2[SnakeCase],
  nhentaiHandler: NHentaiHandler,
):
  
  import quill.*

  private def acquire(status: MangaPage.Status, transform: => MangaPage.Status) = transaction(
    for
      opt <- pageRepo.getFirstByStatusOption(status)
      _ <- opt match
        case Some(page) => pageRepo.updateStatus(page.id, transform)
        case None => ZIO.unit
    yield opt.map(_.copy(status = transform.code))
  )//.mapError(SQLException(_))


  def execute = ZStream.repeatWithSchedule(0, Schedule.spaced(5 seconds)).mapZIOParUnordered(config.parallelPages)(_ =>
    import MangaPage.Status.*
    (
      for
        opt <- acquire(Pending, Running)
        down <- opt match
          case Some(page) => {
            nhentaiHandler.download(page).tapError(_ => pageRepo.updateStatus(page.id, Failed))
          }
          case None => ZIO.unit
        _ <- down match
          case id: Int => 
            transaction( 
              for 
                //_ <- pageRepo.updateStatus(id, Completed)
                _ <- pageRepo.delete(id)
                metaOpt <- metaRepo.getOption(opt.get.metaId)
                meta = metaOpt.get
                _ <- if (meta.completedPages + 1 < meta.totalPages) metaRepo.increaseCompletedPages(meta.id) 
                  else {
                    if (config.dequeueIfCompleted)
                      metaRepo.delete(meta.id)
                    else 
                      metaRepo.increaseCompletedPages(meta.id) *> metaRepo.updateStatus(meta.id, MangaMeta.Status.Completed)
                  } <* ZIO.log(s"Completed: ${meta.title}")
              yield ()
            )
          case _ => ZIO.unit
      yield ()
    )
    .tapError(e => ZIO.logError(s"Error: ${e.getMessage()}"))
  )
  .runDrain



object DownloaderService:

  def runDaemon = ZIO.serviceWithZIO[DownloaderService](_.execute.forkDaemon)



object DownloaderServiceLive:
  /*
  val layer = ZLayer.fromFunction(
    (
      config: AppConfig, 
      nhentaiHandler: NHentaiHandler, 
      hentaiMangaHandler: HentaiMangaHandler, 
      pageRepo: MangaPageRepo
    ) => DownloaderService(config, nhentaiHandler, hentaiMangaHandler, pageRepo)
  )
  */
  val layer = 
    ZLayer {
      for 
        config <- ZIO.service[AppConfig]
        quill <- ZIO.service[Quill.H2[SnakeCase]]
        metaRepo <- ZIO.service[MangaMetaRepo]
        pageRepo <- ZIO.service[MangaPageRepo]
        nhentai <- ZIO.service[NHentaiHandler]
      yield DownloaderService(config, metaRepo, pageRepo, quill, nhentai)
    }