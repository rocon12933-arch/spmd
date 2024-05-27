package moe.karla

import zio.*
import scala.annotation.targetName


package object service:


  type ImplicitUnit[A] = A | Unit

  extension [R, E, A, B] (v: => ZIO[R, E, Option[A]])

    def mapOption(f: A => ZIO[R, E, B])(implicit trace: Trace): ZIO[R, E, Option[B]] =
      ZIO.suspendSucceed:
        for 
          opt <- v
          b <- opt match
            case None => ZIO.none
            case Some(value) => f(value).asSome
        yield b


    def mapUnit(f: A => ZIO[R, E, B])(implicit trace: Trace): ZIO[R, E, ImplicitUnit[B]] =
      ZIO.suspendSucceed:
        for 
          opt <- v
          b <- opt match
            case None => ZIO.unit
            case Some(value) => f(value)
        yield b

  
  extension [R, E, A, B] (v: => ZIO[R, E, ImplicitUnit[A]])

    @targetName("implicitUnitMap")
    def mapUnit(f: A => ZIO[R, E, B])(implicit trace: Trace): ZIO[R, E, ImplicitUnit[B]] =
      ZIO.suspendSucceed:
        for 
          opt <- v
          b <- if (opt.isInstanceOf[Unit]) ZIO.unit
            else f(opt.asInstanceOf[A])
        yield b
  