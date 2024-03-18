package moe.karla.repo


import zio.*
import zio.json.*

import io.getquill.*
import io.getquill.jdbczio.Quill

import java.sql.SQLException



case class MangaMeta(
  id: Int,
  galleryUri: String,
  title: String,
  totalPages: Int,
  completedPages: Int,
  status: Short,
)

object MangaMeta:

  given encoder: JsonEncoder[MangaMeta] = DeriveJsonEncoder.gen[MangaMeta]

  enum Status(val code: Short):
    case Pending extends Status(0)
    case Parsing extends Status(1)
    case Parsed extends Status(2)
    case Running extends Status(3)
    case Completed extends Status(4)
    case Interrupted extends Status(-1)
    case Failed extends Status(-2)
      


/*
trait MangaMetaRepo:

  def all: IO[SQLException, List[MangaMeta]] 


  def updateStatus(id: Int, status: Short): IO[SQLException, Boolean]


  def create(galleryUri: String, title: String, totalPages: Int, status: Short): IO[SQLException, Int]


  def delete(id: Int): IO[SQLException, Boolean]


  def accquire(status: Short, transfrom: Short): IO[SQLException, Option[MangaMeta]]
*/


class MangaMetaRepo(quill: Quill.H2[SnakeCase]):

  import quill.*

  private inline def metaQuery = querySchema[MangaMeta]("manga_meta")


  private inline def queryGet(id: Int) = quote {
    metaQuery.filter(_.id == lift(id)).take(1)
  }


  private inline def queryGetFirstByStatus(status: MangaMeta.Status) = quote {
    metaQuery.filter(_.status == lift(status.code)).take(1)
  }


  private inline def queryUpdateStatus(id: Int, status: MangaMeta.Status) = quote {
    metaQuery.filter(_.id == lift(id)).update(_.status -> lift(status.code))
  }


  private inline def queryIncreaseCompletedPages(id: Int) = quote {
    metaQuery.filter(_.id == lift(id)).update(r => r.completedPages -> (r.completedPages + 1))
  }


  private inline def queryDelete(id: Int) = quote {
    metaQuery.filter(_.id == lift(id)).delete
  }


  private inline def queryInsert(galleryUri: String, title: String, totalPages: Int, status: Short) =
    quote { 
      metaQuery.insert(
        _.galleryUri -> lift(galleryUri), 
        _.title -> lift(title), 
        _.totalPages -> lift(totalPages), 
        _.status -> lift(status),
        _.completedPages -> lift(0)
      )
    }


  private inline def batchInsert(li: List[MangaMeta]) =
    quote { 
      liftQuery(li).foreach(p =>
        metaQuery.insert(
          _.galleryUri -> p.galleryUri, 
          _.totalPages -> p.totalPages,
          _.title -> p.title, 
          _.completedPages -> 0, 
          _.status -> p.status,
        )
      )
    }
  

  def getOption(id: Int) = run(quote { queryGet(id) }).map(_.headOption)


  def all = run(quote { metaQuery })

  
  def updateFastly(meta: MangaMeta) = 
    run(quote { metaQuery.filter(_.id == lift(meta.id)).updateValue(lift(meta)) }).map(_ > 0)

  
  def update(meta: MangaMeta) = 
    run(quote { metaQuery.filter(_.id == lift(meta.id)).update(
      _.galleryUri -> lift(meta.galleryUri), 
      _.totalPages -> lift(meta.totalPages),
      _.title -> lift(meta.title), 
      _.completedPages -> lift(meta.completedPages), 
      _.status -> lift(meta.status),
    ) }).map(_ > 0)


  def updateStatus(id: Int, status: MangaMeta.Status) = run(queryUpdateStatus(id, status)).map(_ > 0)


  def increaseCompletedPages(id: Int) = run(queryIncreaseCompletedPages(id)).map(_ > 0)


  def delete(id: Int) = run(queryDelete(id)).map(_ > 0)

  
  def create(galleryUri: String, title: String, totalPages: Int, status: MangaMeta.Status) = 
    transaction(
      run(queryInsert(galleryUri, title, totalPages, status.code)) *>
      run(metaQuery.filter(_.galleryUri == lift(galleryUri)).map(_.id).take(1))
    )
    //.mapError(SQLException(_))
    .map(_.head)


  def getFirstByStatusOption(status: MangaMeta.Status) = 
    run(queryGetFirstByStatus(status)).map(_.headOption)


  def batchCreate(li: List[MangaMeta]) = run(batchInsert(li))



object MangaMetaRepo:

  def all = ZIO.serviceWithZIO[MangaMetaRepo](_.all)


  def getOption(id: Int) = ZIO.serviceWithZIO[MangaMetaRepo](_.getOption(id))


  def updateFastly(meta: MangaMeta) = ZIO.serviceWithZIO[MangaMetaRepo](_.updateFastly(meta))


  def updateStatus(id: Int, status: MangaMeta.Status) = ZIO.serviceWithZIO[MangaMetaRepo](_.updateStatus(id, status))


  def create(galleryUri: String, title: String, totalPages: Int, status: MangaMeta.Status) =
    ZIO.serviceWithZIO[MangaMetaRepo](_.create(galleryUri, title, totalPages, status))


  def delete(id: Int) = ZIO.serviceWithZIO[MangaMetaRepo](_.delete(id))

  
  def getFirstByStatusOption(status: MangaMeta.Status) = 
    ZIO.serviceWithZIO[MangaMetaRepo](_.getFirstByStatusOption(status))


  def batchCreate(li: List[MangaMeta]) = ZIO.serviceWithZIO[MangaMetaRepo](_.batchCreate(li))


  
object MangaMetaRepoLive:
  
  val layer = ZLayer.fromFunction(new MangaMetaRepo(_))