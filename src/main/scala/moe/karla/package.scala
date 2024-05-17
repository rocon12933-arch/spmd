package moe

package object karla:

  case class ProgramState(
    downValve: DownloadValve
  )

  enum DownloadValve(val code: Short):

    case Enabled extends DownloadValve(0)

    case Blocking extends DownloadValve(1)
