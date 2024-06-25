package moe.karla.endpoint


import moe.karla.config.AppConfig
import moe.karla.repo.*

import zio.*
import zio.http.*
import zio.stream.ZStream
import zio.json.*

import io.getquill.SnakeCase
import io.getquill.jdbczio.Quill

import scala.io.Source



object TaskEndpoint:


  case class PrettyMangaMeta(
    id: Int,
    galleryUri: String,
    title: Option[String],
    progress: String,
  )

  given encoder: JsonEncoder[PrettyMangaMeta] = DeriveJsonEncoder.gen[PrettyMangaMeta]

  //given mangaMetaSchema: Schema[MangaMeta] = DeriveSchema.gen[MangaMeta]

  //given prettyMangaMetaSchema: Schema[PrettyMangaMeta] = DeriveSchema.gen[PrettyMangaMeta]


  extension (m: MangaMeta)
    def toPretty = {

      import MangaMeta.State.*

      PrettyMangaMeta(
        m.id, m.galleryUri, m.title, m.state match
          case Pending.code => "Pending"
          case Running.code => s"${m.completedPages} / ${m.totalPages}"
          case Completed.code => "Completed"
          case Failed.code => "Failed"

          case _ => throw IllegalArgumentException("Undefined code") 
      )
    }


  def routes = Routes(
    Method.GET / "tasks" -> Handler.fromFunctionZIO { (req: Request) =>
      for
        tasks <- MangaMetaRepo.all
      yield 
        if (req.queryParam("pretty").isDefined) Response.json(tasks.map(_.toPretty).toJsonPretty)
        else Response.json(tasks.toJsonPretty)
    },

    Method.POST / "tasks" -> Handler.fromFunctionZIO { (req: Request) =>
      for
        content <- req.body.asString
        lines <-
          ZIO.attempt(
            Source.fromString(content).getLines().map(_.strip()).filterNot(_ == "").filter(URL.decode(_).isRight).toList
          )
        _ <- 
          ZIO.when(lines.size > 0)(
            MangaMetaRepo.batchCreate(lines.map(MangaMeta(0, _, false, None, 0, 0, MangaMeta.State.Pending.code, None)))
          )

      yield if (lines.size > 0) Response.json(lines.toJsonPretty) else Response.badRequest
    },
    
    Method.DELETE / "task" / int("id") -> handler { (id: Int, _: Request) =>
      for
        opt <- MangaMetaRepo.getOption(id)
        resp <- opt match
          case None => ZIO.succeed(Response.notFound)
          case Some(meta) => ZIO.serviceWithZIO[Quill.H2[SnakeCase]](_.transaction(
            MangaMetaRepo.delete(meta.id) *> MangaPageRepo.deleteByMetaId(meta.id)
          )) *> ZIO.succeed(Response.ok)
      yield resp
    },

    Method.POST / "task" / int("id") / "reset" -> handler { (id: Int, _: Request) =>
      for
        opt <- MangaMetaRepo.getOption(id)
        resp <- opt match
          case None => ZIO.succeed(Response.notFound)
          case Some(meta) => ZIO.serviceWithZIO[Quill.H2[SnakeCase]](_.transaction(
            MangaMetaRepo.updateState(meta.id, MangaMeta.State.Pending) *>
              MangaPageRepo.updateStateIn(meta.id, MangaPage.State.Failed, MangaPage.State.Running)(MangaPage.State.Pending)
          )) *> ZIO.succeed(Response.ok)
      yield resp
    }
  )
  .handleError(e =>
    Response.fromThrowable(e)
  )
