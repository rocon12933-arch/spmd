package moe.karla.misc


import moe.karla.misc.Storage.dataSourceLayer

import zio.* 

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.FlywayException
import org.flywaydb.core.api.output.MigrateResult

import java.io.IOException
import javax.sql.DataSource


object FlywayMigration:

  def runMigrate: ZIO[DataSource, IOException, MigrateResult] =
    ZIO.serviceWithZIO(ds => 
      ZIO.attemptBlockingIO(
        Flyway.configure().dataSource(ds).baselineOnMigrate(true).load().migrate()
      )
      .tapError(e => ZIO.logError(e.getMessage()))
    )
