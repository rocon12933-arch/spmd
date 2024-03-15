package moe.karla


import io.getquill.SnakeCase
import io.getquill.jdbczio.Quill


package object repo:
  
  val quillLayer = Quill.H2.fromNamingStrategy(SnakeCase)

    
