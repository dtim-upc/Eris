import scala.collection.immutable._
import Database.Schema
import Database.Rel
import scala.annotation.tailrec


// TODO: Split this into a module for vanilla extended relational algebra (with e.g. standard difference, intersection, theta joins)
// and another one for the language used in the paper which has discriminated union and domain subtraction
// and not union, difference, intersection, or theta joins
// Typechecking for plain queries can ignore the map schemas.
// TODO: an abstract syntax for SQL that we translate RA/MapRA to.

object Absyn {
  type Variable = String
  type Env[A] = Map[Variable,A]

  abstract class Value {
    def toExpr: Option[Expr] = None
    def fvs: Set[String] = Set()
  }
  case class SparseV(l: List[(String,Double)], constantTerm: Double) extends Value {
    override def toString = "row(array["+l.map{case (varname,coeff) => "row("+coeff.toString+",'"+varname+"')"}.mkString(",")+"]::term[],"+constantTerm.toString+")::sparsevec"
    def toList = List((None,constantTerm))++(l.map{case (k,v) => (Some(k),v)})
  }
  case class ArrayV(l: List[Double]) extends Value {
    override def toString = "array["+l.map{_.toString}.mkString(",")+"]"
    def toList = l
  }
  case class StringV(v: String) extends Value {
    override def toString = v 
  }
  case class FloatV(v: Double) extends Value {
    override def toString = v.toString
    override def toExpr = Some(Num(v))
  }
  case class ExprV(e: Expr) extends Value {
    override def toString = e.toString
    override def toExpr = Some(e)
    override def fvs = e.fvs
  }
  case object NullV extends Value {
    override def toString = "NULL"
  }

  case object NYI extends Exception
  case class TypeError(msg: String) extends Exception

  object Value {
    def eq(v1: Value, v2: Value): Boolean = (v1,v2) match {
      case (StringV(s1),StringV(s2)) => s1 == s2
      case (FloatV(v1), FloatV(v2)) => v1 == v2
      case _ => throw NYI
    }
    def lt(v1: Value, v2: Value): Boolean = (v1,v2) match {
      case (StringV(s1),StringV(s2)) => s1 < s2
      case (FloatV(v1), FloatV(v2)) => v1 < v2
      case _ => throw NYI
    }
    def plus(v1: Value, v2: Value): Value = (v1,v2) match {
      // This should be used only at distorting
      case (ArrayV(l), FloatV(v)) => val newl = (l.head+v) :: l.tail
                                     ArrayV(newl)
      case (FloatV(v1), FloatV(v2)) => FloatV(v1 + v2)
      case (ExprV(e1), FloatV(v2)) => ExprV(Plus(e1,Num(v2)))
      case (FloatV(v1),ExprV(e2)) => ExprV(Plus(Num(v1),e2))
      case (ExprV(e1), ExprV(e2)) => ExprV(Plus(e1,e2))
      case (NullV, _) => NullV
      case (_, NullV) => NullV
      case _ => throw NYI
    }
    def minus(v1: Value, v2: Value): Value = (v1,v2) match {
      case (FloatV(v1), FloatV(v2)) => FloatV(v1 - v2)
      case (ExprV(e1), FloatV(v2)) => ExprV(Minus(e1,Num(v2)))
      case (FloatV(v1),ExprV(e2)) => ExprV(Minus(Num(v1),e2))
      case (ExprV(e1), ExprV(e2)) => ExprV(Minus(e1,e2))
      case (NullV, _) => NullV
      case (_, NullV) => NullV
      case _ => throw NYI
    }
    def times(v1: Value, v2: Value): Value = (v1,v2) match {
      case (FloatV(v1), FloatV(v2)) => FloatV(v1 * v2)
      case (ExprV(e1), FloatV(v2)) => ExprV(Times(e1,Num(v2)))
      case (FloatV(v1),ExprV(e2)) => ExprV(Times(Num(v1),e2))
      case (ExprV(e1), ExprV(e2)) => ExprV(Times(e1,e2))
      case (NullV, _) => NullV
      case (_, NullV) => NullV
      case _ => throw NYI
    }
    def div(v1: Value, v2: Value): Value = (v1,v2) match {
      case (FloatV(v1), FloatV(v2)) => FloatV(v1 / v2)
      case (ExprV(e1), FloatV(v2)) => ExprV(Div(e1,Num(v2)))
      case (FloatV(v1),ExprV(e2)) => ExprV(Div(Num(v1),e2))
      case (ExprV(e1), ExprV(e2)) => ExprV(Div(e1,e2))
      case (NullV, _) => NullV
      case (_, NullV) => NullV
      case _ => throw NYI
    }
  }


  abstract class Expr {
    def fvs: Set[String] = Expr.fvs(this)
    def fvsAcc(s: Set[String]) : Set[String] = Expr.fvsAcc(this,s)
  }
  case class Array(l: List[Double]) extends Expr 
  case class Var(x: Variable) extends Expr
  case class Num(n: Double) extends Expr 
  case class Atom(s: String) extends Expr
  case class Plus(e1: Expr, e2: Expr) extends Expr
  case class ArrayPlus(e1: Expr, e2: Expr) extends Expr
  case class ArrayScalar(e1: Expr, e2: Expr) extends Expr 
  case class Minus(e1: Expr, e2: Expr) extends Expr
  // these methods should probably be deleted
  case class Times(e1: Expr, e2: Expr) extends Expr {
    override def toString = "(" + e1.toString + " * " + e2.toString + ")"
    override def fvsAcc(s: Set[String]) = e2.fvsAcc(e1.fvsAcc(s))
  }
  case class Div(e1: Expr, e2: Expr) extends Expr {
    override def toString = "(" + e1.toString + " / " + e2.toString + ")"
    override def fvsAcc(s: Set[String]) = e2.fvsAcc(e1.fvsAcc(s))
  }
  case class UMinus(e: Expr) extends Expr
  case class Inv(e: Expr) extends Expr 
  // creates an array by running a closed query q and sorting the results
  case class CreateArray(q: Query, varname:String, value: String) extends Expr
  // creates a sparse vector with a variable and constant value
  case class CreateSparseVector(variable: String, constant: String) extends Expr 

  object Expr {

    def fvsAcc(e: Expr,acc: Set[String]): Set[String] = {
      @tailrec
      def go(es:List[Expr],  acc:Set[String]): Set[String] = es match {
        case Var(x)::es => go(es,acc + x)
        case Num(c)::es => go(es,acc)
        case Plus(e1,e2)::es => go(e1::e2::es,acc)
        case Minus(e1,e2)::es => go(e1::e2::es,acc)
        case Times(e1,e2)::es => go(e1::e2::es,acc)
        case ArrayPlus(e1,e2)::es => go(e1::e2::es,acc)
        case ArrayScalar(e1,e2)::es => go(e1::e2::es,acc)
        case Div(e1,e2)::es => go(e1::e2::es,acc)
        case UMinus(e)::es => go(e::es,acc)
        case Inv(e)::es => go(e::es,acc)
        case Array(_)::es => go(es,acc)
        case Atom(_)::es => go(es,acc)
        case CreateSparseVector(_,_)::es => go(es,acc)
        case CreateArray(q,varname,value)::es => go(es,if (varname=="ConstantTerm") {acc} else {acc + varname})
        case Nil => acc
        case _ => throw NYI
      }
      go(List(e),acc)
    }
    def fvs(e: Expr) = fvsAcc(e,Set())

    def eval(ctx: Env[Value], e: Expr): Value = e match {
      case Array(a)     => ArrayV(a)
      case Num(n)       => FloatV(n)
      case Atom(s)      => StringV(s)
      case Var(x)       => ctx(x)
      case Plus(e1,e2)  => Value.plus(eval(ctx,e1), eval(ctx,e2))
      case Minus(e1,e2) => Value.minus(eval(ctx,e1), eval(ctx,e2))
      case Times(e1,e2) => Value.times(eval(ctx,e1), eval(ctx,e2))
      case Div(e1,e2)   => Value.div(eval(ctx,e1), eval(ctx,e2))
      case UMinus(e)    => Value.minus(FloatV(0.0),eval(ctx,e))
      case Inv(e)    => Value.div(FloatV(1.0),eval(ctx,e))
    }

    def sql(e: Expr): String = e match {
      case Array(a)       => a.toString
      case Num(n)       => n.toString
      case Atom(s)      => "text('"+Database.escape(s)+"')"
      case Var(x)       => x
      case Plus(e1,e2)  => "(" + sql(e1) + "+" + sql(e2) + ")"
      case ArrayPlus(e1,e2)  => "pairwise_sum(" + sql(e1) + "," + sql(e2) + ")"
      case ArrayScalar(e1,e2)  => "scalar_product(" + sql(e1) + "," + sql(e2) + ")"
      case Minus(e1,e2) => "(" + sql(e1) + "-" + sql(e2) + ")"
      case Times(e1,e2) => "(" + sql(e1) + "*" + sql(e2) + ")"
      case Div(e1,e2)   => "(" + sql(e1) + "/" + sql(e2) + ")"
      case UMinus(e)    => "-" + sql(e)
      case Inv(e)   => "(1.0/" + sql(e) + ")"
      case CreateArray(q,varname,value) =>
        "array(SELECT " +
        "  CASE WHEN varname='"+varname+"' THEN "+value+
        "  ELSE 0.0 END"+
        "  FROM "+ Query.sql_from(q,"_")+" ORDER BY index)"
      case CreateSparseVector(v,c) => if (v=="") { "ROW(ARRAY[]::term[]," + c + ")::sparsevec"
                                      } else {"ROW(ARRAY[(1,"+v+")]::term[]," + c + ")::sparsevec"}
    }

    // TODO: synthesize string/number types
    def tc(fields: Set[String],e: Expr): Boolean = e match {
      case Array(a)     => true
      case Var(x)       => fields.contains(x)
      case Atom(s)      => true
      case Num(n)       => true
      case Plus(e1,e2)  => tc(fields,e1) && tc(fields,e2)
      case ArrayPlus(e1,e2)  => tc(fields,e1) && tc(fields,e2)
      case ArrayScalar(e1,e2)  => tc(fields,e1) && tc(fields,e2)
      case Minus(e1,e2) => tc(fields,e1) && tc(fields,e2)
      case Times(e1,e2) => tc(fields,e1) && tc(fields,e2)
      case Div(e1,e2)   => tc(fields,e1) && tc(fields,e2)
      case UMinus(e)    => tc(fields,e)
      case Inv(e)    => tc(fields,e)
      case CreateSparseVector(v,c) => true
      case CreateArray(q,varname,value) => varname=="ConstantTerm"
    }

    // TODO: This implementation is too restrictive and prevents some valid cases
    def degree(fields: Set[String],e: Expr): Integer = e match {
      case Array(a)     => 1
      case Var(x)       => if (fields.contains(x)) {0} else {1}
      case Atom(s)      => 0
      case Num(n)       => 0
      case Plus(e1,e2)  => if (degree(fields,e1)==1 && degree(fields,e2)==1) {1} else {degree(fields,e1) + degree(fields,e2)}
      case ArrayPlus(e1,e2)  => if (degree(fields,e1)==1 && degree(fields,e2)==1) {1} else {degree(fields,e1) + degree(fields,e2)}
      case ArrayScalar(e1,e2)  => if (degree(fields,e1)==1 && degree(fields,e2)==1) {2} else {degree(fields,e1) + degree(fields,e2)}
      case Minus(e1,e2) => if (degree(fields,e1)==1 && degree(fields,e2)==1) {1} else {degree(fields,e1) + degree(fields,e2)}
      case Times(e1,e2) => degree(fields,e1) + degree(fields,e2)
      case Div(e1,e2)   => if (degree(fields,e2)>0) {2} else {degree(fields,e1)}
      case UMinus(e)    => degree(fields,e)
      case Inv(e)    => if (degree(fields,e)==0) {0} else {2}
      case CreateSparseVector(v,c) => if (v=="") {0} else {1} 
      case CreateArray(q,varname,value) => if (varname=="ConstantTerm") {0} else {1}
    }

  }
  // Booleans
  abstract class Pred
  case class Bool(n: Boolean) extends Pred
  case class Eq(e1: Expr, e2:Expr) extends Pred
  case class Neq(e1: Expr, e2:Expr) extends Pred
  case class Lt(e1: Expr, e2:Expr) extends Pred
  case class Leq(e1: Expr, e2:Expr) extends Pred
  case class Gt(e1: Expr, e2:Expr) extends Pred
  case class Geq(e1: Expr, e2:Expr) extends Pred
  case class And(p1: Pred, p2: Pred) extends Pred
  case class Or(p1: Pred, p2: Pred) extends Pred
  case class Not(p: Pred) extends Pred

  object Pred {
    def eval(ctx: Env[Value], p: Pred): Boolean = p match {
      case Bool(b)    => b
      case And(p1,p2) => eval(ctx,p1) && eval(ctx,p2)
      case Or(p1,p2)  => eval(ctx,p1) || eval(ctx,p2)
      case Not(p)     => !(eval(ctx,p))
      case Eq(e1,e2)  => Value.eq(Expr.eval(ctx,e1),Expr.eval(ctx,e2))
      case Neq(e1,e2) => !(Value.eq(Expr.eval(ctx,e1),Expr.eval(ctx,e2)))
      case Lt(e1,e2)  => Value.lt(Expr.eval(ctx,e1),Expr.eval(ctx,e2))
      case Leq(e1,e2) => !(Value.lt(Expr.eval(ctx,e2),Expr.eval(ctx,e1)))
      case Gt(e1,e2)  => Value.lt(Expr.eval(ctx,e2),Expr.eval(ctx,e2))
      case Geq(e1,e2) => !(Value.lt(Expr.eval(ctx,e1),Expr.eval(ctx,e2)))
    }

    def sql(p: Pred): String = p match {
      case Bool(b)    => if (b) {"TRUE"} else {"FALSE"}
      case And(p1,p2) => "("+sql(p1) + " AND " + sql(p2) +")"
      case Or(p1,p2)  => "("+sql(p1) + " OR " + sql(p2) +")"
      case Not(p)     => "(NOT "+sql(p)+")"
      case Eq(e1,e2)  => "("+ Expr.sql(e1) + "=" + Expr.sql(e2) +")"
      case Neq(e1,e2) => "("+ Expr.sql(e1) + "<>" + Expr.sql(e2) +")"
      case Lt(e1,e2)  => "("+ Expr.sql(e1) + "<" + Expr.sql(e2) +")"
      case Leq(e1,e2) => "("+ Expr.sql(e1) + "<=" + Expr.sql(e2) +")"
      case Gt(e1,e2)  => "("+ Expr.sql(e1) + ">" + Expr.sql(e2) +")"
      case Geq(e1,e2) => "("+ Expr.sql(e1) + ">=" + Expr.sql(e2) +")"
    }

    // TODO: check string/numeric types compatible in equality
    def tc(fields: Set[String], p: Pred): Boolean = p match {
      case Bool(b)    => true
      case And(p1,p2) => tc(fields,p1) && tc(fields,p2)
      case Or(p1,p2)  => tc(fields,p1) && tc(fields,p2)
      case Not(p)     => tc(fields,p)
      case Eq(e1,e2)  => Expr.tc(fields,e1) && Expr.tc(fields,e2)
      case Neq(e1,e2) => Expr.tc(fields,e1) && Expr.tc(fields,e2)
      case Lt(e1,e2)  => Expr.tc(fields,e1) && Expr.tc(fields,e2)
      case Leq(e1,e2) => Expr.tc(fields,e1) && Expr.tc(fields,e2)
      case Gt(e1,e2)  => Expr.tc(fields,e1) && Expr.tc(fields,e2)
      case Geq(e1,e2) => Expr.tc(fields,e1) && Expr.tc(fields,e2)
    }
  }


  abstract class Query {
    var schema = new Schema(new ListSet(), new ListSet(), new ListSet())
  }
  // TODO: This language conflates plain multiset queries and multidimensional/finite map queries.
  // Split this into two langauges, with simple SQL generation and schema checking for multiset queries
  // and refined typechecking and SQL generation for finite map queries
  case class Relation(r: String) extends Query
  case object Emptyset extends Query
  case class Select(q: Query, p: Pred) extends Query
  case class Project(q: Query, atts: List[String]) extends Query
  case class ProjectAway(q: Query, atts:List[String]) extends Query
  case class Intersection(q1: Query, q2: Query) extends Query
  case class UnionAll(q1: Query, q2: Query) extends Query
  case class Difference(q1: Query, q2: Query) extends Query
  case class NaturalJoin(q1: Query, q2: Query) extends Query
  case class ThetaJoin(q1: Query, p: Pred, q2: Query) extends Query
  case class Rename(q: Query, renaming: List[(String, String)]) extends Query
  case class Aggregation(q: Query, group: List[String], sum: List[String]) extends Query
  case class Derivation(q: Query, exprs: List[(String, Expr)]) extends Query
  case class NewRow(keys: List[(String, Expr)], values: List[(String, Expr)]) extends Query
  // Finite map operations
  case class DUnion(q1: Query, q2: Query, a: String) extends Query
  case class Coalesce(q: Query, collapse: List[String]) extends Query

  // Internal operations
  case class Singletons(q: Query, collapse: List[String]) extends Query
  // TODO: The "value" component is an uninterpreted SQL string.  Replace this with an expression,
  // or specialize this to its intended use for generating row numbers by some order and with some string prefix.
  case class AddSurrogate(q: Query, name:String, value:String) extends Query
  case class DefineCTE(name:String, cte: Query, q: Query) extends Query


  object Query {

    def sql_from(q: Query, name: String) = q match {
        case Relation(r) => r + " " + name
        case q => "("+sql(q)+") "+ name
    }
    def sql(q: Query): String = q match {
      case Relation(r) =>
        val fields = (q.schema.keyFields.toList ++ q.schema.valFields.toList).mkString(",")
        "SELECT "+fields+" FROM " + r

      case Emptyset =>
        val fields = (q.schema.keyFields.toList.map{case (s) => " TEXT('') AS "+s} ++ q.schema.valFields.toList.map{case (s) => " 0.0 AS "+s}).mkString(",")
        "SELECT "+fields+" WHERE FALSE"

      case Select(q,p) =>
        "SELECT * FROM "+ sql_from(q,"_") + " WHERE " + Pred.sql(p)

      case Project(q,l) =>
        "SELECT " + (if((q.schema.keyFields.toList++l).isEmpty) {"'Phantom'"} else {(q.schema.keyFields.toList++l).mkString(",")}) + " FROM " + sql_from(q,"_")

      case ProjectAway(q,l) =>
        val rest = (q.schema.valFields ++ q.schema.keyFields) -- l
        "SELECT " + rest.toList.mkString(",") + " FROM " + sql_from(q,"_")

      case Rename(q,renamings) =>
        val rest = (q.schema.valFields ++ q.schema.keyFields) -- (renamings.map(_._1))
        "SELECT " + (rest.toList ++ renamings.map{case (x,y) => x + " AS " + y}).mkString(",") + " FROM " + sql_from(q,"_")

      case UnionAll(q1,q2) =>
        val fields = (q.schema.keyFields.toList ++ q.schema.valFields.toList).mkString(",")
        "(SELECT "+fields+" FROM ("+sql(q1)+") _) UNION ALL (SELECT "+fields+" FROM ("+sql(q2)+") _)"

      case DUnion(q1,q2,attr) =>
        // again fragile, need to return fields in right order
        val keyfields = q1.schema.keyFields.toList
        val valfields = q1.schema.valFields.toList
        val fields0 = (keyfields++List("0 AS "+attr)++valfields).mkString(",")
        val fields1 = (keyfields++List("1 AS "+attr)++valfields).mkString(",")
        "(SELECT "+ fields0 + " FROM ("+sql(q1)+") _) UNION ALL (SELECT "+fields1+" FROM ("+sql(q2)+") _)"

      case Intersection(q1,q2) =>
        val fields = (q.schema.keyFields.toList ++ q.schema.valFields.toList).mkString(",")
        "(SELECT "+fields+" FROM ("+sql(q1)+") _) INTERSECT (SELECT "+fields+" FROM ("+sql(q2)+") _)"

        // this is relational difference rather than map key subtraction
      case Difference(q1,q2) =>
        val fields = (q1.schema.keyFields.toList ++ q1.schema.valFields.toList).mkString(",")
        "(SELECT "+fields+" FROM ("+sql(q1)+") _) EXCEPT (SELECT "+fields+" FROM ("+sql(q2)+") _)"

      case NaturalJoin(q1,q2) =>
        val fields = (q.schema.keyFields.toList ++ q.schema.valFields.toList).mkString(",")
        "SELECT "+fields+" FROM "+sql_from(q1,"_1")+" NATURAL JOIN "+sql_from(q2,"_2")

      case ThetaJoin(q1,p,q2) =>
        val fields = (q.schema.keyFields.toList ++ q.schema.valFields.toList).mkString(",")
        "SELECT "+fields+" FROM "+sql_from(q1,"_1")+", "+sql_from(q2,"_2")+ " WHERE "+ Pred.sql(p)

      case Aggregation(q,gl,sl) =>
        "SELECT "+ (gl++sl.map{x => "SUM("+x+") AS " + x}).mkString(",") +" FROM "+sql_from(q,"_") +
        (if(gl.isEmpty) {""} else {" GROUP BY " + gl.mkString(",")})

      case Coalesce(q,cl) =>
        val gb = q.schema.keyFields--cl
        val values = q.schema.valFields.map{a => "KEEP_ANY("+a+") AS "+a}
        "SELECT "+(gb++values).mkString(",")+
        " FROM "+sql_from(q,"_")+
        (if(gb.isEmpty) {""} else {" GROUP BY "+gb.mkString(",")})+
        " HAVING COUNT(*)>1"

      case Singletons(q,cl) =>
        val gb = q.schema.keyFields--cl
        val values = q.schema.valFields.map{a => "KEEP_ANY("+a+") AS "+a}
        "SELECT "+(gb++values).mkString(",")+
        " FROM "+sql_from(q,"_")+
        (if(gb.isEmpty) {""} else {" GROUP BY "+gb.mkString(",")})+
        " HAVING COUNT(*)=1"

      case Derivation(q,derivs) =>
        val rest = (q.schema.keyFields ++ q.schema.valFields) -- (derivs.map(_._1))
        "SELECT "+ (rest.toList ++ derivs.map{case (x,e) => Expr.sql(e) + " AS " + x}).mkString(",") +
            " FROM "+sql_from(q,"_")

      case NewRow(keys, values) =>
        "SELECT "+ ((keys ++ values).map{case (x,e) => Expr.sql(e) + " AS " + x}).mkString(",")

      case AddSurrogate(q,name,value) =>
        val attList = q.schema.keyFields ++ q.schema.valFields
        "SELECT "+ (attList.toList++List(value + " AS " + name)).mkString(",") +
            " FROM "+sql_from(q,"_")

      case DefineCTE(name,cte,q) =>
        "WITH "+name+" AS ("+sql(cte)+") "+sql(q)
    }

    def assertOrError(test: => Boolean, msg: String) = {
      if (!test) {
        throw TypeError(msg)
      }
    }


    def tc(ctx: Map[String,Schema], q: Query): Schema = {
      def tc_inner(q: Query) = q match {
        case Relation(r) =>
          ctx(r)
        case Emptyset => 
          q.schema
        case Select(q,p) =>
          val schema = tc(ctx,q)
          assertOrError(Pred.tc(schema.keyFields++schema.valFields,p),
            "Selection predicate " + p + " not well-formed" )
          schema
        case Project(q,attrs) =>
          val schema = tc(ctx,q)
          val attrSet = new ListSet() ++ attrs
          assertOrError(attrSet.subsetOf(schema.valFields),
            "Projection fields " + attrSet +
              " includes fields not present in underlying schema " +
              schema.valFields)
          Schema(schema.keyFields, new ListSet() ++ attrs, schema.varfreeFields.intersect(attrSet))
        case ProjectAway(q,attrs) =>
          val schema = tc(ctx,q)
          val attrSet = new ListSet() ++ attrs
          assertOrError(attrSet.subsetOf(schema.valFields),
            "Projection fields " + attrSet +
              " includes fields not present in underlying schema " +
              schema.valFields)
          Database.Schema(schema.keyFields, schema.valFields -- attrSet, schema.varfreeFields -- attrSet)
        case Rename(q,renamings) =>
          val schema = tc(ctx,q)
          val renamedAttributes = new ListSet()++renamings.map(_._1)
          val targetAttributes = new ListSet()++renamings.map(_._2)
          val allFields = schema.keyFields ++ schema.valFields
          // no attribute appears more than once as first or second component
          assertOrError(renamings.groupMapReduce( _._1){x => 1}(_+_).forall{_._2 == 1},
            "Attribute renamed more than once")
          assertOrError(renamings.groupMapReduce( _._2){x => 1}(_+_).forall{_._2 == 1},
            "More than one attribute is renamed to the same attribute")
          // LHS of renamings all appear among existing attributes
          assertOrError(renamedAttributes.subsetOf(allFields), "Renamed attributes " + renamedAttributes.toString + " are not present in the underlying query type" + allFields.toString)
          assertOrError(targetAttributes.intersect(allFields).subsetOf(renamedAttributes), "Attributes are renamed to existing attribute names")
          val newKeys = schema.keyFields--renamedAttributes++renamings.filter({x => schema.keyFields.contains(x._1)}).map(_._2)
          val newVals = schema.valFields--renamedAttributes++renamings.filter({x => schema.valFields.contains(x._1)}).map(_._2)
          val newVarfree = schema.varfreeFields--renamedAttributes++(renamings.filter({x => schema.varfreeFields.contains(x._1)}).map(_._2))
          assertOrError(newKeys.intersect(newVals).isEmpty,
            "After renaming, the sets of key and value fields overlap")
          Schema(newKeys,newVals,newVarfree)
        case UnionAll(q1,q2) =>
          val schema1 = tc(ctx,q1)
          val schema2 = tc(ctx,q2)
          assertOrError(schema1.keyFields == schema2.keyFields && schema1.valFields == schema2.valFields,
            "The schemas of subqueries of a union operator do not match")
          Schema(schema1.keyFields, schema1.valFields, schema1.varfreeFields intersect schema2.varfreeFields)
        case DUnion(q1,q2,att) =>
          val schema1 = tc(ctx,q1)
          val schema2 = tc(ctx,q2)
          assertOrError(schema1.keyFields == schema2.keyFields && schema1.valFields == schema2.valFields,
            "The schemas of subqueries of a discriminated union operator do not match")
          assertOrError(!(schema1.keyFields.contains(att)),
            "Disjoint union discriminant already present as a key field")
          assertOrError(!(schema1.valFields.contains(att)),
            "Disjoint union discriminant already present as a value field")
          Schema(schema1.keyFields++ Set(att), schema1.valFields, schema1.varfreeFields intersect schema2.varfreeFields )
        case Intersection(q1,q2) =>
          val schema1 = tc(ctx,q1)
          val schema2 = tc(ctx,q2)
          assertOrError(schema1.keyFields == schema2.keyFields && schema1.valFields == schema2.valFields,
            "The schemas of subqueries of an intersection operator do not match")
          Schema(schema1.keyFields, schema1.valFields, schema1.varfreeFields union schema2.varfreeFields)
        case Difference(q1,q2) =>
          val schema1 = tc(ctx,q1)
          val schema2 = tc(ctx,q2)
          assertOrError(schema1.keyFields == schema2.keyFields,
            "The key fields of a difference operator do not match")
          assertOrError(schema2.valFields.isEmpty,
            "The value fields of the second argument to a difference operator should be empty")
          schema1
        case NaturalJoin(q1,q2) =>
          val schema1 = tc(ctx,q1)
          val schema2 = tc(ctx,q2)
          assertOrError(schema1.valFields.intersect(schema2.valFields).isEmpty,
            "The value fields of a natural join operator should not overlap")
          Schema(schema1.keyFields union schema2.keyFields,
            schema1.valFields union schema2.valFields,
            schema1.varfreeFields union schema2.varfreeFields)
        case ThetaJoin(q1,p,q2) => 
          val schema1 = tc(ctx,q1)
          val schema2 = tc(ctx,q2)
          assertOrError(schema1.keyFields.intersect(schema2.keyFields).isEmpty,
            "The key fields of a theta join operator should not overlap")
          assertOrError(schema1.valFields.intersect(schema2.valFields).isEmpty,
            "The value fields of a theta join operator should not overlap")
          val schema = Schema(schema1.keyFields union schema2.keyFields,
            schema1.valFields union schema2.valFields,
            schema1.varfreeFields union schema2.varfreeFields)
          assertOrError(Pred.tc(schema.keyFields,p),
            "The theta join predicate " + p + " is not well-formed")
          schema
        case Derivation(q,derivs) =>
          val schema = tc(ctx,q)
          // Check no attributes are defined more than once
          assertOrError(derivs.groupMapReduce( _._1){x => 1}(_+_).forall{_._2 == 1},
            "The same field is assigned multiple times in a derivation operator")
          val newSet = new ListSet() ++ derivs.map(_._1)
          assertOrError((schema.keyFields ++ schema.valFields).intersect(newSet).isEmpty,
            ("The new attributes " ++ newSet.toString ++ "defined in a derivation operator should not already be present in the subquery type" ++ (schema.keyFields ++ schema.valFields).toString))
          assertOrError(derivs.forall{case (x,e) => Expr.tc(schema.valFields,e)},
            "A defining expression in a derivation operator is not well-formed")
          val newVarfreeFields = new ListSet() ++ derivs.filter{case (_,CreateSparseVector(_,_)) => false
                                                                case (_,CreateArray(_,_,_)) => false
                                                                case (_,e) => Expr.degree(schema.varfreeFields,e)==0} map {case (f,_) => f}
          assertOrError(derivs.forall{case (f,e) => Expr.degree(schema.varfreeFields,e)<=1},
            "Some expression in '"+ derivs +"' is not linear")
          Schema(schema.keyFields, schema.valFields ++ newSet, schema.varfreeFields.union(newVarfreeFields))
        case Aggregation(q,groups,aggrs) =>
          val schema = tc(ctx,q)
          val groupSet = new ListSet() ++ groups
          val aggrSet = new ListSet() ++ aggrs
          assertOrError(groupSet.subsetOf(schema.keyFields),
            "The grouped fields of a grouping operator should all be keys")
          assertOrError(aggrSet.subsetOf(schema.valFields),
            "The aggregated fields of a grouping operator should all be values")
          Schema(groupSet,aggrSet, aggrSet.intersect(schema.varfreeFields))
        case Coalesce(q,collapses) =>
          val schema = tc(ctx,q)
          val collapseSet = new ListSet() ++ collapses
          assertOrError(collapseSet.subsetOf(schema.keyFields),
            "The grouped fields of a coalescing operator should all be keys")
          Schema(schema.keyFields--collapseSet, schema.valFields, Set())
        case Singletons(q,collapses) =>
          val schema = tc(ctx,q)
          val collapseSet = new ListSet() ++ collapses
          assertOrError(collapseSet.subsetOf(schema.keyFields),
            "The grouped fields of a singletons operator should all be keys")
          Schema(schema.keyFields--collapseSet, schema.valFields, schema.varfreeFields)
        case NewRow(keys,values) =>
          val keysSet = new ListSet() ++ keys.map{case (a,_) => a}
          val valuesSet = new ListSet() ++ values.map{case (a,_) => a}
          Schema(keysSet, valuesSet, valuesSet)
        case AddSurrogate(q,name,value) =>
          val schema = tc(ctx,q)
          Schema( schema.keyFields+name, schema.valFields, schema.varfreeFields)
        case DefineCTE(name,cte,q) =>
          tc(ctx+(name->tc(ctx,cte)),q)
      }
      val schema = tc_inner(q)
      q.schema = schema
      schema
    }


    /// sums two vectors, assuming they have same keys
    def sumVectors(v1: Env[Value], v2: Env[Value]): Env[Value] = {
      val keys = v1.keySet ++ v2.keySet
      Map[String,Value]() ++ keys.map{k => (k,Value.plus(v1(k),v2(k)))}
    }
    def toRow(k: Env[String], v: Env[Value]) =
      k.map{case (x,y) => (x,StringV(y))} ++ v
    def eval(db: Map[String, Rel], q: Query): Rel = q match {
      case Relation(r) => db(r)

      case Emptyset => Rel(Map())

      case Select(q,p) =>
        val Rel(result) = eval(db,q)
        Rel(result.filter{case (k,v) => Pred.eval(toRow(k,v),p)})

      case Project(q,attrs) =>
        val Rel(result) = eval(db,q)
        Rel(result.map{case (k,v) =>
          (k, v.filter{case (a,x) => attrs.contains(a)})})

      case ProjectAway(q,attrs) =>
        val Rel(result) = eval(db,q)
        Rel(result.map{case (k,v) =>
          (k, v.filter{case (a,x) => !attrs.contains(a)})})

      case Rename(q,renaming) =>
        val Rel(result) = eval(db,q)
        def find(x: String):String = {
          renaming.find{case(x0,_) => x == x0} match {
            case None => x
            case Some((_,y)) => y
          }
        }
        Rel(result.map{case (ks,vs) =>
          (ks.map{case (k,v) => (find(k),v)},
            vs.map{case (k,v) => (find(k),v)})})
    
      case DUnion(q1,q2,attr) =>
        val Rel(result1) = eval(db,q1)
        val Rel(result2) = eval(db,q2)
        Rel(result1.map{case(ks,vs) => (ks + (attr->"1"),vs)} ++
             result2.map{case(ks,vs) => (ks + (attr->"2"),vs)})

      case NaturalJoin(q1,q2) =>
        val Rel(result1) = eval(db,q1)
        val Rel(result2) = eval(db,q2)
        val commonKeys = q1.schema.keyFields.intersect(q2.schema.keyFields)
        Rel(result1.flatMap{case (k1,v1) =>
          result2.flatMap{case (k2,v2) =>
            if (commonKeys.forall{k => k1(k) == k2(k)}) {
              Map() + ((k1 ++ k2) -> (v1 ++ v2))
            } else {Map()}
          }
        })

        // TODO: This doesn't match the SQL behavior
      case Difference(q1,q2) =>
        val Rel(result1) = eval(db,q1)
        val Rel(result2) = eval(db,q2)
        Rel(result1.filter{case (ks,vs) => !result2.keySet.contains(ks)})

      case Aggregation(q,groups,sums) =>
        val Rel(result) = eval(db,q)
        Rel(result.groupMapReduce{case (ks,vs) => ks.filter{case (k,v) => groups.contains(k)}}{case (ks,vs) => vs.filter{case (k,v) => sums.contains(k)}}(sumVectors(_,_)))

      case Derivation(q,exprs) =>
        val Rel(result) = eval(db,q)
        Rel(result.map{case(ks,vs) =>
          (ks, vs ++ exprs.map{case (x,e) =>
            (x,Expr.eval(toRow(ks,vs),e))})})

      // not implementing for map queries
      case Intersection(q1,q2) => throw NYI
      case ThetaJoin(q1,p,q2) => throw NYI
      case UnionAll(q1,q2) =>  throw NYI
    
    }
  }
}




