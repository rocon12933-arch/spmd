package moe.karla.misc

import zio.ZLayer
import zio.Ref



class ProgramState(
  val downValve: Ref[Int]
)


object ProgramState:
  val live = ZLayer.fromZIO(Ref.make(0).map(ProgramState(_)))