package moe.karla


import moe.karla.misc.FlywayMigration
import moe.karla.misc.Storage.dataSourceLayer
import moe.karla.misc.ProgramState
import moe.karla.config.{AppConfig, ServerConfigLive, ClientConfigLive}
import moe.karla.repo.MangaMetaRepoLive
import moe.karla.repo.MangaPageRepoLive
import moe.karla.handler.default.NHentaiHandlerLive
import moe.karla.handler.default.HentaiMangaHandlerLive
import moe.karla.service.*
import moe.karla.service.PrepareService
import moe.karla.endpoint.BasicEndpoint
import moe.karla.endpoint.TaskEndpoint


import zio.*
import zio.http.*
import zio.http.Header.AccessControlAllowOrigin
import zio.http.Middleware.CorsConfig
import zio.http.netty.NettyConfig
import zio.http.netty.NettyConfig.*

import zio.logging.consoleLogger
import zio.config.typesafe.TypesafeConfigProvider


import java.nio.file.Paths
import java.nio.file.Files



object AppMain extends ZIOAppDefault:

  override val bootstrap: ZLayer[Any, Config.Error, Unit] =
    Runtime.removeDefaultLoggers >>> 
    Runtime.setConfigProvider(TypesafeConfigProvider.fromHoconFilePath("logger.conf")) >>> 
    consoleLogger()

  def run =
    FlywayMigration.runMigrate *>
    ( 
      for
        config <- ZIO.service[AppConfig]
        _ <- ZIO.attemptBlockingIO(Files.createDirectories(Paths.get(config.downPath)))
        _ <- PrepareService.run
        _ <- DownloadHub.runDaemon
        port <- 
          Server.install(
            (TaskEndpoint.routes ++ BasicEndpoint.routes) @@ 
            Middleware.cors(
              CorsConfig(
                allowedOrigin = { _ => Some(AccessControlAllowOrigin.All) },
              )
            )// @@ Middleware.serveResources(Path.empty / "static")
          )
        _ <- ZIO.log(s"Server started @ ${config.host}:${port}")
        _ <- ZIO.never
      yield ExitCode.success
    )
    .provide(
      dataSourceLayer, AppConfig.layer,
      ProgramState.live,
      NHentaiHandlerLive.layer,
      HentaiMangaHandlerLive.layer,
      PrepareServiceLive.layer,
      DownloadHubLive.layer,
      moe.karla.repo.quillH2Layer,
      MangaMetaRepoLive.layer, 
      MangaPageRepoLive.layer,
      ServerConfigLive.layer, Server.customized,
      ClientConfigLive.layer, Client.customized, 
      DnsResolver.default,
      Scope.default
    )
    //.exitCode