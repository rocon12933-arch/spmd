package moe.karla


import moe.karla.repo.*
import moe.karla.config.AppConfig

import zio.*


package object handler:
  
  trait Handler:

    def parseAndRetrivePages(meta: MangaMeta): ZIO[zio.http.Client & Scope, Exception, (MangaMeta, List[MangaPage])]


    def download(page: MangaPage): ZIO[zio.http.Client & Scope, Exception, MangaPage]
    
  
  sealed trait HandlerError
  

  case class UnexpectedHandlerError(e: Throwable) extends Exception(e), HandlerError


  case class BypassError(messages: String) extends Exception(messages), HandlerError


  case class ParsingError(messages: String) extends Exception(messages), HandlerError


  case class NetworkError(e: Throwable) extends Exception(e), HandlerError


  case class FileSystemError(e: Throwable) extends Exception(e), HandlerError
