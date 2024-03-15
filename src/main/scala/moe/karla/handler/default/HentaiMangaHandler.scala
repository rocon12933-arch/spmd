package moe.karla.handler.default


import moe.karla.config.*
import moe.karla.repo.*
import moe.karla.handler.*

import zio.*
import zio.http.*
import zio.stream.*
import zio.http.netty.ChannelFactories.Client

import io.getquill.SnakeCase
import io.getquill.jdbczio.Quill

import org.jsoup.Jsoup

import java.nio.file.*
import java.sql.SQLException




class HentaiMangaHandler(
  config: AppConfig
):


  private val retryPolicy = 
    Schedule.recurs(5) || 
    Schedule.spaced(1 second) || 
    Schedule.fibonacci(200 millis)



  def download(page: MangaPage): IO[HandlerError, Unit] = ???



object HentaiMangaHandlerLive:
  val layer = 
    ZLayer {
      for
        config <- ZIO.service[AppConfig]
      yield HentaiMangaHandler(config)
    }
