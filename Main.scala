
object Main {

  val p = new RAParser()

  def main(args:Array[String]) : Unit = {
    val connector = Connector(args(0),args(1),args(2),args(3))
    println(args(3))
    val conn = connector.getConnection()

    val ctx = Database.loadSchema(conn)

    println(ctx)
    //val instance = instanceOfSchema(conn,ctx).map({case(r,rel) => (r,rel.generalizeNulls())})

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
//        println("In-memory evaluation:")
//        val inMemoryResult = Absyn.Query.eval(instance,q)
//        println(inMemoryResult)
//        println("=======")
//        val q_sql = Absyn.Query.sql(q)
//        println(q_sql)
//        val result = getQuery(q)
        println("----->>>>> Result schema")
        println(schema.toString)
//        println("Raw result:")
//        println(result)
        /*
        println("========")
        println("Fuzzed:")
        val fuzzedResult = result.fuzzNonnullValues(schema)
        println(fuzzedResult)
        println(fuzzedResult.fvs)
        println("========")
        println("Nulls generalized:")
        val nullGenResult = result.generalizeNulls(schema)
        println(nullGenResult)
        println(nullGenResult.fvs)
        println("========")
        println("All generalized:")
        val allGenResult = result.generalizeAll(schema)
        println(allGenResult)
        println(allGenResult.fvs)
        println("========")
        println("Distorted:")
        val distorted = result.distort(schema,1.0).obscure(schema,0.1).redact(0.1)
        println(distorted)
        println("========")
        println("Coalesced with distorted relation:")
        val symbolic = result.fuzzNonnullValues(schema).generalizeNulls(schema)
        val coalesced = distorted.coalesce(symbolic)
        println(coalesced)
        println("========")
        println("Vector form:")
        val lpForm = coalesced.map(Database.Equation.toLPForm)
        println(lpForm)
        println("========")
        println("Python form:")
        val fvs = symbolic.fvs.toList
        if (lpForm.isEmpty) {
          println("No equations; skipping")
        } else
        if (fvs.isEmpty) {
          println("No free variables; skipping")
        } else {
          val (a,b) = Solver.toLPForm(fvs,coalesced.map(Database.Equation.toLPForm))
          val osqp = Solver.makeLP(fvs.length, a, b)
          println(osqp)
          println("========")
          val (xs,x) = Solver.runOSQP(osqp)
          val valuation = fvs.zip(xs)
          println("Valuation: " + valuation)
          println("Objective: " + x)
        }
          */
        // test query encoding
//        val (q0,qm) = EncodeTruePivoting.queryEncoding(q)
//        val (q0,qm,(q0vc,qmvc)) = EncodePartitioning.queryEncoding(q)
        val (q0,q0vc) = EncodeNF2_SparseV.queryEncoding(q)
//        val (q0,qm) = EncodeNF2.queryEncoding(q)
        println("----->>>> Query encoding")
        println(q0)
//        println(qm)
        println(q0vc)
//        println(qmvc)
//        val enc_schema = EncodeTruePivoting.instanceSchemaEncoding(ctx)
//        val enc_schema = EncodePartitioning.instanceSchemaEncoding(ctx)
        val enc_schema = EncodeNF2_SparseV.instanceSchemaEncoding(ctx)
//        val enc_schema = EncodeNF2.instanceSchemaEncoding(ctx)
        println(enc_schema)
        val schema0 = Absyn.Query.tc(enc_schema,q0)
        val schema0vc = Absyn.Query.tc(enc_schema,q0vc)
//        val schemam = qm.map{case (f,qf) => (f,Absyn.Query.tc(enc_schema,qf))}
//        val schemamvc = qmvc.map{case (f,qf) => (f,Absyn.Query.tc(enc_schema,qf))}
        println("----->>>>>>> Query encoding schema")
        println(schema0)
        println(schema0vc)
//        val schemam = Absyn.Query.tc(enc_schema,qm)
//        println(schemam)
//        println(schemamvc)
//        val sqlm = Absyn.Query.sql(qm)
        val sql0 = Absyn.Query.sql(q0)
        val sql0vc = Absyn.Query.sql(q0vc)
//        val sqlm = qm.map{case (f,qf) => (f,Absyn.Query.sql(qf))}
//        val sqlmvc = qmvc.map{case (f,qf) => (f,Absyn.Query.sql(qf))}
        println("----->>>>>>> Query encoding SQL")
        println(sql0)
        println(sql0vc)
//        println(sqlm)
//        println(sqlmvc)
        println("========")
        println("Base result:")
        val result0 = getQuery(q0)
        println(result0)
        /*
        qm.map{case (f,qf) => 
          println("Field "+f+" result:")
          val resultm = getQuery(qf)
          println(resultm)
          }
        val resultm = getQuery(qm)
        println("Field table result:")
        println(resultm)
        */
        println("========")
        println("========")
        println("Base result VC:")
        val result0vc = getQuery(q0vc)
        println(result0vc)
        /*
        qmvc.map{case (f,qf) => 
          println("Field "+f+" result:")
          val resultm = getQuery(qf)
          println(resultm)
          }
        */
        println("========")
      } catch {
        case Absyn.TypeError(msg) => println("Type error: " + msg)
      }
    }
  }
}
