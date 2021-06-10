import java.sql.DriverManager

// wrapper for database connections

case class Connector(hostname: String, dbname: String, username: String, password: String) {
  locally {
    Class.forName("org.postgresql.Driver")
  }

  def getConnection() = 
    DriverManager.getConnection("jdbc:postgresql://" + hostname + ":5432/" + dbname, username, password)

}
