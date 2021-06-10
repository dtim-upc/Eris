import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.DriverManager

object Test {

  def main(args:Array[String]) : Unit = {
    Class.forName("org.postgresql.Driver")
    
    val conn = DriverManager.getConnection("jdbc:postgresql://127.0.0.1:5432/" + args(0), args(1), args(2))

    val preparedStatement = conn.prepareStatement("select table_name, column_name from information_schema.columns order by table_name, column_name;")

    val resultSet = preparedStatement.executeQuery()

    while (resultSet.next()) {
      val tablename = resultSet.getString("table_name")
      val columnname = resultSet.getString("column_name")
      println(tablename + "," + columnname)
    }

  }
}
