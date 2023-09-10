
object MainNF2 {

  val p = new RAParser()

  def main(args:Array[String]) : Unit = {
    val connector = Connector(args(0),args(1),args(2),args(3))
    println(args(3))
    val conn = connector.getConnection()

    val ctx = Database.loadSchema(conn)

    println(ctx)

    // should only be called after queries are typechecked
    def getQuery( q: Absyn.Query): Database.Rel = {
      val q_sql = Absyn.Query.sql(q)
      val ps = conn.prepareStatement(q_sql)
      val stream = Database.streamQuery(ps)
      Database.getRelation(stream, q.schema)
    }

    while (true) {
      print("query> ")
      val str = scala.io.StdIn.readLine()
      try {
        val q = p.parseStr(p.query,str)
        val schema = Absyn.Query.tc(ctx,q)
        println("----->>>>> Result schema")
        println(schema.toString)
        val (q0,q0vc) = EncodeNF2_SparseV.queryEncoding(q)
        println("----->>>> Query encoding")
        println(q0)
        println(q0vc)
        val enc_schema = EncodeNF2_SparseV.instanceSchemaEncoding(ctx)
        println(enc_schema)
        val schema0 = Absyn.Query.tc(enc_schema,q0)
        val schema0vc = Absyn.Query.tc(enc_schema,q0vc)
        println("----->>>>>>> Query encoding schema")
        println(schema0)
        println(schema0vc)
        val sql0 = Absyn.Query.sql(q0)
        val sql0vc = Absyn.Query.sql(q0vc)
        println("----->>>>>>> Query encoding SQL")
        println(sql0)
        println(sql0vc)
        println("========")
        println("Base result:")
        val result0 = getQuery(q0)
        println(result0)
        println("========")
        println("========")
        println("Base result VC:")
        val result0vc = getQuery(q0vc)
        println(result0vc)
        println("========")
      } catch {
        case Absyn.TypeError(msg) => println("Type error: " + msg)
      }
    }
  }
}
