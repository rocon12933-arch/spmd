package moe.karla.repo


import zio.*

import io.getquill.*
import io.getquill.jdbczio.Quill


case class MangaPage(
  id: Int,
  metaId: Int,
  pageUri: String,
  pageNumber: Int,
  title: String,
  state: Short,
)

object MangaPage:

  enum State(val code: Short):
    case Pending extends State(0)
    case Running extends State(1)
    case Completed extends State(2)
    case Failed extends State(-1)
    //case Interrupted extends State(-2)
      


class MangaPageRepo(quill: Quill.H2[SnakeCase]):

  import quill.*
  import MangaPage.State
  import MangaPage.State.*


  private inline def pageQuery = querySchema[MangaPage]("manga_page")
  

  private inline def queryGet(id: Int) = quote {
    pageQuery.filter(_.id == lift(id)).take(1)
  }


  private inline def queryUpdateAllState(state: State) = quote {
    pageQuery.update(_.state -> lift(state.code))
  } 


  private inline def queryUpdateState(id: Int, state: State) = quote {
    pageQuery.filter(_.id == lift(id)).update(_.state -> lift(state.code))
  }


  private inline def queryUpdateStateIn(state: Seq[State])(newState: State) =
    quote {
      pageQuery
        .filter(p => liftQuery(state.map(s => s.code)).contains(p.state))
        .update(_.state -> lift(newState.code))
    }


  private inline def queryUpdateStateIn(metaId: Int, newState: State, in: Seq[State]) = 
    quote {
      pageQuery
        .filter(_.metaId == lift(metaId))
        .filter(p => liftQuery(in.map(s => s.code)).contains(p.state))
        .update(_.state -> lift(newState.code))
    }


  private inline def queryUpdateStateExcept(metaId: Int, newState: State, excepts: Seq[State]) = 
    quote {
      pageQuery
        .filter(_.metaId == lift(metaId))
        .filter(p => !liftQuery(excepts.map(s => s.code)).contains(p.state))
        .update(_.state -> lift(newState.code))
    }


  private inline def queryGetFirstByState(state: State) = quote {
    pageQuery.filter(_.state == lift(state.code)).take(1)
  }

  private inline def queryGetFirstByMetaIdAndState(metaId: Int, state: State) = quote {
    pageQuery.filter(_.metaId == lift(metaId)).filter(_.state == lift(state.code)).take(1)
  }


  private inline def queryGetFirstByMetaIdAndStateIn(metaId: Int, states: State*) = quote {
    pageQuery
      .filter(_.metaId == lift(metaId))
      .filter(p => liftQuery(states.map(s => s.code)).contains(p.state))
      .take(1)
  }


  private inline def queryDelete(id: Int) = quote {
    pageQuery.filter(_.id == lift(id)).delete
  }


  private inline def queryDeleteByMetaId(metaId: Int) = quote {
    pageQuery.filter(_.metaId == lift(metaId)).delete
  }


  private inline def queryInsert(metaId: Int, pageUri: String, pageNumber: Int, title: String, state: State) =
    quote { 
      pageQuery.insert(
        _.metaId -> lift(metaId), 
        _.pageNumber -> lift(pageNumber), 
        _.pageUri -> lift(pageUri), 
        _.title -> lift(title), 
        _.state -> lift(state.code)
      )
    }
    

  private inline def batchInsert(li: List[MangaPage]) = 
    quote { 
      liftQuery(li).foreach(p =>
        pageQuery.insert(
          _.metaId -> p.metaId, 
          _.pageNumber -> p.pageNumber,
          _.pageUri -> p.pageUri, 
          _.title -> p.title, 
          _.state -> p.state,
        )
      )
    }
  

  def underlying = quill
  

  def getOption(id: Int) = run(queryGet(id)).map(_.headOption)

  
  def create(metaId: Int, pageUri: String, pageNumber: Int, path: String, state: State) = 
    run(queryInsert(metaId, pageUri, pageNumber, path, state))
  
  
  def updateAllState(state: State) = run(queryUpdateAllState(state))


  def updateStateIn(state: State*)(newState: State) = run(queryUpdateStateIn(state)(newState))


  def updateState(id: Int, state: State) = run(queryUpdateState(id, state)).map(_ > 0)


  def updateStateExcept(metaId: Int, excepts: State*)(newState: State) = 
    run(queryUpdateStateExcept(metaId, newState, excepts))


  def updateStateIn(metaId: Int, in: State*)(newState: State) = 
    run(queryUpdateStateIn(metaId, newState, in))
  

  def batchCreate(li: List[MangaPage]) = 
    run(batchInsert(li))

  
  def getFirstByStateOption(state: State) =
    run(queryGetFirstByState(state)).map(_.headOption)

  
  def getFirstByMetaIdAndStateOption(metaId: Int, state: State) =
    run(queryGetFirstByMetaIdAndState(metaId, state)).map(_.headOption)


  def delete(id: Int) = run(queryDelete(id)).map(_ > 0)


  def deleteByMetaId(metaId: Int) = run(queryDeleteByMetaId(metaId))

  
    
object MangaPageRepo:

  def getOption(id: Int) = ZIO.serviceWithZIO[MangaPageRepo](_.getOption(id))

   
  def create(metaId: Int, pageUri: String, pageNumber: Int, path: String, State: MangaPage.State) = 
    ZIO.serviceWithZIO[MangaPageRepo](_.create(metaId, pageUri, pageNumber, path, State))
   

  def batchCreate(li: List[MangaPage]) = 
    ZIO.serviceWithZIO[MangaPageRepo](_.batchCreate(li))


  def updateState(id: Int, State: MangaPage.State) = ZIO.serviceWithZIO[MangaPageRepo](_.updateState(id, State))

  
  def delete(id: Int) = ZIO.serviceWithZIO[MangaPageRepo](_.delete(id))


  def deleteByMetaId(metaId: Int) = ZIO.serviceWithZIO[MangaPageRepo](_.deleteByMetaId(metaId))

  
object MangaPageRepoLive:
  val layer = ZLayer.fromFunction(MangaPageRepo(_))
  
  

