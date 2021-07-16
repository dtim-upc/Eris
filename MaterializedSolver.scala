import scala.collection.immutable._
import java.lang.ProcessBuilder
import java.io.File
import java.io.BufferedReader
import java.io.InputStream
import java.io.FileWriter
import java.io.PrintWriter
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.util.stream.Collectors
import scala.jdk.CollectionConverters._
import scala.annotation.tailrec
import play.api.libs.json.Json

object Solver {
  val p = new RAParser()

  type Emitter = PrintWriter => Unit

  def timeIt[A](e: => A): (A,Double) = {
    val startTime = System.nanoTime()
    val x = e
    val endTime = System.nanoTime()
    (x,(endTime-startTime)/1e9)
  }

  def toLPForm(vars: List[String], sys: List[(Database.Vector[String],Double)]): (List[List[Double]], List[Double]) = {
    val A = sys.map{v => vars.map{x => v._1.getOrElse(x,0.0)}}
    val b = sys.map(_._2)
    (A,b)
  }

  def toLPFormSparse(sys: List[(Database.Vector[String],Double)]): (List[Database.Vector[String]], List[Double]) = {
    val A = sys.map(_._1)
    val b = sys.map(_._2)
    (A,b)
  }

  // building up lists by string concatenation is inefficient, we should be emitting straight to output stream
  def toPythonList(l: List[Double]): String = {
    "["+l.map(_.toString).mkString(",")+"]"
  }

  def emitPythonList(l: List[Double]): Emitter = {
    @tailrec
    def emit(l: List[Double], wr: PrintWriter): Unit = l match {
      case Nil => ()
      case dbl::Nil => wr.write(dbl.toString)
      case dbl::dbls =>
        wr.write(dbl.toString)
        wr.write(",")
        emit(dbls,wr)
    }
    {wr =>
      wr.write("[")
      emit(l,wr)
      wr.write("]")
    }

  }

  def toPythonMatx(a: List[List[Double]]): String = {
    "["+a.map(toPythonList).mkString(",")+"]"
  }

  def emitPythonMatx(a: List[List[Double]]): Emitter = {
    @tailrec
    def emit(l: List[List[Double]],wr: PrintWriter): Unit = l match {
      case Nil => ()
      case vec::Nil => emitPythonList(vec)(wr)
      case vec::vecs => 
        emitPythonList(vec)(wr)
        wr.write(",")
        emit(vecs,wr)
    }
    {wr =>
      wr.write("[")
      emit(a,wr)
      wr.write("]")
    }

  }

  def sparseMatx(fvs: List[String], a: List[Database.Vector[String]]) = {
    val m = a.length
    val atbl = List.tabulate(m)(identity).zip(a)
    val n = fvs.length
    val fvtbl = fvs.zip(List.tabulate(n)(identity)).toMap
    val coords:List[(Int,Int,Double)] = atbl.flatMap{case (row: Int,vec: Database.Vector[String]) =>
      vec.toList.map{case (vr,vl) =>
        (row, fvtbl(vr), vl)
      }
    }
    val i = toPythonList(coords.map(_._1))
    val j = toPythonList(coords.map(_._2))
    val data = toPythonList(coords.map(_._3))
    (data,i,j,m,n)
  }

  def toPythonMatxSparse(fvs: List[String], a: List[Database.Vector[String]]): String = {
    val (data,i,j,m,n) = sparseMatx(fvs,a)
    s"sparse.coo_matrix(($data,($i,$j)),shape=($m,$n))"
  }

  def emitPythonMatxSparse(fvs: List[String], a: List[Database.Vector[String]]): Emitter = {
    val (data,i,j,m,n) = sparseMatx(fvs,a)
    val emitter: Emitter = {wr =>
      wr.write("sparse.coo_matrix((")
      wr.write(data)
      wr.write(",(")
      wr.write(i)
      wr.write(",")
      wr.write(j)
      wr.write(s")),shape=($m,$n))")
    }
    emitter
  }


  def makeLP(n: Int, l: List[Double], a: List[List[Double]], u: List[Double]): String = {
    toOSQP(s"np.zeros(($n,$n))", s"np.zeros($n)", toPythonList(l), toPythonMatx(a), toPythonList(u))
  }

  def emitLP(n: Int, l: List[Double], a: List[List[Double]], u: List[Double]): Emitter = {
    emitOSQP({wr => wr.write(s"np.zeros(($n,$n))")},
      {wr => wr.write(s"np.zeros($n)")},
      emitPythonList(l),
      emitPythonMatx(a),
      emitPythonList(u))
  }

  def makeQP(fvs: List[String], l: List[Double], a: List[List[Double]], u: List[Double]): String = {
    val n = fvs.length
    // assign cost of x^2 to each variable not starting with "_"
    val diags = toPythonList(fvs.map{x => if (x(0) == '_') {0.0} else {1.0}})
    toOSQP(s"sparse.diags($diags)", s"np.zeros($n)", toPythonList(l), toPythonMatx(a), toPythonList(u))
  }

  def emitQP(fvs: List[String], l: List[Double], a: List[List[Double]], u: List[Double]): Emitter = {
    val n = fvs.length
    // assign cost of x^2 to each variable not starting with "_"
    val diags = emitPythonList(fvs.map{x => if (x(0) == '_') {0.0} else {1.0}})
    emitOSQP({wr =>
      wr.write("sparse.diags(")
      diags(wr)
      wr.write(")")},
      {wr => wr.write(s"np.zeros($n)")},
      emitPythonList(l),
      emitPythonMatx(a),
      emitPythonList(u))
  }

  def makeQPSparse(fvs: List[String], l: List[Double], a: List[Database.Vector[String]], u: List[Double]): String = {
    val n = fvs.length
    // assign cost of x^2 to each variable not starting with "_"
    val diags = toPythonList(fvs.map{x => if (x(0) == '_') {0.0} else {1.0}})
    toOSQP(s"sparse.diags($diags)", s"np.zeros($n)", toPythonList(l), toPythonMatxSparse(fvs,a), toPythonList(u))
  }

  def emitQPSparse(fvs: List[String], l: List[Double], a: List[Database.Vector[String]], u: List[Double]): Emitter = {
    val n = fvs.length
    // assign cost of x^2 to each variable not starting with "_"
    val diags = emitPythonList(fvs.map{x => if (x(0) == '_') {0.0} else {1.0}})
    emitOSQP({wr =>
      wr.write("sparse.diags(")
      diags(wr)
      wr.write(")")},
      {wr => wr.write(s"np.zeros($n)")},
      emitPythonList(l),
      emitPythonMatxSparse(fvs,a),
      emitPythonList(u))
  }

  def toOSQP(p: String, q: String, l: String, a: String, u: String): String = {
    raw"""
import osqp
import numpy as np
from scipy import sparse
import math
import json

P = sparse.csc_matrix($p)
q = np.array($q)
A = sparse.csc_matrix($a)
l = np.array($l)
u = np.array($u)
prob = osqp.OSQP()
prob.setup(P, q, A, l, u,  verbose=False)
res = prob.solve()
#print({"solution": res.x, "objective": res.info.obj_val})
print(json.dumps({"solution": res.x.tolist(), "objective": res.info.obj_val}))
"""
  }

  def emitOSQP(p: Emitter, q: Emitter, l: Emitter, a: Emitter, u: Emitter): Emitter = {wr =>
    wr.write(raw"""
import osqp
import numpy as np
from scipy import sparse
import math
import json

""")
      wr.write("P = sparse.csc_matrix(")
      p(wr)
      wr.write(")\n")
      wr.write("q = np.array(")
      q(wr)
      wr.write(")\n")
      wr.write("A = sparse.csc_matrix(")
      a(wr)
      wr.write(")\n")
      wr.write("l = np.array(")
      l(wr)
      wr.write(")\n")
      wr.write("u = np.array(")
      u(wr)
      wr.write(")\n")
      wr.write(raw"""
prob = osqp.OSQP()
prob.setup(P, q, A, l, u,  verbose=False)
res = prob.solve()
print(json.dumps({"solution": res.x.tolist(), "objective": res.info.obj_val}))
""")
  }



  def readProcessOutput(inputStream: InputStream): String = {
    val output = new BufferedReader(new InputStreamReader(inputStream))
    val x: java.util.List[String] = output.lines().collect(Collectors.toList())
    x.asScala.toList.mkString("\n")
  }


  def runOSQP(script: String): (List[Double],Double) = {

    val filename = "foo.py"
    val pythonname = if (new File("python.bat").exists) { "python.bat" } else { "python3" }
    val file = new File(filename)
    val writer = new PrintWriter(filename)
    writer.write(script)
    writer.close()
    
    val processBuilder = new ProcessBuilder(pythonname, file.getAbsolutePath())
    val process = processBuilder.start()
    val results = readProcessOutput(process.getInputStream())

    println(p.result)
    println(results)
    p.parseStr(p.result,results)

  }

  def parseJsonResult(json: play.api.libs.json.JsValue): (List[Double],Double) = {
    ((json \ "solution").get.as[List[Double]],
      (json \ "objective").get.as[Double])
  }
 
  def runOSQPStreaming(script: Emitter): (List[Double],Double) = {

    val filename = "foo.py"
    val pythonname = if (new File("python.bat").exists) { "python.bat" } else { "python3" }
    val file = new File(filename)
    val writer = new PrintWriter(new BufferedWriter(new FileWriter(filename)))
    script(writer)
    writer.close()
    
    val processBuilder = new ProcessBuilder(pythonname, file.getAbsolutePath())
    val process = processBuilder.start()
    val results = Json.parse(process.getInputStream())

    parseJsonResult(results)

  }
  // TODO: Split this into a function that extends a LP/QP problem with the constraints
  // entailed by coalescing a raw table and symbolic table, and another function that finalizes the
  // system and solves it.  This will make it easier to deal with multiple tables.
  def coalesce(raw_table: Database.Rel, symbolic: Database.Rel) = {
    val (coalesced,coalesceTime) = timeIt(raw_table.coalesce(symbolic))
    val (fvs,fvsTime) = timeIt(coalesced.foldLeft(Set[String]()){case (s,eqn) => eqn.fvsAcc(s)}.toList)
    val (lpForm,lpFormTime) = timeIt(coalesced.map(Database.Equation.toLPForm))
    if (lpForm.isEmpty) {
      println("No equations; skipping")
    } else if (fvs.isEmpty) {
      println("No free variables; skipping")
    } else {
      val ((a,b),solveLpFormTime) = timeIt(toLPFormSparse(lpForm))
      val (osqp,emitTime) = timeIt(emitQPSparse(fvs, b, a, b))
      val ((xs,x),solveTime) = timeIt(runOSQPStreaming(osqp))
      val (valuation,zipTime) = timeIt(fvs.zip(xs))
      println(coalesced.length + "," + fvs.length)
      println(s"$coalesceTime,$fvsTime,$lpFormTime,$solveLpFormTime,$emitTime,$solveTime,$zipTime")
      (valuation,x)
    }
  }

  def solve(connector: Connector, tbl: String, encoding: Encoding) = {
    val conn = connector.getConnection()
    val ctx = Database.loadSchema(conn)
    val (result,getTime) = timeIt(Database.getTable(conn,tbl,ctx(tbl)))
    val (enc_iter,iterTime) = timeIt(encoding.iterateEncodedTable(conn,tbl,ctx(tbl)))
    val (symbolic,symbolicTime) = timeIt(Database.Rel(enc_iter.toMap))
    val (coalesced,coalesceTime) = timeIt(coalesce(result,symbolic))
    println(s"$getTime,$iterTime,$symbolicTime,$coalesceTime")
    coalesced
  }

  def main(args:Array[String]) : Unit = {
    Class.forName("org.postgresql.Driver")

    val hostname = args(0)
    val dbname = args(1)
    val username = args(2)
    val password = args(3)
    val interactive = args.length < 6

    val connector = Connector(hostname,dbname,username,password)

    val encoder_to_use = Encoding.encoder_to_use(args.applyOrElse(4,{_:Int =>"partitioning"}))

    if (interactive) {
      val conn = connector.getConnection()
      val ctx = Database.loadSchema(conn)
      println(ctx)

      while (true) {
        print("solver> ")
        val tbl = scala.io.StdIn.readLine()
        val result = Database.getTable(conn,tbl,ctx(tbl))
        println("Raw result:")
        println(result)
        println("========")
        println("Symbolic result:")
        val enc_iter = encoder_to_use.iterateEncodedTable(conn,tbl,ctx(tbl))
        val symbolic = Database.Rel(enc_iter.toMap)
        println(symbolic)
        println("========")
        val (valuation,objective) = coalesce(result,symbolic)
        println("Valuation: " + valuation.toString)
        println("Objective: " + objective.toString)
      }
    } else {
      val tbl = args(5)
      val (valuation,objective) = solve(connector, tbl, encoder_to_use)
      println("Valuation: " + valuation.toString)
      println("Objective: " + objective.toString)
    }
  }
}

