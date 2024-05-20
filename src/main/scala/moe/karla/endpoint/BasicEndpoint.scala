package moe.karla.endpoint


import zio.*
import zio.http.*
import moe.karla.DownloadValve



object BasicEndpoint:
  
  def routes = Routes(
    Method.GET / "health" -> Handler.text("Running"),

    Method.POST / "valve" / "download" / "enable" -> handler {
      ZIO.setState(DownloadValve.Enabled) *> ZIO.succeed(Response.ok)
    },

    Method.POST / "valve" / "download" / "disable" -> handler {
      ZIO.setState(DownloadValve.Blocking) *> ZIO.succeed(Response.ok)
    },
  )