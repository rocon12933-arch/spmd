package moe.karla.misc



import io.getquill.*
import io.getquill.jdbczio.Quill

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource



object Storage:
  
  
  def dataSource = {
    val config = new HikariConfig()
    //config.setJdbcUrl("jdbc:sqlite:./data.db")
    //config.setDataSourceClassName("org.h2.jdbcx.JdbcDataSource")
    config.setJdbcUrl("jdbc:h2:./data")
    config.setUsername("sa")
    HikariDataSource(config)
  }
  
  def dataSourceLayer = Quill.DataSource.fromDataSource(dataSource)
  
  /*
  def dataSourceLayer = Quill.DataSource.fromPrefix("db")
  */