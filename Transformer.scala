

// Evaluates a view query and loads it into the database as a raw table.


object Transformer {

  // TODO: really this could be generalized to take any partial function on rows and apply it to a table
  // maybe this more general function could be provided by Database
  def transform(connector: Connector, tbl: String, sigma: Double, p_null: Double, p_row_null: Double): Unit = {

    val conn = connector.getConnection()
    val ctx = Database.loadSchema(conn)

    if (!ctx.contains(tbl)) {
      println("Warning: table " + tbl + " is not present in schema")
      System.exit(0)
    }
    val schema = ctx(tbl)

    val conn2 = connector.getConnection()
    conn2.setAutoCommit(false)
    val st = conn2.createStatement()

    val blocksize = 4096 // maximum number of inserts to do at once
                         // Insert commands to load encoded data
    val preparedStatement2 = conn.prepareStatement("SELECT * FROM "+ tbl)
    val stream = Database.iterateQuery(preparedStatement2,blocksize)

    Database.iterateRelation(stream, schema)
      .map(Database.Row.distort(schema,_,sigma))
      .map(Database.Row.obscure(schema,_,p_null))
      .map(row =>
      if (Database.Row.redact(row,p_row_null)) {
        Database.deleteRowCommand(tbl,row)
      } else {
        Database.updateRowCommand(tbl,row)
      }
    ).grouped(blocksize)
      .map(_.mkString("\n"))
      .foreach{cmd =>
      Debug.println(1,cmd)
      st.executeUpdate(cmd)
      conn2.commit()
    }
  }


  def main(args:Array[String]) : Unit = {
    val connector = Connector(args(0),args(1),args(2),args(3))

    val tbl = args(4)
    val sigma = args(5).toDouble
    val p_null = args(6).toDouble
    val p_row_null = args(7).toDouble


    transform(connector,tbl,sigma,p_null,p_row_null)

  }
}
