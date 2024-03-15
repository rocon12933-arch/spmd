package moe.karla.misc


import zio.*

import com.microsoft.playwright.* 

import java.nio.file.Paths


object PlaywrightLayer:

  private def acquire: Task[Playwright] = 
    ZIO.attemptBlocking(Playwright.create())
    

  private def release(source: => Playwright): ZIO[Any, Nothing, Unit] =
    ZIO.succeedBlocking(source.close())


  val playwrightLayer: ZLayer[Any, Throwable, Playwright] =
    ZLayer.scoped(ZIO.acquireRelease(acquire)(release(_)))