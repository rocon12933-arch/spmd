package moe.karla.misc



import io.getquill.*
import io.getquill.jdbczio.Quill

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource



object Storage:
  
  
  def dataSource(poolSize: Int) = {
    val config = new HikariConfig()
    //config.setJdbcUrl("jdbc:sqlite:./data.db")
    //config.setDataSourceClassName("org.h2.jdbcx.JdbcDataSource")
    config.setJdbcUrl("jdbc:h2:./data")
    config.setUsername("sa")
    config.setTransactionIsolation("TRANSACTION_SERIALIZABLE")
    config.setMaximumPoolSize(poolSize)
    HikariDataSource(config)
  }
  

  def dataSourceLayer = Quill.DataSource.fromDataSource(dataSource(1))
  
  /*
  def dataSourceLayer = Quill.DataSource.fromPrefix("db")
  */