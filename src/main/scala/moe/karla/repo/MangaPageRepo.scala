package moe.karla.repo


import zio.*

import io.getquill.*
import io.getquill.jdbczio.Quill


case class MangaPage(
  id: Int,
  metaId: Int,
  pageUri: String,
  pageNumber: Int,
  path: String,
  status: Short,
)

object MangaPage:

  enum Status(val code: Short):
    case Pending extends Status(0)
    case Running extends Status(1)
    case Completed extends Status(2)
    case Failed extends Status(-1)
      


class MangaPageRepo(quill: Quill.H2[SnakeCase]):

  import quill.*
  
  private inline def pageQuery = querySchema[MangaPage]("manga_page")
  

  private inline def queryGet(id: Int) = quote {
    pageQuery.filter(_.id == lift(id)).take(1)
  }


  private inline def queryUpdateAllStatus(status: MangaPage.Status) = quote {
    pageQuery.update(_.status -> lift(status.code))
  } 


  private inline def queryUpdateStatus(id: Int, status: MangaPage.Status) = quote {
    pageQuery.filter(_.id == lift(id)).update(_.status -> lift(status.code))
  } 


  private inline def queryGetFirstByStatus(status: MangaPage.Status) = quote {
    pageQuery.filter(_.status == lift(status.code)).take(1)
  }


  private inline def queryDelete(id: Int) = quote {
    pageQuery.filter(_.id == lift(id)).delete
  }


  private inline def queryInsert(metaId: Int, pageUri: String, pageNumber: Int, path: String, status: MangaPage.Status) =
    quote { 
      pageQuery.insert(
        _.metaId -> lift(metaId), 
        _.pageNumber -> lift(pageNumber), 
        _.pageUri -> lift(pageUri), 
        _.path -> lift(path), 
        _.status -> lift(status.code)
      ).onConflictIgnore
    }
    

  private inline def batchInsert(li: List[MangaPage]) = 
    quote { 
      liftQuery(li).foreach(p =>
        pageQuery.insert(
          _.metaId -> p.metaId, 
          _.pageNumber -> p.pageNumber,
          _.pageUri -> p.pageUri, 
          _.path -> p.path, 
          _.status -> p.status,
        )
        .onConflictIgnore
      )
    }
  

  def getOption(id: Int) = run(queryGet(id)).map(_.headOption)

  
  def create(metaId: Int, pageUri: String, pageNumber: Int, path: String, status: MangaPage.Status) = 
    run(queryInsert(metaId, pageUri, pageNumber, path, status))
  
  
  def updateAllStatus(status: MangaPage.Status) = run(queryUpdateAllStatus(status))


  def updateStatus(id: Int, status: MangaPage.Status) = run(queryUpdateStatus(id, status)).map(_ > 0)


  def batchCreate(li: List[MangaPage]) = 
    run(batchInsert(li))

  
  def getFirstByStatusOption(status: MangaPage.Status) = 
    run(queryGetFirstByStatus(status)).map(_.headOption)


  def delete(id: Int) = run(queryDelete(id)).map(_ > 0)

    
object MangaPageRepo:

  def getOption(id: Int) = ZIO.serviceWithZIO[MangaPageRepo](_.getOption(id))

   
  def create(metaId: Int, pageUri: String, pageNumber: Int, path: String, status: MangaPage.Status) = 
    ZIO.serviceWithZIO[MangaPageRepo](_.create(metaId, pageUri, pageNumber, path, status))
   

  def batchCreate(li: List[MangaPage]) = 
    ZIO.serviceWithZIO[MangaPageRepo](_.batchCreate(li))


  def updateStatus(id: Int, status: MangaPage.Status) = ZIO.serviceWithZIO[MangaPageRepo](_.updateStatus(id, status))

  
  def delete(id: Int) = ZIO.serviceWithZIO[MangaPageRepo](_.delete(id))


  
object MangaPageRepoLive:
  val layer = ZLayer.fromFunction(MangaPageRepo(_))
  
  

