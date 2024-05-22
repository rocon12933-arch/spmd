package moe.karla


import io.getquill.SnakeCase
import io.getquill.jdbczio.Quill


package object repo:
  
  //val quillSqliteLayer = Quill.Sqlite.fromNamingStrategy(SnakeCase)

  val quillH2Layer = Quill.H2.fromNamingStrategy(SnakeCase)

    
