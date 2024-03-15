package moe.karla.config



import NHentaiConfig.*

import zio.*



final case class NHentaiConfig(

  patterns: List[String],

  cloudflare: CloudflareConfig,

  download: DownloadConfig
)

object NHentaiConfig:
  final case class CloudflareConfig(

    strategy: String,

    cookies: String
  )

  final case class DownloadConfig(
    implementation: String
  )

  val layer = ZLayer(ZIO.service[AppConfig].map(_.nhentai))