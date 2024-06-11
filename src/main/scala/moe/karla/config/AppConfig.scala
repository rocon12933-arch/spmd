package moe.karla.config

import moe.karla.config.*

import zio.*
import zio.config.*
import zio.config.typesafe.*
import zio.config.magnolia.*



final case class AppConfig(
  host: String,

  port: Int,

  agent: String,

  downPath: String,

  parallelPages: Int,

  parallelGalleries: Int,

  resetFailureOnBoot: Boolean,

  dequeueIfCompleted: Boolean,

  nhentai: NHentaiConfig,

  hentaiManga: HentaiMangaConfig
)


object AppConfig:

  private def load = 
    ConfigProvider.fromHoconFile(new java.io.File("application.conf"))
      .load(deriveConfig[AppConfig].mapKey(toKebabCase))
      .tapError(e => ZIO.logError(e.getMessage()))


  val layer = ZLayer.fromZIO(load)
