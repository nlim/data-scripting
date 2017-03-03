object Postgres {
  import doobie.imports._
  import doobie.contrib.hikari.hikaritransactor._
  import scalaz.concurrent.Task

  val driverClassName = "org.postgresql.Driver"

  case class PostgresConfig(
                          schema: String,
                          host: String,
                          user: String,
                          passwordOpt: Option[String],
                          ssl: Boolean
                        ) {
    def password        = passwordOpt.getOrElse("")

    def jdbcUrl         = {
      val root = s"jdbc:postgresql://${host}/${schema}?user=${user}&password=${password}"
      if (ssl) {
        root + s"&ssl=true"
      } else {
        root
      }
    }
  }

  def withTransactor[T](config: PostgresConfig)(p: HikariTransactor[Task] => Task[T]): Task[T] = for {
    xa <- HikariTransactor[Task](driverClassName, config.jdbcUrl, config.user, config.password)
    _  <- xa.configure(hx => Task.delay(()))
    r  <- p(xa) ensuring xa.shutdown
  } yield r
}