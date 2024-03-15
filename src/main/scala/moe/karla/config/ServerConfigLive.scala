package moe.karla.config


import zio.*
import zio.http.*
import zio.http.netty.NettyConfig
import zio.http.netty.NettyConfig.LeakDetectionLevel


object ServerConfigLive:
  
  val layer = 
    ZLayer {
      for 
        config <- ZIO.service[AppConfig]
      yield (Server.Config.default.binding(config.host, config.port))
    } ++
    ZLayer.succeed(
      NettyConfig.default
        .leakDetection(LeakDetectionLevel.PARANOID)
        .maxThreads(2)
    )
