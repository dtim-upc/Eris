
// Loads a table and translates it to symbolic form

object Loader {

  def load(connector: Connector, tablename: String, encode: Encoding, cleanup: Boolean) = {
    val conn = connector.getConnection()
    val ctx = Database.loadSchema(conn)

    val q = Absyn.Relation(tablename)
    val schema = Absyn.Query.tc(ctx,q)

    val conn2 = connector.getConnection()
    conn2.setAutoCommit(false)
    val st = conn2.createStatement()

    // Create table commands to create/refresh encoded schema
    val encodedSchema = encode.schemaEncodingWithSourceField(tablename,q.schema)

    // Drop all tables with given names
    encodedSchema.foreach{case (r,_) =>
      val cmd = Database.dropViewCommand(r)
      //println(cmd)
      st.executeUpdate(cmd)
      conn2.commit()
    }

    if(!cleanup) {
      Debug.println(1,ctx.toString)
      // Create the views
      encodedSchema.foreach{case (r,(f,sch)) =>
        Debug.println(1,tablename)
        Debug.println(1,schema.toString)
        val cmd =  Database.createViewCommand(tablename,r,encode.schemaToViewDef(tablename,r,f,sch), q.schema.varfreeFields.contains(f))
        Debug.println(1,cmd)
        st.executeUpdate(cmd)
        conn2.commit()
      }
    }

  }

  def main(args:Array[String]) : Unit = {
    val hostname = args(0)
    val dbname = args(1)
    val username = args(2)
    val password = args(3)

    val connector = Connector(hostname,dbname,username,password)

    val tablename = args(4)

    val cleanup = args.applyOrElse(6,{_:Int => ""}) == "cleanup"


    load(connector,tablename, Encoding.encoder_to_use(args.applyOrElse(5,{_:Int =>"partitioning"})), cleanup)

  }

}
