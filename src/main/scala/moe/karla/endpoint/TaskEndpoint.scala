package moe.karla.endpoint

import moe.karla.repo.MangaMeta
import moe.karla.repo.MangaMetaRepo

import zio.*
import zio.http.*
import zio.stream.ZStream
import zio.json.*

import scala.io.Source
import moe.karla.config.AppConfig



object TaskEndpoint:
  def routes = Routes(
    Method.GET / "task" -> Handler.fromZIO(MangaMetaRepo.all.map(li => Response.json(li.toJson))),
    Method.POST / "task" -> Handler.fromFunctionZIO { (req: Request) =>
      for
        content <- req.body.asString
        config <- ZIO.service[AppConfig]
        lines <- ZIO.attempt(Source.fromString(content).getLines().map(_.strip()).filterNot(_ == "").toList)
        _ <- 
          if (lines.size > 0)
            MangaMetaRepo.batchCreate(lines.map(MangaMeta(0, _, "Pending...", 0, 0, MangaMeta.Status.Pending.code)))
          else ZIO.unit

      yield Response.json(lines.toJsonPretty)
    }
  )
  .handleError(e =>
    Response.fromThrowable(e)
  )
