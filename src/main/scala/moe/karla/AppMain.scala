package moe.karla


import moe.karla.misc.FlywayMigration
import moe.karla.misc.Storage.dataSourceLayer
import moe.karla.config.{AppConfig, ServerConfigLive, ClientConfigLive}
import moe.karla.repo.MangaMetaRepoLive
import moe.karla.repo.MangaPageRepoLive
import moe.karla.handler.default.NHentaiHandlerLive
import moe.karla.handler.default.HentaiMangaHandlerLive
import moe.karla.service.ParserService
import moe.karla.service.ParserServiceLive
import moe.karla.service.DownloaderService
import moe.karla.service.DownloaderServiceLive
import moe.karla.endpoint.TaskEndpoint


import zio.*
import zio.http.*
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
    (
      for
        config <- ZIO.service[AppConfig]
        _ <- FlywayMigration.runMigrate
        _ <- ZIO.attempt(Files.createDirectories(Paths.get(config.downPath)))
        _ <- ParserService.runDaemon
        _ <- DownloaderService.runDaemon
        port <- Server.install(TaskEndpoint.routes.toHttpApp)
        _ <- ZIO.log(s"Server started @ ${config.host}:${port}")
        _ <- ZIO.never
      yield ExitCode.success
    )
    .provide(
      dataSourceLayer, AppConfig.layer, ParserServiceLive.layer, DownloaderServiceLive.layer,
      NHentaiHandlerLive.layer,
      //HentaiMangaHandlerLive.layer,
      moe.karla.repo.quillLayer,
      MangaMetaRepoLive.layer, 
      MangaPageRepoLive.layer,
      ServerConfigLive.layer, Server.customized,
      ClientConfigLive.layer, Client.customized, 
      DnsResolver.default,
      Scope.default
    )
    //.exitCode
