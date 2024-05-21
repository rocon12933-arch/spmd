package moe.karla.service



import moe.karla.config.AppConfig
import moe.karla.repo.*
import moe.karla.handler.*
import moe.karla.handler.default.*
import moe.karla.DownloadValve
import moe.karla.DownloadValve.Blocking


import zio.*
import zio.stream.*

import io.getquill.SnakeCase
import io.getquill.jdbczio.Quill

import scala.concurrent.duration.DurationInt



class DownloadHub(
  config: AppConfig,
  metaRepo: MangaMetaRepo,
  pageRepo: MangaPageRepo,
  quill: Quill.H2[SnakeCase],
  nhentaiHandler: NHentaiHandler,
  hentaiMangaHandler: HentaiMangaHandler,
):

  import quill.*


  private def acquireMeta(states: MangaMeta.State*)(newState: MangaMeta.State) = 
    transaction(
      for
        opt <- metaRepo.getFirstByStateInOption(states*)
        _ <- opt match
          case Some(meta) => metaRepo.updateState(meta.id, newState)
          case None => ZIO.unit
      yield opt
    )


  private def acquirePage(metaId: Int, state: MangaPage.State)(newState: MangaPage.State) = 
    transaction(
      for
        opt <- pageRepo.getFirstByMetaIdAndStateOption(metaId, state)
        _ <- opt match
          case Some(page) => pageRepo.updateState(page.id, newState)
          case None => ZIO.unit
      yield opt
    )

  
  def run = ZStream.repeatWithSchedule(0, Schedule.spaced(3 seconds)).mapZIOParUnordered(config.parallelGalleries)(_ =>
      
    import MangaMeta.State.*

    ZIO.whenCaseZIO(ZIO.getState[DownloadValve]) {
      case DownloadValve.Blocking => ZIO.unit
      case DownloadValve.Enabled => acquireMeta(Pending, Interrupted)(Running).mapOption(meta =>
        meta match
          case m if (config.nhentai.patterns.exists(meta.galleryUri.matches(_))) => {
            if (m.state == Pending.code)
              predefStarter(m)(nhentaiHandler)
            else if (m.state == Interrupted.code)
              predefResuming(m)(nhentaiHandler)
            else ZIO.fail(IllegalArgumentException(s"Can't handle such state: ${m.state}"))
          }
          case m if (config.hentaiManga.patterns.exists(meta.galleryUri.matches(_))) => {
            if (m.state == Pending.code)
              predefStarter(m)(hentaiMangaHandler)
            else if (m.state == Interrupted.code)
              predefResuming(m)(hentaiMangaHandler)
            else ZIO.fail(IllegalArgumentException(s"Can't handle such state: ${m.state}"))
          }
          case nhentai => ZIO.unit
          //case u if (u.matches("")) => hentaiMangaHandler.parseAndRetrivePages(m)
          case null => metaRepo.updateState(meta.id, Failed)
      )
    }
    .catchAll(e => ZIO.logError(s"Error: ${e.getMessage()}"))
  )
  .runDrain



  private def predefStarter(meta: MangaMeta)(handler: Handler) = {

    import MangaMeta.State.*

    for
      tup <- handler.parseAndRetrivePages(meta)
        .tapError(_ => ZIO.setState(Blocking) *> metaRepo.updateState(meta.id, Failed))

      (m, lp) = tup

      _ <- quill.transaction(
          metaRepo.update(m.copy(state = Parsed.code)) *> pageRepo.batchCreate(lp)
        )
        .flatMap(_ => ZIO.log(s"Meta is saved: ${m.title}"))

      signal <- FiberRef.make(true)

      _ <- metaRepo.update(m.copy(state = Running.code))

      _ <-
        ZStream.fromIterable(0 until m.totalPages).mapZIOParUnordered(config.parallelPages)(_ =>

          import MangaPage.State.*

          ZIO.whenCaseZIO(ZIO.getState[DownloadValve]) {

            case DownloadValve.Blocking => ZIO.unit
            
            case DownloadValve.Enabled =>
              ZIO.ifZIO(signal.get)(
                onTrue = 
                  acquirePage(m.id, Pending)(Running).mapOption(page =>
                    for 
                      _ <- handler.download(page).tapError(_ => 
                        transaction(
                          pageRepo.updateState(page.id, Failed) *> metaRepo.updateState(meta.id, MangaMeta.State.Failed)
                        ) *> signal.set(false)
                      )
                      _ <- transaction(
                        pageRepo.delete(page.id) *> metaRepo.getOption(page.metaId).mapOption(m =>
                          metaRepo.increaseCompletedPages(m.id) *> ZIO.when(m.completedPages + 1 >= m.totalPages)(
                            (
                              if (config.dequeueIfCompleted) metaRepo.delete(m.id)
                              else 
                                metaRepo.increaseCompletedPages(m.id) *> metaRepo.updateState(m.id, MangaMeta.State.Completed)
                            ) *> ZIO.log(s"Completed: ${m.title}") *> signal.set(false)
                          )
                        )
                      ) <* ZIO.sleep(2 seconds)
                    yield ()
                  ),
                onFalse = ZIO.unit
              )
          }
        )
        .tapError(_ => metaRepo.updateState(m.id, Failed))
        .runDrain
    yield ()
  }


  private def predefResuming(meta: MangaMeta)(handler: Handler) =

    for
      signal <- FiberRef.make(true)

      _ <- metaRepo.update(meta.copy(state = MangaMeta.State.Running.code))

      _ <- ZStream.fromIterable(meta.completedPages until meta.totalPages).mapZIOParUnordered(config.parallelPages)(_ =>

        import MangaPage.State.*

        ZIO.whenCaseZIO(ZIO.getState[DownloadValve]) {

          case DownloadValve.Blocking => ZIO.unit
          
          case DownloadValve.Enabled =>
            ZIO.ifZIO(signal.get)(
              onTrue = 
                acquirePage(meta.id, Pending)(Running).mapOption(page =>
                  for 
                    _ <- handler.download(page).tapError(_ => 
                      transaction(
                        pageRepo.updateState(page.id, Failed) *> metaRepo.updateState(meta.id, MangaMeta.State.Failed)
                      ) *> signal.set(false)
                    )
                    _ <- transaction(
                      pageRepo.delete(page.id) *> metaRepo.getOption(page.metaId).mapOption(m =>
                        metaRepo.increaseCompletedPages(m.id) *> ZIO.when(m.completedPages + 1 >= m.totalPages)(
                          (
                            if (config.dequeueIfCompleted) metaRepo.delete(m.id)
                            else 
                              metaRepo.increaseCompletedPages(m.id) *> metaRepo.updateState(m.id, MangaMeta.State.Completed)
                          ) *> ZIO.log(s"Completed: ${m.title}") *> signal.set(false)
                        )
                      )
                    ) <* ZIO.sleep(2 seconds)
                  yield ()
                ),
              onFalse = ZIO.unit
            )
        }
      )
      .runDrain
      .tapError(_ => metaRepo.updateState(meta.id, MangaMeta.State.Failed))
    yield ()




object DownloadHub:

  def runDaemon = ZIO.serviceWithZIO[DownloadHub](_.run.forkDaemon)



object DownloadHubLive:

  val layer = 
    ZLayer {
      for 
        config <- ZIO.service[AppConfig]
        quill <- ZIO.service[Quill.H2[SnakeCase]]
        metaRepo <- ZIO.service[MangaMetaRepo]
        pageRepo <- ZIO.service[MangaPageRepo]
        nhentai <- ZIO.service[NHentaiHandler]
        hentaiManga <- ZIO.service[HentaiMangaHandler]
      yield DownloadHub(config, metaRepo, pageRepo, quill, nhentai, hentaiManga)
    }