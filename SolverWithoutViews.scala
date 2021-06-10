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

object SolverWithoutViews {
  val p = new RAParser()

  type Emitter[A] = PrintWriter => A

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

  def emitPythonList(l: List[Double]): Emitter[Unit] = {
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

  def emitPythonListIter(l: Iterator[Double]): Emitter[Unit] = {
    @tailrec
    def emit(wr: PrintWriter): Unit =
      if (l.hasNext) {
        val dbl = l.next
        wr.write(dbl.toString)
        if (l.hasNext) {
          wr.write(",")
          emit(wr)
        }
      }
    {wr =>
      wr.write("[")
      emit(wr)
      wr.write("]")
    }
  }

  def toPythonMatx(a: List[List[Double]]): String = {
    "["+a.map(toPythonList).mkString(",")+"]"
  }

  def emitPythonMatx(a: List[List[Double]]): Emitter[Unit] = {
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

  def emitPythonMatxSparse(fvs: List[String], a: List[Database.Vector[String]]): Emitter[Unit] = {
    val (data,i,j,m,n) = sparseMatx(fvs,a)
    val emitter: Emitter[Unit] = {wr =>
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

  def emitLP(n: Int, a: List[List[Double]], c: List[Double]): Emitter[Unit] = {
    emitOSQPEq({wr => wr.write(s"np.zeros(($n,$n))")},
      {wr => wr.write(s"np.zeros($n)")},
      emitPythonMatx(a),
      emitPythonList(c))
  }

  def makeQP(fvs: List[String], l: List[Double], a: List[List[Double]], u: List[Double]): String = {
    val n = fvs.length
    // assign cost of x^2 to each variable not starting with "_"
    val diags = toPythonList(fvs.map{x => if (x(0) == '_') {0.0} else {1.0}})
    toOSQP(s"sparse.diags($diags)", s"np.zeros($n)", toPythonList(l), toPythonMatx(a), toPythonList(u))
  }

  def emitQP(fvs: List[String], a: List[List[Double]], c: List[Double]): Emitter[Unit] = {
    val n = fvs.length
    // assign cost of x^2 to each variable not starting with "_"
    val diags = emitPythonList(fvs.map{x => if (x(0) == '_') {0.0} else {1.0}})
    emitOSQPEq({wr =>
      wr.write("sparse.diags(")
      diags(wr)
      wr.write(")")},
      {wr => wr.write(s"np.zeros($n)")},
      emitPythonMatx(a),
      emitPythonList(c))
  }

  def makeQPSparse(fvs: List[String], l: List[Double], a: List[Database.Vector[String]], u: List[Double]): String = {
    val n = fvs.length
    // assign cost of x^2 to each variable not starting with "_"
    val diags = toPythonList(fvs.map{x => if (x(0) == '_') {0.0} else {1.0}})
    toOSQP(s"sparse.diags($diags)", s"np.zeros($n)", toPythonList(l), toPythonMatxSparse(fvs,a), toPythonList(u))
  }

  def emitQPSparse(fvs: List[String], a: List[Database.Vector[String]], c: List[Double]): Emitter[Unit] = {
    val n = fvs.length
    // assign cost of x^2 to each variable not starting with "_"
    val diags = emitPythonList(fvs.map{x => if (x(0) == '_') {0.0} else {1.0}})
    emitOSQPEq({wr =>
      wr.write("sparse.diags(")
      diags(wr)
      wr.write(")")},
      {wr => wr.write(s"np.zeros($n)")},
      emitPythonMatxSparse(fvs,a),
      emitPythonList(c))
  }

  type BiMap[A,B] = (Map[A,B],Map[B,A])



  def emitData(r: Iterator[Database.Equation]): Emitter[(BiMap[String,Int],Int,Int)] = {wr =>
    @tailrec
    def emit(bimap: BiMap[String,Int],m: Int,n: Int, wr: PrintWriter): (BiMap[String,Int],Int,Int) = {
      if (r.hasNext) {
        val eqn = r.next
        val vec = Database.Equation.toVec(eqn)
        val (new_bimap,new_n) = vec.foldLeft(bimap,n){
          case ((bimap,n0),(None,c)) =>
            wr.write(s"($c,$m,0),")
            (bimap,n0)
          case (((to,from),n0),(Some(x),c)) =>
            if (!to.contains(x)) {
              wr.write(s"($c,$m,$n0),")
              // extend maps and increase n
              ((to + (x -> n0), from + (n0 -> x)),n0+1)
            } else {
              val j = to(x)
              wr.write(s"($c,$m,$j),")
              ((to,from),n0)
            }
        }
        emit(new_bimap,m+1,new_n,wr)
        
      } else {
        (bimap,m,n)
      }
    }
    wr.write("np.array([(1.0,0,0),")
    val result = emit((Map("_one_" -> 0),Map(0 -> "_one_")),1,1,wr)
    wr.write("])\n")
    result
  }

  def emitWeights(n: Int,fvsmap: BiMap[String,Int]): Emitter[Unit] = {wr =>
    @tailrec
    def emit(n0: Int, wr: PrintWriter): Unit = {
      if (n0 < n) {
        wr.write((if (fvsmap._2(n0)(0) == '_') {"0.0"} else {"1.0"}) + ",")
        emit(n0+1,wr)
      }
    }
    wr.write("sparse.diags([")
    emit(0,wr)
    wr.write("])\n")
  }


  // iteratively build OSQP input from stream of equations
  def emitQPSparseIter(r: Iterator[Database.Equation]): Emitter[(BiMap[String,Int],Int,Int)] = {wr =>
    wr.write(raw"""
import osqp
import numpy as np
from scipy import sparse
import math
import json

def get(x, i): 
    return np.array([r[i] for r in x])

""")
    wr.write("data = ")
    val (fvsmap,m,n) = emitData(r)(wr)
    wr.write("\n")
    wr.write("P = sparse.csc_matrix(")
    emitWeights(n,fvsmap)(wr)
    wr.write(")\n")
    wr.write(s"q = np.array(np.zeros($n))\n")
    wr.write(s"A = sparse.csc_matrix(sparse.coo_matrix((get(data,0),(get(data,1),get(data,2))),shape=($m,$n)))\n")
    wr.write(s"c = np.hstack([np.ones(1),np.zeros($m-1)])\n")
    wr.write(raw"""
prob = osqp.OSQP()
prob.setup(P, q, A, c, c,  verbose=False)
res = prob.solve()
print(json.dumps({"solution": res.x.tolist(), "objective": res.info.obj_val}))
""")
    (fvsmap,m,n)
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
prob.setup(P, q, A, l, u, verbose=False)
res = prob.solve()
#print({"solution": res.x, "objective": res.info.obj_val})
print(json.dumps({"solution": res.x.tolist(), "objective": res.info.obj_val}))
"""
  }

  def emitOSQP(p: Emitter[Unit], q: Emitter[Unit], l: Emitter[Unit], a: Emitter[Unit], u: Emitter[Unit]): Emitter[Unit] = {wr =>
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


  // specialized form where lower and upper bound matrix are equal
  def emitOSQPEq(p: Emitter[Unit], q: Emitter[Unit], a: Emitter[Unit], c: Emitter[Unit]): Emitter[Unit] = {wr =>
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
      wr.write("c = np.array(")
      c(wr)
      wr.write(")\n")
      wr.write(raw"""
prob = osqp.OSQP()
prob.setup(P, q, A, c, c, verbose=False)
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
 
  def runOSQPStreaming[A](script: Emitter[A]): (A,(List[Double],Double)) = {

    val filename = "foo.py"
    val pythonname = if (new File("python.bat").exists) { "python.bat" } else { "python3" }
    val file = new File(filename)
    val writer = new PrintWriter(new BufferedWriter(new FileWriter(filename)))
    val a = script(writer)
    writer.close()
    
    val processBuilder = new ProcessBuilder(pythonname, file.getAbsolutePath())
    val process = processBuilder.start()
    //val results = readProcessOutput(process.getInputStream())
    val results = Json.parse(process.getInputStream())

    //println(p.result)
    //println(results)
    //p.parseStr(p.result,results)
    (a,parseJsonResult(results))

  }
  // TODO: Split this into a function that extends a LP/QP problem with the constraints
  // entailed by coalescing a raw table and symbolic table, and another function that finalizes the
  // system and solves it.  This will make it easier to deal with multiple tables.
  def solveList(equations: List[Database.Equation]) = {
    val (fvs,fvsTime) = timeIt(equations.foldLeft(Set[String]()){case (s,eqn) => eqn.fvsAcc(s)}.toList)
    val (lpForm,lpFormTime) = timeIt(equations.map(Database.Equation.toLPForm))
    if (lpForm.isEmpty) {
      print("No equations; skipping;")
      (null,0)
    } else if (fvs.isEmpty) {
      print("No free variables; skipping;")
      (null,0)
    } else {
      val ((a,c),solveLpFormTime) = timeIt(toLPFormSparse(lpForm))
      val (osqp,emitTime) = timeIt(emitQPSparse(fvs, a, c))
      val (((),(xs,x)),solveTime) = timeIt(runOSQPStreaming(osqp))
      val (valuation,zipTime) = timeIt(fvs.zip(xs))
//      println("Equations: "+equations.length + ", Variables: " + fvs.length + ", Relevant variables: " + fvs.filterNot(_.startsWith("_")).length )
//      println(s"$fvsTime,$lpFormTime,$solveLpFormTime,$emitTime,$solveTime,$zipTime")
//      (valuation,x)
      (valuation,x/fvs.filterNot(_.startsWith("_")).length)
    }
  }

  def processList(conn: java.sql.Connection, encoder: Encoding, ctx: Database.InstanceSchema, es: Database.InstanceSchema, str: String) = {
    val q = p.parseStr(p.query,str)
    val schema = Absyn.Query.tc(ctx,q)
//    println("----->>>>> Result schema")
//    println(schema.toString)
    val eq = encoder.queryEncoding(q)
//    println("----->>>>> Encoded query")
//    println(eq)
//    val enc_iter_symbolic = encoder.iterateEncodedQuery(conn,eq,es)
//    val symbolic = Database.Rel(enc_iter_symbolic.toMap)
//    println("----->>>>> Data")
//    println(symbolic)
    val equations = encoder.iterateEncodedConstraints(conn,eq,es)
//    println("----->>>>> Constraints")
//    println(equations)
    val (valuation,objective) = solveList(equations.toList)
//    println("----->>>>> Solution")
    (valuation, objective)
  }

  def solveIter(r: Iterator[Database.Equation]) = {
    if (r.isEmpty) {
      (null,0,0,0,0)
    } else {
      val (osqp,emitTime) = timeIt(emitQPSparseIter(r))
      val (((fvsmap,m,n),(xs,x)),solveTime) = timeIt(runOSQPStreaming(osqp))
      @tailrec
      def getValuation(vs: List[Double],i: Int, acc:List[(String,Double)]): List[(String,Double)] = vs match {
        case Nil => acc
        case v::vs => getValuation(vs,i+1,(fvsmap._2(i),v)::acc)
      }
      val (valuation,valuationTime) = timeIt(getValuation(xs,0,List()))
      //println(valuation)
      val (relvars,relvarTime) = timeIt(valuation.filterNot{case (v,_) => v.startsWith("_")}.length)
//      println("Equations: "+m + ", Variables: " + n + ", Relevant variables: " + relvars )
//      print(m + ";" + relvars +";"+solveTime+ ";")
//      println(s"$emitTime,$solveTime,$valuationTime")
      //      (valuation,x)
//      (valuation,x/relvars)
      (valuation,x/relvars,m,relvars,solveTime)
    }
  }

  def processIter(conn: java.sql.Connection, encoder: Encoding, ctx: Database.InstanceSchema, es: Database.InstanceSchema, str: String) = {
    val q = p.parseStr(p.query,str)
    val schema = Absyn.Query.tc(ctx,q)
//    println("----->>>>> Result schema")
//    println(schema.toString)
    val eq = encoder.queryEncoding(q)
//    println("----->>>>> Encoded query")
//    println(eq)
    val (enc_iter_constraints,eqCreationTime) = timeIt(encoder.iterateEncodedConstraints(conn,eq,es))
    val (valuation,objective,eqs,vars,solveTime) = solveIter(enc_iter_constraints)
//    println("----->>>>> Solution")
    (valuation, objective, eqs, vars, eqCreationTime, solveTime)
    }
 
  def solve(connector: Connector, tbl: String, encoding: Encoding) = {
    val conn = connector.getConnection()
    conn.setAutoCommit(false)
    val ctx = Database.loadSchema(conn)
    val es = encoding.instanceSchemaEncoding(ctx)
    val result = processIter(conn, encoding, ctx, es, tbl)
    conn.close()
    result
 }

  def main(args:Array[String]) : Unit = {
    Class.forName("org.postgresql.Driver")

    val hostname = args(0)
    val dbname = args(1)
    val username = args(2)
    val password = args(3)
    val interactive = args.length < 6

    val connector = Connector(hostname,dbname,username,password)

    val encoder_to_use:Encoding = Encoding.encoder_to_use(args.applyOrElse(4,{_:Int =>"partitioning"}))

      

    if (interactive) {
      val conn = connector.getConnection()
      val ctx = Database.loadSchema(conn)
      println("----->>>>> Table schemas")
      println(ctx)
      val es = encoder_to_use.instanceSchemaEncoding(ctx)
      println("----->>>>> Encoded schemas")
      println(es)
      while (true) {
        try {
          print("solver> ")
          val (valuation,objective) = processList(conn, encoder_to_use, ctx, es, scala.io.StdIn.readLine())
//          println("Valuation: " + valuation.toString)
          println("Objective: " + objective.toString)
        } catch {
          case Absyn.TypeError(msg) => println("Type error: " + msg)
        }
      }
    } else {
      solve(connector, args(5), encoder_to_use)
    }
  }
}

