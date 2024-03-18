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



class PrepareService(
  config: AppConfig,
  metaRepo: MangaMetaRepo,
  pageRepo: MangaPageRepo,
  quill: Quill.H2[SnakeCase],
):

  def execute = pageRepo.updateAllStatus(MangaPage.Status.Pending)


object PrepareService:
  def run = ZIO.serviceWithZIO[PrepareService](_.execute)


object PrepareServiceLive:

  val layer = 
    ZLayer {
      for
        config <- ZIO.service[AppConfig]
        quill <- ZIO.service[Quill.H2[SnakeCase]]
        metaRepo <- ZIO.service[MangaMetaRepo]
        pageRepo <- ZIO.service[MangaPageRepo]
      yield PrepareService(config, metaRepo, pageRepo, quill)
    }