package moe.karla.service



import moe.karla.config.AppConfig
import moe.karla.misc.ProgramState
import moe.karla.repo.*
import moe.karla.handler.*
import moe.karla.handler.default.*


import zio.*
import zio.stream.*

import io.getquill.SnakeCase
import io.getquill.jdbczio.Quill

import scala.concurrent.duration.DurationInt



class DownloadHub(
  config: AppConfig,
  programState: ProgramState,
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

    ZIO.whenCaseZIO(programState.downValve.get) {
      case 0 => acquireMeta(Pending)(Running).mapOption(
        _ match
          case meta if (config.nhentai.patterns.exists(meta.galleryUri.matches(_))) => {
            if (meta.isParsed) predefResuming(meta)(nhentaiHandler)
            else predefStarter(meta)(nhentaiHandler)
          }
          case meta if (config.hentaiManga.patterns.exists(meta.galleryUri.matches(_))) => {
            if (meta.isParsed) predefResuming(meta)(hentaiMangaHandler)
            else predefStarter(meta)(hentaiMangaHandler)
          }
          case m => 
            metaRepo.update(m.copy(state = Failed.code, cause = Some("No compatible handler"))) *> 
            ZIO.logError(s"No compatible handler for '${m.galleryUri}'")
      )
      case _ => ZIO.unit
    }
    .catchAll(e => ZIO.logError(s"Error: ${e.getMessage()}"))
  )
  .runDrain



  private def predefStarter(meta: MangaMeta)(handler: Handler) = {

    import MangaMeta.State.*

    for
      tup <- handler.parseAndRetrivePages(meta)
        .tapError: e =>
          ZIO.logError(e.getMessage()) *>
            metaRepo.update(meta.copy(isParsed = false, state = Failed.code, cause = Some(e.getMessage())))
        .onInterrupt:
          metaRepo.update(meta.copy(isParsed = false, state = Pending.code)).orDie

      (m, lp) = tup

      _ <- 
        quill.transaction(
          metaRepo.update(m.copy(isParsed = true, state = Running.code)) *> 
            pageRepo.batchCreate(lp.map(_.copy(state = Pending.code)))
        )
        .flatMap(_ => ZIO.log(s"Meta is saved: '${m.title}'"))

      signal <- FiberRef.make(true)

      _ <-
        ZStream.fromIterable(0 until m.totalPages).mapZIOParUnordered(config.parallelPages)(_ =>

          import MangaPage.State.*

          ZIO.whenCaseZIO(programState.downValve.get) {
            case 0 =>
              ZIO.ifZIO(signal.get)(
                onTrue = 
                  acquirePage(m.id, Pending)(Running).mapOption(page =>
                    for 
                      _ <- handler.download(page).onInterrupt(_ => pageRepo.updateState(page.id, Pending).orDie).tapError(e => 
                        transaction(
                          pageRepo.updateState(page.id, Failed) *> 
                            metaRepo.getOption(page.metaId).mapOption(modifiedMeta =>
                              metaRepo.update(modifiedMeta.copy(state = MangaMeta.State.Failed.code, cause = Some(e.getMessage())))
                            )
                        ) *> signal.set(false)
                      )
                      _ <- transaction(
                        pageRepo.delete(page.id) *> metaRepo.getOption(page.metaId).mapOption(m =>
                          metaRepo.increaseCompletedPages(m.id) *> ZIO.when(m.completedPages + 1 >= m.totalPages)(
                            (
                              if (config.dequeueIfCompleted) metaRepo.delete(m.id)
                              else metaRepo.updateState(m.id, MangaMeta.State.Completed)
                            ) *>
                              ZIO.log(s"Completed: '${m.title}'") *> signal.set(false)
                          )
                        )
                      ) <* ZIO.sleep(2 seconds)
                    yield ()
                  ),
                onFalse = ZIO.unit
              )
            case _ => ZIO.unit
          }
        )
        .runDrain
        .tapError(_ => metaRepo.updateState(m.id, Failed))
    yield ()
  }


  private def predefResuming(meta: MangaMeta)(handler: Handler) =

    for
      signal <- FiberRef.make(true)

      _ <- 
        ZStream.fromIterable(meta.completedPages until meta.totalPages).mapZIOParUnordered(config.parallelPages)(_ =>

          import MangaPage.State.*

          ZIO.whenCaseZIO(programState.downValve.get) {

            case 0 =>
              ZIO.ifZIO(signal.get)(
                onTrue = 
                  acquirePage(meta.id, Pending)(Running).mapOption(page =>
                    for 
                      _ <- handler.download(page).onInterrupt(_ => pageRepo.updateState(page.id, Pending).orDie).tapError(e => 
                        transaction(
                          pageRepo.updateState(page.id, Failed) *>
                            metaRepo.getOption(page.metaId).mapOption(modifiedMeta =>
                              metaRepo.update(modifiedMeta.copy(state = MangaMeta.State.Failed.code, cause = Some(e.getMessage())))
                            )
                        ) *> signal.set(false)
                      )
                      _ <- transaction(
                        pageRepo.delete(page.id) *> metaRepo.getOption(page.metaId).mapOption(m =>
                          metaRepo.increaseCompletedPages(m.id) *> ZIO.when(m.completedPages + 1 >= m.totalPages)(
                            (
                              if (config.dequeueIfCompleted) metaRepo.delete(m.id)
                              else metaRepo.updateState(m.id, MangaMeta.State.Completed)
                            ) *> 
                              ZIO.log(s"Completed: '${m.title}'") *> signal.set(false)
                          )
                        )
                      ) <* ZIO.sleep(2 seconds)
                    yield ()
                  ),
                onFalse = ZIO.unit
              )
            
            case _ => ZIO.unit
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
        state <- ZIO.service[ProgramState]
        quill <- ZIO.service[Quill.H2[SnakeCase]]
        metaRepo <- ZIO.service[MangaMetaRepo]
        pageRepo <- ZIO.service[MangaPageRepo]
        nhentai <- ZIO.service[NHentaiHandler]
        hentaiManga <- ZIO.service[HentaiMangaHandler]
      yield DownloadHub(config, state, metaRepo, pageRepo, quill, nhentai, hentaiManga)
    }