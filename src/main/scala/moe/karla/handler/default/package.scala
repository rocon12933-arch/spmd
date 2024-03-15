package moe.karla.handler



import org.jsoup.nodes.Element


package object default:

  extension[A <: Element] (element: A)
    def map[B](f: A => B): B = f(element)