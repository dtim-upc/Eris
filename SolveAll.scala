object SolveAll {

  import Timer.timeIt

  val p = new RAParser()

  def median(numbers: List[Double]): Double = {
    val sortedNumbers = numbers.sorted
    val middleIndex = numbers.length / 2
    if (numbers.length % 2 == 0) {
      (sortedNumbers(middleIndex - 1) + sortedNumbers(middleIndex)) / 2.0
    } else {
      sortedNumbers(middleIndex)
    }
  }

  def main(args:Array[String]) : Unit = {
    Class.forName("org.postgresql.Driver")

    val hostname = args(0)
    val dbname = args(1)
    val username = args(2)
    val password = args(3)
    val numRuns = 5

    val connector = Connector(hostname, dbname, username, password)

    val conn = connector.getConnection()

    val ps = conn.prepareStatement("SELECT COUNT(*) FROM S")
    val rs = ps.executeQuery()
    rs.next()
    val n = rs.getInt(1)
    conn.close()
    
    //./run-viewer.sh $1 $2 $3 $4 copy.spec
    Viewer.view(connector,"copy.spec")

    //./run-transformer.sh $1 $2 $3 $4 r2 1.0 0.1 0
    Transformer.transform(connector, "r2", 1.0, 0.1, 0.0)

    //./run-transformer.sh $1 $2 $3 $4 s2 1.0 0.1 0
    Transformer.transform(connector, "s2", 1.0, 0.1, 0.0)

    val encodings: List[(String, Encoding)] = List(("partitioning",EncodePartitioning), ("nf2_sparsev",EncodeNF2_SparseV))

    // do all the setup at the beginning so that the debugging messages get printed before the results
    for ((encname,encoding) <- encodings) {
      //./run-loader.sh $1 $2 $3 $4 r2 $5
      Loader.load(connector,"r", encoding, false)
      Loader.load(connector,"s", encoding, false)
      Loader.load(connector,"r2", encoding, false)
      Loader.load(connector,"s2", encoding, false)
    }

    val eval_queries = List(
      ("q1","r JOIN s"),
      ("q2","r{z := c + d}"),
      ("q3","((r{z:=1}){x:=c*z})"),
      ("q4","r[a SUM c]"),
      ("q5","r[b SUM c]"),
      ("q6","r DUNION[discr] r2"),
      ("q7","(r DUNION[discr] r2)[COAL discr]"))
    for ((encname,encoding: Encoding) <- encodings) {
      for ((id,str) <- eval_queries) {
        val conn = connector.getConnection()
        val ctx = Database.loadSchema(conn)
        val q = p.parseStr(p.query,str)
        val schema = Absyn.Query.tc(ctx,q)
        val enc_q = encoding.queryEncoding(q)
        val enc_schema = encoding.instanceSchemaEncoding(ctx)
        var runningTime: List[Double] = List()

        for (i <- Range(0, numRuns)) {
          val (result0,time) = Timer.timeIt(encoding.iterateEncodedQuery(conn,enc_q,enc_schema).foreach{case _ => ()})
          runningTime = time/1e6::runningTime
          println("eval" + "," + n + "," + encname +","+ id +","+ time/1e6)
          }
        println("eval_median" + "," + n + "," + encname +","+ id +","+ median(runningTime))
      }
    }


    val solve_queries = List(
      ("q1","((r JOIN s) DUNION[discr] (r2 JOIN s2))[coal discr]"),
      ("q2","(r{z := c + d} DUNION[discr] r2{z := c + d})[coal discr]"),
      ("q3","(((r{z:=1}){x:=c*z}) DUNION[discr] ((r2{z:=1}){x := c*z}))[coal discr]"),
      ("q4","(r[a SUM c] DUNION[discr] r2[a SUM c])[coal discr]"),
      ("q5","(r[b SUM c] DUNION[discr] r2[b SUM c])[coal discr]"))
    for ((encname,encoding) <- encodings) {
      for ((id,q) <- solve_queries) {
        var runningSolveTime: List[Double] = List()
        var runningEqCreationTime: List[Double] = List()
        var runningTotalTime: List[Double] = List()
	var runningObjective = 0.0
        var objective = 0.0
        for (i <- Range(0,numRuns)) {
          val ((_,o,eqs,vars,eqCreationTime,solveTime),totalTime) = Timer.timeIt(VirtualSolver.solve(connector, q, encoding))
          runningEqCreationTime = eqCreationTime/1e6::runningEqCreationTime
          runningSolveTime = solveTime/1e6::runningSolveTime
          runningTotalTime = totalTime/1e6::runningTotalTime
          objective = o
          println("solve" + "," + n + "," + encname + "," +  id + "," + objective.toString + "," + eqCreationTime/1e6 + "," + solveTime/1e6 + "," + totalTime/1e6)
        }

        println("solve_median" + ","+ n + ","+ encname + "," +  id  + "," + "@" + "," + median(runningEqCreationTime) + "," + median(runningSolveTime) + "," + median(runningTotalTime))
      }
    }
    
    
    // cleanup
    for ((encname, encoding) <- encodings) {
      Loader.load(connector,"r", encoding, true)
      Loader.load(connector,"s", encoding, true)
      Loader.load(connector,"r2", encoding, true)
      Loader.load(connector,"s2", encoding, true)
    }
  
    val conn2 = connector.getConnection()
    val st = conn2.createStatement()
    st.executeUpdate(Database.deleteRowCommand("schema", new Database.Row(Map(("tablename"->"r2")),Map())))
    st.executeUpdate(Database.deleteRowCommand("schema", new Database.Row(Map(("tablename"->"s2")),Map())))
    st.executeUpdate(Database.dropTableCommand("r2"))
    st.executeUpdate(Database.dropTableCommand("s2"))
    conn2.close()
  }

}




