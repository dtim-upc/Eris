import scala.util.parsing.combinator.PackratParsers
import scala.util.parsing.combinator.syntactical.StandardTokenParsers

import Absyn._

class RAParser extends StandardTokenParsers with PackratParsers  {

  type P[+A] = PackratParser[A]

  def parseStr[A](parser: P[A], input: String): A = {
//    println(input)
    phrase(parser)(new lexical.Scanner(input)) match {
      case Success(ast, _) => ast
      case e: NoSuccess => sys.error(e.msg)
    }
  }

  def parse[A](parser: P[A], input: String): A = {
    val source = scala.io.Source.fromFile(input)
    val lines = try source.mkString finally source.close()
    parseStr(parser,lines)
  }

  lexical.reserved ++= List("TRUE", "FALSE", "AND", "OR", "NOT",
    "UNION", "DUNION", "INTERSECT", "EXCEPT", "JOIN", "GROUP", "SUM", "COAL", "coal",
  "true", "false", "and", "or", "not",
    "union", "dunion", "intersect", "except", "join", "group", "sum", "e", "E")
  lexical.delimiters ++= List("=","*", "/", "+", "-", "(", ")", "[", "]", "{", "}",
    "<>", "<", "<=", ">", ">=", ",", ".", ";", ":", "->", ":=", "^", "⨝", "∩", "∪", "⊎",
  "∧","∨","¬","⊤","⊥", "'")


  lazy val posNumber: P[Double] = (
    // this handles signed exponents but not unsigned
    // this seems to be because e1234 gets parsed as an identifier token
    // the right way to handle this is probably to define a regex-based parser
    // using the standard regular expression for floating point numebrs during tokenization
    // but this is workable as long as we only need to parse floating point numbers with signed exponents,
    // which for example python prints by default (but scala doesn't).
    // This approach also means that e/E cannot appear on its own as an identifier, which is
    // not ideal.
    // TODO: Redo the whole parser usign RegexParsers...
    (numericLit~"."~numericLit~("e"|"E")~("+"|"-")~numericLit ^^ {
      case x~y~z~_~s~e => (x+"."+z+"e"+s+e).toDouble }) |
    (numericLit~"."~numericLit ^^ { case x~y~z => (x+"."+z).toDouble }) |
    (numericLit~"." ^^ { case x~y => (x+".0").toDouble }) |
    (numericLit ^^ { x => x.toDouble })
  )

  lazy val identifier: P[String] = (
    ident | "e" | "E" ^^ {x => x}
  )

  lazy val number: P[Double] = (
    posNumber ^^ {x => x} |
    "-"~posNumber ^^ {case _~x => -x}
  )

  lazy val expression: P[Expr] =
    summation

  lazy val summation: P[Expr] =
    summation ~ "+" ~ product ^^ {
      case e1~"+"~e2 => Plus(e1,e2)
    } | summation ~ "-" ~ product ^^ {
      case e1~"-"~e2 => Minus(e1,e2)
    } | product

  lazy val product: P[Expr] =
    product ~ "*" ~ atomic ^^ {
      case e1~"*"~e2 => Times(e1,e2)
    } | product ~ "/" ~ atomic ^^ {
      case e1~"/"~e2 => Div(e1,e2)
    } | "-"~atomic ^^ {
      case x~y => UMinus(y)
    } | atomic

  lazy val atomic: P[Expr] = (
    (stringLit ^^ Atom ) |
      (identifier ^^ Var) |
      (number ^^ { x => Num(x) }) |
      "("~>expression<~")"
  )

  lazy val predicate: P[Pred] = (
    disjPredicate
  )

  lazy val disjPredicate: P[Pred] =
    disjPredicate ~ ("OR"|"or"|"∨") ~ conjPredicate ^^ {
      case p1~_~p2 => Or(p1,p2)
    } | conjPredicate

  lazy val conjPredicate: P[Pred] =
    conjPredicate ~ ("AND"|"and"|"∧") ~ atomicPredicate ^^ {
      case p1~_~p2 => And(p1,p2)
    } | atomicPredicate

  lazy val atomicPredicate: P[Pred] = (
    (("TRUE"|"true"|"⊤") ^^^ Bool(true))
      | (("FALSE"|"false"|"⊥") ^^^ Bool(false))
      | "("~>predicate<~")"
      | ("NOT"|"not"|"¬")~atomicPredicate ^^ {
        case _~p => Not(p)
      } | expression~"="~expression ^^ {
        case e1~"="~e2 => Eq(e1,e2)
      } | expression~"<>"~expression ^^ {
        case e1~"<>"~e2 => Neq(e1,e2)
      } | expression~"<"~expression ^^ {
        case e1~"<"~e2 => Lt(e1,e2)
      } | expression~"<="~expression ^^ {
        case e1~"<="~e2 => Leq(e1,e2)
      } | expression~">"~expression ^^ {
        case e1~">"~e2 => Gt(e1,e2)
      } | expression~">="~expression ^^ {
        case e1~">="~e2 => Geq(e1,e2)
      }
  )

  lazy val query: P[Query] =
    differenceQuery

  lazy val differenceQuery: P[Query] =
    differenceQuery ~ "-" ~ unionQuery ^^ {
      case q1~"-"~q2 => Difference(q1,q2)
    } | differenceQuery ~ ("EXCEPT"|"except") ~ unionQuery ^^ {
      case q1~_~q2 => Difference(q1,q2)
    } | unionQuery

  lazy val unionQuery: P[Query] =
    unionQuery ~ ("UNION"|"union"|"∪") ~ intersectionQuery ^^ {
      case q1 ~ _ ~ q2 => UnionAll(q1,q2)
    } | unionQuery ~ ("DUNION"|"dunion"|"⊎") ~ "[" ~ identifier ~ "]" ~ intersectionQuery ^^ {
      case q1 ~ _ ~ _ ~ att ~ _ ~ q2  => DUnion(q1,q2,att)
    } | intersectionQuery

  lazy val intersectionQuery: P[Query] =
    intersectionQuery ~ ("INTERSECT"|"intersect"|"∪") ~ joinQuery ^^ {
      case q1 ~ _ ~ q2 => Intersection(q1,q2)
    } | joinQuery

  lazy val joinQuery: P[Query] =
    joinQuery ~ ("JOIN"|"join"|"⨝") ~ basicQuery ^^ {
      case q1 ~ _ ~ q2 => NaturalJoin(q1,q2)
    } | basicQuery

  lazy val basicQuery: P[Query] =
    atomicQuery ~ "(" ~ predicate ~ ")" ^^ {
      case q~_~p~_ => Select(q,p)
    } | atomicQuery ~ "[" ~ predicate ~ "]" ~ atomicQuery^^ {
      case q1~_~p~_~q2 => ThetaJoin(q1,p,q2)
    } | atomicQuery ~ "[" ~ repsep(identifier,",") ~ "]" ^^ {
      case q~_~attrs~_ => Project(q,attrs)
    }  | atomicQuery ~ "[" ~ repsep(identifier,",") ~ ("SUM"|"sum") ~ repsep(identifier,",") ~ "]" ^^ {
      case q~_~attrs~_~sums~_ => Aggregation(q,attrs,sums)
    }  | atomicQuery ~ "[" ~ ("COAL"|"coal") ~ repsep(identifier,",") ~ "]" ^^ {
      case q~_~_~attrs~_ => Coalesce(q,attrs)
    } | atomicQuery ~ "[" ~ "^" ~ rep1sep(identifier,",") ~ "]" ^^ {
      case q~_~_~attrs~_ => ProjectAway(q,attrs)
    } | atomicQuery ~ "{" ~ rep1sep(derivation,",") ~ "}" ^^ {
      case q~_~derivs~_ => Derivation(q,derivs)
    } | atomicQuery ~ "{" ~ rep1sep(renaming,",") ~ "}" ^^ {
      case q~_~renamings~_ => Rename(q,renamings)
    } | atomicQuery

  lazy val derivation: P[(String,Expr)] =
    identifier ~ ":=" ~ expression ^^ {
      case x~_~e => (x,e)
    }

  lazy val renaming: P[(String,String)] =
    identifier ~ "->" ~ identifier ^^ {
      case x~_~y => (x,y)
    }

  lazy val atomicQuery: P[Query] = (
    identifier ^^ { r => Relation(r) }
      | "("~>query<~")"
  )

  lazy val viewDef: P[(String,Query)] = 
    identifier ~ ":=" ~ query ^^ {
      case x~":="~q => (x,q)
    }

  lazy val specification : P[List[(String,Query)]] = (
    repsep(viewDef,";")
  )

  lazy val result: P[(List[Double],Double)] = (
    "{" ~ stringLit ~ ":" ~ "[" ~ repsep(number,",") ~ "]" ~","~ stringLit ~ ":" ~ number ~ "}" ^^ {
      case _~_~_~v~_~_~_~_~x~_ => (v,x)
    }
  )


}
