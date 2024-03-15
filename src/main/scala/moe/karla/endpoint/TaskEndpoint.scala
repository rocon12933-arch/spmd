package moe.karla.endpoint

import moe.karla.repo.MangaMeta
import moe.karla.repo.MangaMetaRepo

import zio.*
import zio.http.*
import zio.stream.ZStream
import zio.json.*

import scala.io.Source



object TaskEndpoint:
  def routes = Routes(
    Method.GET / "task" -> Handler.fromZIO(MangaMetaRepo.all.map(li => Response.json(li.toJson))),
    Method.POST / "task" -> Handler.fromFunctionZIO { (req: Request) =>
      for
        content <- req.body.asString
        //_ <- ZIO.log(s"content: $content")
        lines <- ZIO.attempt(Source.fromString(content).getLines().toList)
        _ <- MangaMetaRepo.batchCreate(lines.map(MangaMeta(0, _, "Pending...", 0, 0, 0)))
      yield Response.ok
    }
  )
  .handleError(e => 
    e.printStackTrace()
    Response.fromThrowable(e)
  )
