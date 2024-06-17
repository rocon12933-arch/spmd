package moe.karla.config



import NHentaiConfig.*

import zio.*



final case class NHentaiConfig(

  patterns: List[String],

  cloudflare: CloudflareConfig,

  parsing: ParsingConfig,

  download: DownloadConfig
)

object NHentaiConfig:
  
  final case class CloudflareConfig(

    strategy: String,

    providedCookies: String
  )

  final case class ParsingConfig(

    implementation: String
  )


  final case class DownloadConfig(

    fileSignatureCheck: FileSignatureCheckConfig
  )


  final case class FileSignatureCheckConfig(

    enabled: Boolean,

    fallbackExtensionName: String
  )

  val layer = ZLayer(ZIO.service[AppConfig].map(_.nhentai))