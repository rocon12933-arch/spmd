package moe.karla.repo


import zio.*
import zio.json.*

import io.getquill.*
import io.getquill.jdbczio.Quill

import java.sql.SQLException
import java.sql.Timestamp



case class MangaMeta(
  id: Int,
  galleryUri: String,
  isParsed: Boolean,
  title: String,
  totalPages: Int,
  completedPages: Int,
  state: Short,
  cause: Option[String],
)

object MangaMeta:


  given encoder: JsonEncoder[MangaMeta] = DeriveJsonEncoder.gen[MangaMeta]

  enum State(val code: Short):
    case Pending extends State(0)
    case Running extends State(1)
    case Completed extends State(2)
    case Failed extends State(-1)
    case Interrupted extends State(-2)

    def fromCode(code: Short) = 
      code match 
        case Pending.code => Pending
        case Running.code => Running
        case Completed.code => Completed
        case Failed.code => Failed
        case Interrupted.code => Interrupted
        case _ => throw IllegalArgumentException("No such code in defined states") 


/*
trait MangaMetaRepo:

  def all: IO[SQLException, List[MangaMeta]] 


  def updateState(id: Int, State: Short): IO[SQLException, Boolean]


  def create(galleryUri: String, title: String, totalPages: Int, State: Short): IO[SQLException, Int]


  def delete(id: Int): IO[SQLException, Boolean]


  def accquire(State: Short, transfrom: Short): IO[SQLException, Option[MangaMeta]]
*/


class MangaMetaRepo(quill: Quill.H2[SnakeCase]):

  import quill.*
  import MangaMeta.State

  private inline def metaQuery = querySchema[MangaMeta]("manga_meta")


  private inline def queryGet(id: Int) = quote {
    metaQuery.filter(_.id == lift(id)).take(1)
  }


  private inline def queryGetFirstByState(state: State) = quote {
    metaQuery.filter(_.state == lift(state.code)).take(1)
  }

  
  private inline def queryGetFirstByStateIn(states: Seq[State]) = quote {
    metaQuery.filter(m => liftQuery(states.map(_.code)).contains(m.state)).take(1)
  }


  private inline def queryUpdateState(id: Int, state: State) = quote {
    metaQuery.filter(_.id == lift(id)).update(_.state -> lift(state.code))
  }


  private inline def queryUpdateStateIn(states: Seq[State])(newState: State) = quote {
    metaQuery.filter(m => liftQuery(states.map(_.code)).contains(m.state)).update(_.state -> lift(newState.code))
  }


  private inline def queryIncreaseCompletedPages(id: Int) = quote {
    metaQuery.filter(_.id == lift(id)).update(r => r.completedPages -> (r.completedPages + 1))
  }


  private inline def queryDelete(id: Int) = quote {
    metaQuery.filter(_.id == lift(id)).delete
  }


  private inline def queryInsert(
    galleryUri: String, 
    isParsed: Boolean, 
    title: String, 
    totalPages: Int, 
    state: Short
  ) =
    quote { 
      metaQuery.insert(
        _.galleryUri -> lift(galleryUri),
        _.isParsed -> lift(isParsed),
        _.title -> lift(title),
        _.totalPages -> lift(totalPages),
        _.state -> lift(state),
        _.completedPages -> lift(0),
      )
    }


  private inline def batchInsert(li: List[MangaMeta]) =
    quote { 
      liftQuery(li).foreach(p =>
        metaQuery.insert(
          _.galleryUri -> p.galleryUri, 
          _.isParsed -> p.isParsed,
          _.totalPages -> p.totalPages,
          _.title -> p.title, 
          _.completedPages -> 0, 
          _.state -> p.state,
        )
      )
    }
  

  def underlying = quill


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
      _.state -> lift(meta.state),
    ) }).map(_ > 0)


  def updateState(id: Int, state: State) = run(queryUpdateState(id, state)).map(_ > 0)


  def increaseCompletedPages(id: Int) = run(queryIncreaseCompletedPages(id)).map(_ > 0)


  def delete(id: Int) = run(queryDelete(id)).map(_ > 0)

  
  def create(galleryUri: String, isParsed: Boolean, title: String, totalPages: Int, state: State) = 
    transaction(
      run(queryInsert(galleryUri, isParsed, title, totalPages, state.code)) *>
      run(metaQuery.filter(_.galleryUri == lift(galleryUri)).map(_.id).take(1))
    )
    //.mapError(SQLException(_))
    .map(_.head)


  def getFirstByStateOption(state: State) = 
    run(queryGetFirstByState(state)).map(_.headOption)


  def getFirstByStateInOption(state: State*) = 
    run(queryGetFirstByStateIn(state)).map(_.headOption)

  
  def updateStateIn(states: State*)(newState: State) = run(queryUpdateStateIn(states)(newState))


  def batchCreate(li: List[MangaMeta]) = run(batchInsert(li))


object MangaMetaRepo:

  def all = ZIO.serviceWithZIO[MangaMetaRepo](_.all)


  def getOption(id: Int) = ZIO.serviceWithZIO[MangaMetaRepo](_.getOption(id))


  def updateFastly(meta: MangaMeta) = ZIO.serviceWithZIO[MangaMetaRepo](_.updateFastly(meta))


  def updateState(id: Int, state: MangaMeta.State) = ZIO.serviceWithZIO[MangaMetaRepo](_.updateState(id, state))


  def create(
    galleryUri: String, 
    isParsed: Boolean, 
    title: String, 
    totalPages: Int, 
    state: MangaMeta.State
  ) =
    ZIO.serviceWithZIO[MangaMetaRepo](_.create(galleryUri, isParsed, title, totalPages, state))


  def delete(id: Int) = ZIO.serviceWithZIO[MangaMetaRepo](_.delete(id))

  
  def getFirstByStateOption(state: MangaMeta.State) = 
    ZIO.serviceWithZIO[MangaMetaRepo](_.getFirstByStateOption(state))


  def batchCreate(li: List[MangaMeta]) = ZIO.serviceWithZIO[MangaMetaRepo](_.batchCreate(li))


  
object MangaMetaRepoLive:
  
  val layer = ZLayer.fromFunction(new MangaMetaRepo(_))