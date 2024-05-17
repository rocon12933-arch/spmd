package moe.karla.handler.default


import moe.karla.config.*
import moe.karla.repo.*
import moe.karla.handler.*

import zio.*
import zio.stream.*
import zio.http.*
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


  private def configureRequest(uri: String) = 
    Request.get(uri).addHeader("User-Agent", config.agent)


  def parseAndRetrivePagesNew(meta: MangaMeta) =
    for 
      client <- ZIO.service[Client]

      request = configureRequest(meta.galleryUri)

      _ <- ZIO.log(s"Parsing: ${meta.galleryUri}")
      
    yield ()


  def parseAndRetrivePages(meta: MangaMeta): ZIO[zio.http.Client & Scope, Exception, (MangaMeta, List[MangaPage])] = ???


  def download(page: MangaPage): ZIO[zio.http.Client & Scope, Exception, MangaPage] = ???


  def download = ???



object HentaiMangaHandlerLive:
  val layer = 
    ZLayer {
      for
        config <- ZIO.service[AppConfig]
      yield HentaiMangaHandler(config)
    }
