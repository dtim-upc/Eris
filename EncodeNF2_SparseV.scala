import scala.collection.Iterator
import Database._
import java.sql.ResultSet

object EncodeNF2_SparseV extends Encoding {

  type EncodedQuery = (Absyn.Query,Absyn.Query)
  type EncodedSchema = (Database.Schema,Database.Schema)

  def encodeRow(r: String, sch:Schema, row: Row): List[String] = {
    // make value part of row into vectors
    val keys = row._1
    val vals = row._2
    val valVectors:Map[String,Vector[Option[String]]]  = vals.map{case (k,v) =>
      v.toExpr match {
        case Some(e) => (k,Expr.toVec(e))
        case None => throw NYI
      }
    }
    // insert one entry into base table containing all the arrays of coefficients
    List(
      insertRowCommand(
        r, 
          (keys,
            valVectors.toList.filter{
              case (k,_) => sch.varfreeFields.contains(k)
                }.map{
                  case (k,vec) => (k, Absyn.FloatV(vec.head._2))
                  }.toMap ++
            valVectors.toList.filterNot{
              case (k,_) => sch.varfreeFields.contains(k)
                }.map{
                  case (k,vec) =>
                    val ct=vec.flatMap{case(k,v) => Map(k -> v)}.filter{case (x,_) => x==None}
                    (k,
                    Absyn.SparseV(
                      vec.flatMap{
                        case(Some(x),coeff) => Map(x -> coeff.toDouble)
                        case(None,_) => Map("RemoveMe" -> 0.0)
                        }.filter{case (x,_) => x!="RemoveMe"}.toList,
                      if (ct.isEmpty) {0.0} else {ct.head._2}
                      )
                    )
                  }.toMap
 
          )
        )
      )
  }

  def baseEncoding(r: String) = r+"_NF2_SparseVec"
  def baseSchema(sch: Schema) = sch

  def constraintSchema() = Schema(Set("id"),Set("lhs","rhs"),Set())

  def schemaEncoding(r: String, sch: Schema) : Map[String,Schema] = {
    Map(baseEncoding(r) -> sch) 
  }

  def schemaEncodingWithSourceField(r: String, sch: Schema) : Map[String,(String,Schema)] = {
    Map(baseEncoding(r) -> ("THIS IS NOT USED IN THIS CASE",sch)) 
  }

  def encodeSchema(sch: Schema) : EncodedSchema = {
    (baseSchema(sch),constraintSchema())
  }

  def instanceSchemaEncoding(sch: Map[String, Schema]) : Map[String, Schema] = {
    sch.flatMap{case (r,r_sch) => schemaEncoding(r, r_sch)}
  }

  def insertEncodedStream(r: String, sch:Schema, s: Iterator[Row]): Iterator[String] = {
    // insert table
    s.flatMap{row => encodeRow(baseEncoding(r),sch,row)}  
  }

  // TODO: This works by patching the types of the translated tables, which is fragile because it depends on reversing  details of the encoding, it would be better to generate the view matching the translated schema(s) from the original schema.
  def schemaToViewDef(sourceName:String, r:String, sourceField:String, sch: Schema): Map[String,String] = {
    def DBDataType(tableName:String, attName: String, pk: String) =
      s"row(array[(case when $attName IS NULL OR $attName=0 then 1.0 else $attName end,(case when $attName IS NULL then '_' else '' end) ||'$tableName'||'_'||'$attName'||'_'||row_number() over (order by $pk))]::term[],case when $attName IS NULL then 0.0 else $attName end)::sparsevec"

    
    // this is the key of the original table
    val pk = sch.keyFields.mkString(",")
    val keyFields = sch.keyFields.map{f => (f,f+"::text")}
    val valFields = sch.valFields.diff(sch.varfreeFields).map{f => (f,DBDataType(sourceName,f,pk))}
    val varfreeFields = sch.varfreeFields.map(f => (f,f+"::double precision"))

    (keyFields ++ valFields ++ varfreeFields).toMap
  }

  def queryEncoding(q: Absyn.Query): EncodedQuery = {
    import Absyn._
    q match {
      case Relation(r) =>
        (Relation(baseEncoding(r)),Emptyset)

      case Select(q,p) =>
        val (q0,vc) = queryEncoding(q)
        (Select(q0,p),vc)

      case ProjectAway(q,fs) =>
        val (q0,vc) = queryEncoding(q)
        (ProjectAway(q0,fs),vc)

      case Project(q,fs) =>
        val (q0,vc) = queryEncoding(q)
        (Project(q0,fs),vc)

      case Rename(q,renaming) =>
        val (q0,vc) = queryEncoding(q)
        (Rename(q0,renaming),vc)

      // TODO: Disable this case
      case UnionAll(q1,q2) =>
        val (q10,vc1) = queryEncoding(q1)
        val (q20,vc2) = queryEncoding(q2)
        val schema1 = baseSchema(q1.schema)
        val schema2 = baseSchema(q2.schema)
        val remove1 = schema1.varfreeFields.diff(schema2.varfreeFields)
        val remove2 = schema2.varfreeFields.diff(schema1.varfreeFields)
        val create1 = remove1.foldLeft(List():List[(String,Expr)]){(l,a) => 
          (a+"_tmp", CreateSparseVector("",a))::l
          }
        val create2 = remove2.foldLeft(List():List[(String,Expr)]){(l,a) => 
          (a+"_tmp", CreateSparseVector("",a))::l
          }
        val rename1 = remove1.foldLeft(List():List[(String,String)]){(l,a) => 
          (a+"_tmp",a)::l
          }
        val rename2 = remove2.foldLeft(List():List[(String,String)]){(l,a) => 
          (a+"_tmp",a)::l
          }
        (UnionAll(
          Rename(ProjectAway(Derivation(q10,create1),remove1.toList),rename1),
          Rename(ProjectAway(Derivation(q20,create2),remove2.toList),rename2)
          ), 
        if (vc1==Emptyset && vc2==Emptyset) { Emptyset }
        else if (vc1==Emptyset) {vc2}
        else if (vc2==Emptyset) {vc1}
        else {UnionAll(vc1,vc2)}
        )
        
      case DUnion(q1,q2,attr) =>
        val (q10,vc1) = queryEncoding(q1)
        val (q20,vc2) = queryEncoding(q2)
        val schema1 = baseSchema(q1.schema)
        val schema2 = baseSchema(q2.schema)
        val remove1 = schema1.varfreeFields.diff(schema2.varfreeFields)
        val remove2 = schema2.varfreeFields.diff(schema1.varfreeFields)
        val create1 = remove1.foldLeft(List():List[(String,Expr)]){(l,a) => 
          (a+"_tmp", CreateSparseVector("",a))::l
          }
        val create2 = remove2.foldLeft(List():List[(String,Expr)]){(l,a) => 
          (a+"_tmp", CreateSparseVector("",a))::l
          }
        val rename1 = remove1.foldLeft(List():List[(String,String)]){(l,a) => 
          (a+"_tmp",a)::l
          }
        val rename2 = remove2.foldLeft(List():List[(String,String)]){(l,a) => 
          (a+"_tmp",a)::l
          }
        (DUnion(
          Rename(ProjectAway(Derivation(q10,create1),remove1.toList),rename1),
          Rename(ProjectAway(Derivation(q20,create2),remove2.toList),rename2),
          attr),
        if (vc1==Emptyset && vc2==Emptyset) { Emptyset }
        else if (vc1==Emptyset) {vc2}
        else if (vc2==Emptyset) {vc1}
        else {UnionAll(vc1,vc2)}
        )

      case Derivation(q,List((f,Num(c)))) =>
        val (q0,vc) = queryEncoding(q)
        (Derivation(q0,List((f,Num(c)))),vc)

      case Derivation(q,List((f,Var(a)))) =>
        val (q0,vc) = queryEncoding(q)
        (Derivation(q0,List((f,Var(a)))),vc)

      case Derivation(q,List((f,Times(Var(a),Num(c))))) =>
        val (q0,vc) = queryEncoding(q)
        val schema = baseSchema(q.schema)
        (if (schema.varfreeFields.contains(a)) {
          Derivation(q0,List((f,Times(Var(a),Num(c)))))
        } else {
          Derivation(q0,List((f,ArrayScalar(Var(a),Num(c)))))
        },
        vc)

      case Derivation(q,List((f,Times(Num(c),Var(a))))) =>
        queryEncoding(Derivation(q,List((f,Times(Var(a),Num(c))))))

      case Derivation(q,List((b,Times(UMinus(Num(c)),Var(a))))) =>
        queryEncoding(Derivation(q,List((b,Times(Num(-c),Var(a))))))

      case Derivation(q,List((f,Times(Var(a),Var(b))))) =>
        val (q0,vc) = queryEncoding(q)
        val schema = baseSchema(q.schema)
        // The fourth case should not happen, because it is not a linear expression
        if (schema.varfreeFields.contains(b) && !schema.varfreeFields.contains(a)) {
          (Derivation(q0,List((f,ArrayScalar(Var(a),Var(b))))),vc)
        } else if (schema.varfreeFields.contains(a) && !schema.varfreeFields.contains(b)) {
          (Derivation(q0,List((f,ArrayScalar(Var(b),Var(a))))),vc)
        } else {//(schema.varfreeFields.contains(a) && schema.varfreeFields.contains(b)) 
          (Derivation(q0,List((f,Times(Var(b),Var(a))))),vc)
          }

      case Derivation(q,List((f,Div(Var(a),Num(c))))) =>
        queryEncoding(Derivation(q,List((f,Times(Var(a),Num(1/c))))))

      // This case assumes the second variable is actually constant
      case Derivation(q,List((f,Div(Num(c),Var(a))))) => 
        val (q0,vc) = queryEncoding(q)
        (Derivation(q0,List((f,Times(Num(c),Inv(Var(a)))))),vc)

      // This case assumes the second variable is actually constant
      case Derivation(q,List((f,Div(Var(a),Var(b))))) =>
        val (q0,vc) = queryEncoding(q)
        val schema = baseSchema(q.schema)
        if (schema.varfreeFields.contains(a)) {
          (Derivation(q0,List((f,Times(Var(a),Inv(Var(b)))))),vc)
        } else {
          (Derivation(q0,List((f,ArrayScalar(Var(a),Inv(Var(b)))))),vc)
        }

      case Derivation(q,List((f,Plus(Var(a),Num(c))))) =>
        val (q0,vc) = queryEncoding(q)
        val schema = baseSchema(q.schema)
        if (schema.varfreeFields.contains(a)) {
          (Derivation(q0,List((f,Plus(Var(a),Num(c))))),vc)
        } else {
          (Derivation(q0,List((f,ArrayPlus(Var(a),
            CreateSparseVector("",c.toString))
            ))),vc)
        }

      case Derivation(q,List((f,Plus(Num(c),Var(a))))) =>
        queryEncoding(Derivation(q,List((f,Plus(Var(a),Num(c))))))

      case Derivation(q,List((f,Plus(Var(a),Var(b))))) =>
        val (q0,vc) = queryEncoding(q)
        val schema = baseSchema(q.schema)
        if (schema.varfreeFields.contains(b) && schema.varfreeFields.contains(a)) {
          (Derivation(q0,List((f,Plus(Var(a),Var(b))))),vc)
        } else if (!schema.varfreeFields.contains(a) && !schema.varfreeFields.contains(b)) {
          (Derivation(q0,List((f,ArrayPlus(Var(b),Var(a))))),vc)
        } else if (!schema.varfreeFields.contains(a) && schema.varfreeFields.contains(b)) {
          (Derivation(q0,List((f,ArrayPlus(Var(a),
            CreateSparseVector("",b)
            )))),vc)
        } else {
          queryEncoding(Derivation(q,List((f,Plus(Var(b),Var(a))))))
        }
        
/* Case not really needed as soon as the parser does not generate "Inv"
      case Derivation(q,List((f,Inv(Var(a))))) =>
        val (q0,qm) = queryEncoding(q)
        def inplaceDeriv(q: Query) = {
          val q1 = Rename(q,List(("coeff","tmpcoeff")))
          val q2 = Derivation(q1,List(("coeff",Inv(Var("tmpcoeff")))))
          ProjectAway(q2,List("tmpcoeff"))
        }
        (Derivation(q0,List((f,Inv(Var(a))))),
          qm + (f -> inplaceDeriv(qm(a)) )
          )
*/

      case Aggregation(q,grouped, aggregated) =>
        val (q0,vc) = queryEncoding(q)
        (Aggregation(q0,grouped,aggregated),vc)

      case Coalesce(q, collapse) =>
        val (q0,vc) = queryEncoding(q)
        val schema = baseSchema(q.schema)
        val id = Gensym.freshVar("_COAL_")
        val gbBefore = schema.keyFields.mkString(",")
        val gbAfter = (schema.keyFields--collapse).mkString(",")
        val lluns = schema.valFields.toList.map{a => (a,CreateSparseVector("'"+id+"_ATTR_"+a+"_LLUN_'||"+(if(gbAfter.isEmpty) {"0"} else {"10000+row_number() over (order by "+gbAfter+")"}),"0"))}
        val eqs = schema.valFields.toList.map{a => (a,"'"+id+"_ATTR_"+a+"_EQ_'||"+(if(gbBefore.isEmpty) {"0"} else {"row_number() over (order by "+gbBefore+")"})) }.toMap
        val coalesced = Coalesce(Project(Relation(id),List()),collapse)
        val vc_new = schema.valFields.foldLeft(Emptyset:Query){
            (a,f) => 
              if (a==Emptyset) {
                DefineCTE(id, q0, Aggregation( NaturalJoin( NaturalJoin( AddSurrogate( Project(Relation(id), List()), "id", eqs(f)), Rename( Project( Derivation( coalesced, lluns), List(f)), List((f,"lhs")))), Project( (if (schema.varfreeFields.contains(f)) {Derivation(Relation(id),List(("rhs",CreateSparseVector("",f))))} else { Rename(Relation(id) , List((f,"rhs"))) }) , List("rhs"))), List("id"), List("lhs","rhs")))
              } else {
                UnionAll(a, DefineCTE(id, q0, Aggregation( NaturalJoin( NaturalJoin( AddSurrogate( Project(Relation(id), List()), "id", eqs(f)), Rename( Project( Derivation( coalesced, lluns), List(f)), List((f,"lhs")))), Project( (if (schema.varfreeFields.contains(f)) {Derivation(Relation(id),List(("rhs",CreateSparseVector("",f))))} else { Rename(Relation(id) , List((f,"rhs"))) }) , List("rhs"))), List("id"), List("lhs","rhs"))))
              }
            }
        val remove = schema.varfreeFields
        val create = remove.foldLeft(List():List[(String,Expr)]){(l,a) => 
          (a+"_tmp", CreateSparseVector("",a))::l
          }
        val rename = remove.foldLeft(List():List[(String,String)]){(l,a) => 
          (a+"_tmp",a)::l
          }
        (DefineCTE(id, q0, UnionAll(Derivation(coalesced,lluns), Rename(ProjectAway(Derivation(Singletons(Relation(id),collapse),create),remove.toList),rename))),
          if (vc==Emptyset) {vc_new}
          else {UnionAll(vc,vc_new)}
          )

      case NaturalJoin(q1,q2) =>
        val (q10,vc1) = queryEncoding(q1)
        val (q20,vc2) = queryEncoding(q2)
        (NaturalJoin(q10,q20),
          if (vc1==Emptyset && vc2==Emptyset) { Emptyset }
          else if (vc1==Emptyset) {vc2}
          else if (vc2==Emptyset) {vc1}
          else {UnionAll(vc1,vc2)}
          )
    }
  }

  def getEncodedView(vtable: String, eq: EncodedQuery): Map[String,Absyn.Query] = {
    val (q,_) = eq
    Map(baseEncoding(vtable) -> q) 
  }

  def iterateEncodedRows(iter: Iterator[Row]): Iterator[Row] = {
    // fold over the main iterator and in each step, pull off all of the matching  term structure 
    iter.map{case (keys,vals) =>
      val decodedvals = vals.map{case (k,v) =>
        var e : Absyn.Expr = Absyn.Num(0)
        v match {
          case Absyn.FloatV(f) => e = Absyn.Num(f)

          case Absyn.SparseV(a,b) =>
            var first=true
            Absyn.SparseV(a,b).toList.map{
              case (None,v1) =>
                if (v1!=0.0) {
                  if (first) {
                    e = Absyn.Num(v1)
                    first=false
                  } else {
                    e = Absyn.Plus(e,Absyn.Num(v1))
                  }
                }
              case (Some(k1),v1) =>
                if (first) {
                  e = Absyn.Times(Absyn.Num(v1),Absyn.Var(k1))
                  first=false
                } else {
                  e = Absyn.Plus(e,Absyn.Times(Absyn.Num(v1),Absyn.Var(k1)))
                }
              }
          }
        (k,Absyn.ExprV(e))
        }
      (keys,decodedvals)
    }
  }

  def iterateEncodedTable(conn: java.sql.Connection, r: String, sch: Schema): Iterator[Row] = {
    //build iterators for each table
    val blocksize = 4096
    val (sch_0,_) = encodeSchema(sch)
    val r_0 = baseEncoding(r)
    def ord_sql(x: String) =
      conn.prepareStatement("SELECT * FROM "+x, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)
    val iter: Iterator[Row] = Database.iterateRelation(Database.iterateQuery(ord_sql(r_0),blocksize), sch_0)

    iterateEncodedRows(iter)
  }

  def iterateEncodedQuery(conn: java.sql.Connection, eq:EncodedQuery, es: Map[String, Schema]): Iterator[Row] = {
    val blocksize = 4096
    //build iterators for each table
    val (q0,_) = eq
    val sch_0 = Absyn.Query.tc(es,q0)
    val sql0 = Absyn.Query.sql(q0)
    def createStatement(x: String) = conn.prepareStatement(x, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)
    val iter: Iterator[Row] = Database.iterateRelation(Database.iterateQuery(createStatement(sql0),blocksize), sch_0)

    iterateEncodedRows(iter)
  }

  def iterateEncodedConstraints(conn: java.sql.Connection, eq:EncodedQuery, es: Map[String, Schema]): Iterator[Equation] = {
    val blocksize = 4096
    //build iterators for each table
    val (_,vc) = eq
    val sch_vc = Absyn.Query.tc(es,vc)
    val sql_vc = Absyn.Query.sql(vc)
    def createStatement(x: String) = conn.prepareStatement(x, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)
    val iter: Iterator[Row] = Database.iterateRelation(Database.iterateQuery(createStatement(sql_vc),blocksize), sch_vc)

    iterateEncodedRows(iter).map{ r =>
      Equation(r._2("lhs").toExpr.getOrElse(Absyn.Num(0)), r._2("rhs").toExpr.getOrElse(Absyn.Num(0)))
    }
  }

}
