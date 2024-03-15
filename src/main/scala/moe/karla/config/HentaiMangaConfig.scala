package moe.karla.config


import HentaiMangaConfig.*

import zio.*


final case class HentaiMangaConfig(

  patterns: List[String],

  download: DownloadConfig
)


object HentaiMangaConfig:
  
  final case class DownloadConfig(

    nativeArchive: Boolean
  )

  val layer = ZLayer(ZIO.service[AppConfig].map(_.hentaiManga))