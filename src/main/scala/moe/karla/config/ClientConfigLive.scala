package moe.karla.config


import zio.*
import zio.http.*
import zio.http.netty.client.NettyClientDriver


object ClientConfigLive:

  val layer = 
    ZLayer {
      for 
        config <- ZIO.service[AppConfig]
      yield ZClient.Config.default.ssl(ClientSSLConfig.Default).addUserAgentHeader(true).disabledConnectionPool
    } 
    ++ NettyClientDriver.live
