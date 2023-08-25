import java.sql.Connection
import java.sql.PreparedStatement
import scala.collection.immutable._
import scala.collection.Iterator
import scala.util.Random
import Absyn._
import scala.annotation.tailrec

object Database {
  case object NYI extends Exception


  case class Schema(keyFields: Set[String], valFields: Set[String], varfreeFields: Set[String]) {
    override def toString: String = {
      keyFields.toList.mkString(",") + ";" +
      valFields.toList.mkString(",") + ";" +
      varfreeFields.toList.mkString(",")
    }
  }

  type InstanceSchema = Map[String,Schema]

  def loadSchema(conn: Connection) = {
    val schemaQuery = "select tablename, fieldname, key, varfree from schema;"
    val preparedStatement = conn.prepareStatement(schemaQuery)
    val result = streamQuery(preparedStatement)
    schemaOfResult(result)
  }

  object Gensym {
    var i: Int = 1
    var indexVarName: Map[String,Int] = Map(("ConstantTerm"->1))
    def freshVar(x: String) = {
      val newVar = x + i
      i = i + 1
      indexVarName += (newVar->i)
      newVar
    }
  }

  def printResult(l: Result) = {
    for (x <- l) {
      println(x.mkString(","))
    }
  }

  type ResultIterator = Iterator[Map[String,Option[String]]]
  def iterateQuery(q: PreparedStatement,blocksize: Integer): ResultIterator = {
    q.setFetchSize(blocksize)
    val rs = q.executeQuery()
    val meta = rs.getMetaData()
    val n = meta.getColumnCount()
    val fields = for (i <- Range(0,n,1))
                 yield (i+1, meta.getColumnName(i+1))
    var buffer : Map[String,Option[String]] = Map()
    new Iterator[Map[String,Option[String]]] {
      def fillBuffer() = {
        Map() ++ (
          for ((i,f) <- fields)
          yield (f,{
            val x = rs.getString(i)
            if (rs.wasNull()) {None} else {Some(x)}
          })
        )
      }
      def hasNext = { if (buffer.isEmpty) {
                        val exists=rs.next()
                        if (exists) buffer=fillBuffer()
                        exists
                      } else {
                        true
                      }
                      }
      def next() = {
        if (buffer.isEmpty) {
          throw new NoSuchElementException
        } else {
          val copy = buffer
          if (rs.next()) {
            buffer = fillBuffer()
          } else {
            buffer = Map()
          }
          copy
        }
      }
      }
  }

  type Result = LazyList[Map[String,Option[String]]]
  def streamQuery(q: PreparedStatement): Result = {
    val rs = q.executeQuery()
    val meta = rs.getMetaData()
    val n = meta.getColumnCount()
    val fields = for (i <- Range(0,n,1))
                 yield (i+1, meta.getColumnName(i+1))
    def get() : Result =
      if (rs.next()) {
        val row = for ((i,f) <- fields)
                  yield (f,{
                    val x = rs.getString(i)
                    if (rs.wasNull()) {None} else {Some(x)}
                  })
        (Map() ++ row) #:: get()
      } else {
        LazyList.empty
      }
    get()
  }
 
  def schemaOfResult(res: Result): InstanceSchema = {
    val tuples = res.map{row =>
      val tablename = row("tablename").get.toLowerCase
      val fieldname = row("fieldname").get.toLowerCase
      val key = row("key").get.equals("t")
      val varfree = row("varfree").get.equals("t")
      (tablename,fieldname,key,varfree)
    }
    tuples.groupMapReduce{_._1}{
      case (_,f,k,vf) =>
        if (k) {
          Schema(Set(f),Set(),Set())
        } else {
          if (vf) { 
            Schema(Set(),Set(f),Set(f))
          } else {
            Schema(Set(),Set(f),Set())
          }
        }
    }{
      case (Schema(k1,v1,f1),Schema(k2,v2,f2)) =>
        Schema(k1.union(k2), v1.union(v2), f1.union(f2))
    }
  }

  // streams result into lazy list of rows, using schema
  // useful for reading result and doing something with it
  // without materializing as an inmemory relation
  def streamRelation(result: Result, schema: Schema): LazyList[Row] = {
    result.map{case row =>
      val keys = Map() ++ schema.keyFields.map{k => (k,row(k).getOrElse("!!NULL!!"))}
      val vals = Map() ++ schema.valFields.map{k =>
        row(k) match {
          case Some(x) =>
            if (schema.varfreeFields.contains(k))
                 {(k,Absyn.FloatV(x.toDouble))}
            else {if (x.startsWith("{") && x.endsWith("}")) {
                    (k,Absyn.ArrayV(x.substring(1,x.length-1).split(",").toList.map{_.toDouble}))
                  } else {if (x.startsWith("(") && x.endsWith(")")) {
                    val pair = x.substring(1,x.length-1).replaceAll("\"","")
                    val terms = pair.substring(0,pair.lastIndexOf(","))
                    val termsList = if (terms.length>4) {
                                      terms.substring(2,terms.length-2).split("[)],[(]").toList
                                    } else { List() }
                      (k,
                        Absyn.SparseV(
                          termsList.map(term => (term.split(",")(1),term.split(",")(0).toDouble)),
                          pair.substring(pair.lastIndexOf(",")+1).toDouble
                          )
                        )
                    } else {
                      (k,Absyn.FloatV(x.toDouble))
                    }
                  }
                 }
          case None => (k,Absyn.NullV)
        }
      }
      (keys,vals)
    }
  }

  def iterateRelation(result: ResultIterator, schema: Schema): Iterator[Row] = {
    result.map{case row =>
      val keys = Map() ++ schema.keyFields.map{k => (k,row(k).getOrElse("!!NULL!!"))}
      val vals = Map() ++ schema.valFields.map{k =>
        row(k) match {
          case Some(x) =>
            if (schema.varfreeFields.contains(k))
                 {(k,Absyn.FloatV(x.toDouble))}
            else {if (x.startsWith("{") && x.endsWith("}")) {
                    (k,Absyn.ArrayV(x.substring(1,x.length-1).split(",").toList.map{_.toDouble}))
                  } else {if (x.startsWith("(") && x.endsWith(")")) {
                    val pair = x.substring(1,x.length-1).replaceAll("\"","")
                    val terms = pair.substring(0,pair.lastIndexOf(","))
                    val termsList = if (terms.length>4) {
                                      terms.substring(2,terms.length-2).split("[)],[(]").toList
                                    } else { List() }
                      (k,
                        Absyn.SparseV(
                          termsList.map(term => (term.split(",")(1),term.split(",")(0).toDouble)),
                          pair.substring(pair.lastIndexOf(",")+1).toDouble
                          )
                        )
                    } else {
                      (k,Absyn.FloatV(x.toDouble))
                    }
                  }
                 }
          case None => (k,Absyn.NullV)
        }
      }
      (keys,vals)
    }
  }


  // materializes relation in memory
  def getRelation(result: Result, schema: Schema): Rel = {
    Rel(Map() ++ streamRelation(result, schema))
  }

  def instanceOfSchema(conn: Connection, schema: InstanceSchema): Instance = {
    schema.map{case (r,s) =>
      val q_sql = Absyn.Query.sql(Absyn.Relation(r))
      val preparedStatement = conn.prepareStatement(q_sql)
      val stream = streamQuery(preparedStatement)
      (r,getRelation(stream,schema(r)))
    }
  }

  // escaping specific to Postgres ugh

  def escape(s: String): String = {
    s.replaceAll("'","''")
  }

  def insertRowCommand(r: String, row: Row): String = {
    val keyFields = row._1.keySet.toList
    val valFields = row._2.keySet.toList
    val fieldnames = "(" + (keyFields ++ valFields).mkString(",") + ")"
    val values = "(" + (keyFields.map{x => "'"+escape(row._1(x))+"'"} ++ valFields.map{x => row._2(x).toString}).mkString(",") + ")"
    raw"""INSERT INTO $r $fieldnames VALUES $values;""" 
  }

  def updateRowCommand(r: String, row: Row): String = {
    val keyFields = row._1.toList
    val valFields = row._2.toList
    val set_clause = valFields.map{case (f,v) => f + " = " + v}.mkString(",")
    val where_clause = keyFields.map{case (f,v) => f + " = '"+ escape(v) + "'"}.mkString(" AND ")
    raw"""UPDATE $r SET $set_clause WHERE $where_clause;""" 
  }

  def deleteRowCommand(r: String, row: Row): String = {
    val keyFields = row._1.toList
    val where_clause = keyFields.map{case (f,v) => f + " = '"+ escape(v) + "'"}.mkString(" AND ")
    raw"""DELETE FROM $r WHERE $where_clause;""" 
  }

  // q must be typechecked first
  // field order consistency is weirdly important here
  def insertQueryCommand(r: String, q: Query): String = {
    val q_sql = Absyn.Query.sql(q)
    val fieldnames = "(" + (q.schema.keyFields.toList ++ q.schema.valFields.toList).mkString(",") + ")"
    raw"""INSERT INTO $r $fieldnames ($q_sql);"""
  }

  def dropTableCommand(r: String): String = {
    raw"""DROP TABLE IF EXISTS $r CASCADE;"""
  }

  def dropViewCommand(r: String): String = {
    raw"""DROP VIEW IF EXISTS $r CASCADE;"""
  }

  def createTableCommand(r: String, fields: Map[String,(String,Boolean)]): String = {
    val fieldstr = fields.toList.map{
      case (f,(ty,true)) => f + " " + ty
      case (f,(ty,false)) => f + " " + ty + " NOT NULL"
    }.mkString(",\n  ")
    raw"""
CREATE TABLE $r
(
  $fieldstr
);
"""
  }

  // TODO: the 'empty' flag indicates whether the view should be forced to be empty
  // it is a hack to work around the fact that we create tables for both symbolic and varfree fields in
  // the partitioning encoding.  We should instead not create these tables, and correctly track whcih fields
  // are varfree/symbolic when translating the queries so that only partitioning queries for symbolic
  //  fields get created
  def createViewCommand(sourcename:String, r: String, fields: Map[String,String], empty: Boolean): String = {
    val fieldstr = fields.toList.map{
      case (f,ty) => ty + " AS " + f
    }.mkString(",\n  ")
    val test = if (empty) {"FALSE"} else {"TRUE"}
    raw"""
CREATE VIEW $r
AS (SELECT
  $fieldstr
FROM $sourcename
WHERE $test);
"""
  }

  def schemaToTableDef(sch: Schema, valFieldType: String): Map[String,(String,Boolean)] = {
    val keyFields = sch.keyFields.map{f => (f,("text",false))}
    val valFields = sch.valFields.filterNot(sch.varfreeFields.contains(_)).map(f => (f,(valFieldType, true)))
    val varfreeFields = sch.valFields.toList.filter(sch.varfreeFields.contains(_)).map(f => (f,("double precision",true)))
    (keyFields ++ valFields ++ varfreeFields).toMap
  }



  def alterTableCommand(r: String, sch: Schema): String = {
    val keyFieldTuple = "(" + sch.keyFields.toList.mkString(",") + ")"
    val r_pkey = r + "_pkey"
    raw"""ALTER TABLE $r ADD CONSTRAINT $r_pkey PRIMARY KEY $keyFieldTuple"""
  }

  type Vector[A] = Map[A,Double]

  def vecPlus[A](v1: Vector[A], v2: Vector[A]): Vector[A] = {
    v2.foldLeft(v1){case (v,(k,a)) => v.updatedWith(k){z => Some(a+z.getOrElse(0.0))}}
  }

  def vecScalar[A](alpha: Double, v: Vector[A]): Vector[A] = {
    v.foldLeft(Map[A,Double]()){case (v,(k,x)) => v + (k -> alpha * x)}
  }
  object Expr {
    // tail recursive function that accumulates map across expression in a stack friendly way
    // the list consists of either expressions Left(e) yet to be processed,
    // or reminders Right(c) of previous scalar values to restore after processing a subexpression.
    // When we encounter a variable or constant we add its contribution to the corresponding entry in the map
    // applying the current scalar.
    // When we encounter other constructs we decompose, using the heap instead of call stack to manage
    // larger terms, and adjusting the scalar argument and adding Right(c) reminders as needed 
    def toVec(e: Expr): Map[Option[String], Double] = {
      @tailrec
      def go(es:List[Either[Expr,Double]], scalar: Double, acc: Map[Option[String], Double]): Map[Option[String], Double] = es match {
        case Left(Var(x))::es => go(es,scalar,acc.updatedWith(Some(x)){z => Some(scalar + z.getOrElse(0.0))})
        case Left(Num(c))::es => go(es,scalar,acc.updatedWith(None){z => Some(scalar*c + z.getOrElse(0.0))})
        case Left(Plus(e1,e2))::es => go(Left(e1)::Left(e2)::es,scalar,acc)
        case Left(Minus(e1,e2))::es => go(Left(e2)::Right(scalar)::Left(e1)::es,-scalar,acc)
        case Left(Times(e,Num(c)))::es => go(Left(e)::Right(scalar)::es,c*scalar,acc)
        case Left(Times(Num(c),e))::es => go(Left(e)::Right(scalar)::es,c*scalar,acc)
        case Left(Div(e,Num(c)))::es => go(Left(e)::Right(scalar)::es,scalar/c,acc)
        case Left(UMinus(e))::es => go(Left(e)::Right(scalar)::es,-scalar,acc)
        case Right(newscalar)::es => go(es,newscalar,acc)
        case Nil => acc
        case _ => throw NYI
      }
      go(List(Left(e)),1.0,Map())
    }


    def simplifyLinear(e: Expr): Expr = {
      val v = toVec(e)
      val b = v.getOrElse(None,0.0)
      v.foldLeft[Expr](Num(b)){
        case (e,(None,_)) => e
        case (e,(Some(x),a)) => Plus(e,Times(Num(a),Var(x)))
      }
    }

  }
  case class Equation(e1: Expr, e2: Expr) {
    override def toString = e1.toString + " = " + e2.toString
    def fvs: Set[String] = fvsAcc(Set[String]())
    def fvsAcc(s: Set[String]) = e2.fvsAcc(e1.fvsAcc(s))
  }
  object Equation {
    // an affine vector whose value is zero precisely when the equation holds
    def toVec(eqn: Equation): Vector[Option[String]] = vecPlus(Expr.toVec(eqn.e1), vecScalar(-1.0,Expr.toVec(eqn.e2)))

    def toLPForm(eqn: Equation): (Vector[String],Double) = {
      val v = toVec(eqn)
      val b = v.getOrElse(None,0.0)
      val aVec = v.collect{ case (Some(x),a) => (x, a) }
      (aVec,-b) // negate since moving to other side of equation
    }
  }

  type Row = (Env[String],Env[Value])
  object Row {
    val rand = new Random()

    def generalizeNulls(s: Schema, r:Row):Row = r match {
      case (ks,vs) =>
        (ks,
          vs.map{case(a,NullV) => if (!s.varfreeFields.contains(a)) {(a,ExprV(Var(Gensym.freshVar("_"+a))))} else {(a,NullV)}
            case(a,x) => (a,x)})
    }

    def fuzzNonnullValues(s: Schema, r: Row):Row = r match {
      case (ks,vs) =>
        (ks,
          vs.map{case(a,FloatV(x)) => if (!s.varfreeFields.contains(a)) {(a,ExprV(Plus(Num(x),Var(Gensym.freshVar(a)))))} else {(a,FloatV(x))}
            case(a,x) => (a,x)})
    }

    def generalizeAll(s: Schema, r: Row):Row = r match {
      case (ks,vs) =>
        (ks,
          vs.map{case(a,x) => if (!s.varfreeFields.contains(a)) {(a,ExprV(Var(Gensym.freshVar(a))))} else {(a,x)} })
    }

    // TODO: see if we can avoid need for schema argument

    def distort(s: Schema, r: Row, sigma: Double): Row = r match {
      // This only distorts fields that are not varfree
      // In case of array implementation, it distorts the first position of the array
      case (ks,vs) =>
        (ks, vs.map{case (a,x) => if (!s.varfreeFields.contains(a)) {(a,Value.plus(x, FloatV(sigma * rand.nextGaussian())))} else {(a,x)}})
    }
    def obscure(s: Schema, r: Row, p: Double): Row = r match {
      case (ks,vs) =>
        (ks, vs.map{case (a,x) => if (!s.varfreeFields.contains(a)) {(a, if (rand.nextDouble() < p) {NullV} else {x})} else {(a,x)}})
    }

        //deletes rows with probability p
    def redact(r: Row, p: Double): Boolean = r match {
      case (ks,vs) => rand.nextDouble() < p
    }
  }

  case class Rel(m: Map[Env[String],Env[Value]]) {
    override def toString: String = {
      m.toList.map{case(ks,vs) => ks.toList.map{case(a,x) => x.toString}.mkString(",") + ";" + vs.toList.map{case(a,x) => x.toString}.mkString(",")}.mkString("\n")
    }

    def generalizeNulls(s: Schema): Rel = {
      Rel(m.map(Row.generalizeNulls(s,_)))
    }

    def fuzzNonnullValues(s: Schema): Rel = {
      Rel(m.map(Row.fuzzNonnullValues(s,_)))
    }

    def generalizeAll(s: Schema): Rel = {
      Rel(m.map(Row.generalizeAll(s,_)))
    }

    // TODO: see if we can avoid need for schema argument
    //applies gaussian noise with stdev sigma
    def distort(s: Schema, sigma: Double): Rel = {
      Rel(m.map(Row.distort(s,_,sigma)))
    }

    // replaces fields with null with probability p
    def obscure(s: Schema, p: Double): Rel = {
      Rel(m.map(Row.obscure(s,_,p)))
    }

    //deletes rows with probability p
    def redact(p: Double): Rel = {
      Rel(m.filterNot(Row.redact(_,p)))
    }


    def getEquations(): List[Equation] = {
      m.map{ r =>
        Equation(r._2("lhs").toExpr.getOrElse(Num(0)), r._2("rhs").toExpr.getOrElse(Num(0)))
      }.toList
    }

    // unary coalescing operation
    // naive approach that is not necessarily scalable
    def kappa(keys: List[String]): List[Equation] = {
      val reducedKeys = m.keySet.map{ks => (ks -- keys)}
      val keyMap = reducedKeys.map{ks => (ks,Gensym.freshVar("_L"))}.toMap
      m.toList.flatMap{case (ks,vs) =>
        vs.toList.flatMap{case (vk,vv) =>
          vv.toExpr match {
            case (Some(e)) => 
              List(Equation(Var(keyMap(ks -- keys)+"_"+vk), e))
            case None => List()
          }
        }
      }
    }

    def coalesce(otherRel: Rel): List[Equation] = {
      val newField = Gensym.freshVar("field")
      this.dunion(otherRel,newField).kappa(List(newField))
    }

    // discriminated union operation on relations
    def dunion(otherRel: Rel, attr: String): Rel = {
      Rel(m.map{case(ks,vs) => (ks + (attr->"1"),vs)} ++
        otherRel.m.map{case(ks,vs) => (ks + (attr->"2"),vs)})
    }

    def fvs = m.toList.flatMap{case (ks,vs) =>
      vs.toList.flatMap{case (a,x) => x.fvs}
    }.toSet
  }

  type Instance = Map[String,Rel]


  def getTable(conn: Connection, s: String, sch: Schema): Rel = {
    val keyfields = sch.keyFields.toList.mkString(",")
    val ps = conn.prepareStatement("SELECT * FROM "+s+" ORDER BY "+ keyfields)
    val stream = Database.streamQuery(ps)
    Database.getRelation(stream,sch)
  }

  case class UpdQueue(conn: java.sql.Connection, size: Int) {
    val queue = scala.collection.mutable.Queue[String]()
    def put(upd: String): Unit = {
      if (queue.length >= size) {
        flush()
      }
      queue.enqueue(upd)
    }
    def flush(): Unit = {
      val upds = queue.dequeueAll(_ => true)
      val st = conn.createStatement()
      st.executeUpdate(upds.mkString(";"))
      conn.commit()
    }
    def close(): Unit = {
      flush()
    }
  }

}
