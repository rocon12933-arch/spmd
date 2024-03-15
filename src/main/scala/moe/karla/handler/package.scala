package moe.karla


import moe.karla.repo.*
import moe.karla.config.AppConfig

import zio.*


package object handler:
  
  trait Handler:

    def parseGallery(meta: MangaMeta): IO[HandlerError, MangaMeta]

    def download(page: MangaPage): IO[HandlerError, MangaPage]

  
  sealed trait HandlerError

  case class UnexpectedHandlerError(e: Throwable) extends Exception(e), HandlerError

  case class ParsingError(messages: String) extends Exception(messages), HandlerError

  case class NetworkError(e: Throwable) extends Exception(e), HandlerError

  case class FileSystemError(e: Throwable) extends Exception(e), HandlerError
