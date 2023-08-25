import scala.util.Random

// Creates random data given a scaling factor

object GenerateData {

  


  def main(args:Array[String]) : Unit = {
    val rand = new Random()

    Class.forName("org.postgresql.Driver")

    val hostname = args(0)
    val dbname = args(1)
    val username = args(2)
    val password = args(3)
    val n = args(4).toInt

    def random(n: Int): Double = {
      rand.nextDouble()*n
    }
    val connector = Connector(hostname, dbname, username, password)


    val conn = connector.getConnection()

    val table_r = "r"
    val r_schema = Database.Schema(Set("a","b"), Set("c","d"), Set())
    val table_s = "s"
    val s_schema = Database.Schema(Set("b"), Set("e","f"), Set())
     
   
    // remove/recreate r,s if present
    

    conn.setAutoCommit(false)
    
    val st = conn.createStatement()
    st.executeUpdate(Database.dropTableCommand(table_r))
    st.executeUpdate(Database.dropTableCommand(table_s))

    st.executeUpdate( Database.createTableCommand(table_r,Database.schemaToTableDef(r_schema,"double precision")) )
    st.executeUpdate( Database.createTableCommand(table_s,Database.schemaToTableDef(s_schema,"double precision")) )
    
    val updQ = Database.UpdQueue(conn,1024)
    var bi = 0
    for (i <- Range(0,n)) {
      bi = bi + rand.nextInt(2) + 1
      val b = bi.toString
      val e = random(n)
      val f = random(n)
      updQ.put(Database.insertRowCommand("s",(Map("b" -> b),Map("e" -> Absyn.FloatV(e), "f" -> Absyn.FloatV(f)))))
      var ai = 0
      for (j <- Range(0,rand.nextInt(java.lang.Math.ceil(java.lang.Math.sqrt(n)).toInt))) {
        ai = ai + rand.nextInt(2) + 1
        val a = ai.toString
        val c = random(n)
        val d = random(n)
        updQ.put(Database.insertRowCommand("r",(Map("a" -> a, "b" -> b),Map("c" -> Absyn.FloatV(c), "d" -> Absyn.FloatV(d)))))
      }
     if (i % 100 == 0) { println(i) }
    }
    updQ.close()

    // add key constraint at the end to avoid rechecking it a million times
    st.executeUpdate(Database.alterTableCommand(table_r,r_schema))
    st.executeUpdate(Database.alterTableCommand(table_s,s_schema))
    conn.commit()
    
    def resetSchema( tablename: String,  schema: Database.Schema): Unit = {
      st.executeUpdate("DELETE FROM schema WHERE tablename = '"+tablename+"';")
    
      schema.keyFields.foreach{k =>
        st.executeUpdate("INSERT INTO schema (tablename,fieldname,key,varfree) VALUES ('"+tablename+"','"+k+"',TRUE,FALSE)")
      }
      schema.valFields.foreach{v =>
        st.executeUpdate("INSERT INTO schema (tablename,fieldname,key,varfree) VALUES ('"+tablename+"','"+v+"',FALSE," +
          (if (schema.varfreeFields.contains(v)) {"TRUE"} else {"FALSE"}) + ")")
      }
      conn.commit()
    }

    resetSchema(table_r, r_schema)
    resetSchema(table_s, s_schema)
    conn.close()
  }
}
