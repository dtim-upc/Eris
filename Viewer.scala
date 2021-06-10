
// Evaluates a view query and loads it into the database as a raw table.


object Viewer {


  def handleViewStep(connector: Connector, ctx: Database.InstanceSchema, vtable: String, vquery: Absyn.Query) = {
    val conn = connector.getConnection()
    val schema = Absyn.Query.tc(ctx,vquery)

    conn.setAutoCommit(false)

    val st = conn.createStatement()
    st.executeUpdate(  Database.dropTableCommand(vtable) )
    conn.commit()

    st.executeUpdate( Database.createTableCommand(vtable,Database.schemaToTableDef(schema,"double precision")) )
    conn.commit()

    val cmd = Database.insertQueryCommand(vtable, vquery)
    println(cmd)
    st.executeUpdate(cmd)
    conn.commit()

    st.executeUpdate(Database.alterTableCommand(vtable,schema))
    conn.commit()

    st.executeUpdate("DELETE FROM schema WHERE tablename = '"+vtable+"';")

    schema.keyFields.foreach{k =>
      st.executeUpdate("INSERT INTO schema (tablename,fieldname,key,varfree) VALUES ('"+vtable+"','"+k+"',TRUE,FALSE)")
    }
    schema.valFields.foreach{v =>
      st.executeUpdate("INSERT INTO schema (tablename,fieldname,key,varfree) VALUES ('"+vtable+"','"+v+"',FALSE," +
        (if (schema.varfreeFields.contains(v)) {"TRUE"} else {"FALSE"}) + ")")
    }
    conn.commit()

    ctx + (vtable -> schema)
  }

  def handleSpecification(connector: Connector, ctx: Database.InstanceSchema, spec: List[(String,Absyn.Query)]) = {
    spec.foldLeft(ctx){case (ctx,(vtable,vquery)) =>
      handleViewStep(connector,ctx,vtable,vquery)
    }
  }

  def view(connector: Connector, specfile: String) = {

    val p = new RAParser()
    val spec = p.parse(p.specification,specfile)

    val conn = connector.getConnection()
    val ctx = Database.loadSchema(conn)


    try {
      handleSpecification(connector,ctx, spec)
    } catch {
      case Absyn.TypeError(msg) => println("Type error: " + msg)
    }
  }


  def main(args:Array[String]) : Unit = {
    Class.forName("org.postgresql.Driver")

    val hostname = args(0)
    val dbname = args(1)
    val username = args(2)
    val password = args(3)
    val interactive = args.length < 5

    val connector = Connector(hostname, dbname, username, password)


    if (interactive) {
      // REPL loop
      val conn = connector.getConnection()
      val p = new RAParser()

      while (true) {
        val ctx = Database.loadSchema(conn)

        print("view> ")
        val str = scala.io.StdIn.readLine()
        val (vtable,vquery) = p.parseStr(p.viewDef,str)

        // check whether table is alread in schema, if so ask
        if (ctx.contains(vtable)) {
          println("Warning: table " + vtable + " is already present in schema, delete it?")
          val ans = scala.io.StdIn.readLine()
          if(ans != "y") {
            System.exit(0)
          }
        }

        try {
          handleViewStep(connector,ctx,vtable,vquery)
        } catch {
          case Absyn.TypeError(msg) => println("Type error: " + msg)
        }
      }
    } else { // Read from spec file
      view(connector, args(4))
    }

  }

}

