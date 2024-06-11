package moe.karla.endpoint


import zio.*
import zio.http.*
import moe.karla.misc.ProgramState



object BasicEndpoint:
  
  def routes = Routes(
    Method.GET / "health-check" -> Handler.text("health"),

    Method.GET / "valve" / "download" -> handler {
      ZIO.ifZIO(ZIO.serviceWithZIO[ProgramState](_.downValve.get.map(_ == 0)))(
        onTrue = ZIO.succeed(Response.text("Not blocking")),
        onFalse = ZIO.succeed(Response.text("Blocking"))
      )
    },

    Method.POST / "valve" / "download" / "enable" -> handler {
      ZIO.serviceWithZIO[ProgramState](_.downValve.set(0)) *> ZIO.succeed(Response.ok)
    },

    Method.POST / "valve" / "download" / "disable" -> handler {
      ZIO.serviceWithZIO[ProgramState](_.downValve.set(1)) *> ZIO.succeed(Response.ok)
    },
  )