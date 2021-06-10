import scala.collection.Iterator
import Database._
import java.sql.ResultSet
import scala.annotation.tailrec

object EncodePartitioning extends Encoding {

  type EncodedQuery = (Absyn.Query,Map[String,Absyn.Query],(Absyn.Query,Map[String,Absyn.Query]))
  type EncodedSchema = (Database.Schema,Map[String,Database.Schema],(Database.Schema,Map[String,Database.Schema]))

  object Gensym {
    var i: Int = 0
    def freshVar(x: String) = {
      val newVar = x + i
      i = i + 1
      newVar
    }
  }

  def encodeRow(r: String, row: Row): List[String] = {
    // make value part of row into vectors
    val keys = row._1
    val vals = row._2
    val valVectors:Map[String,Vector[Option[String]]]  = vals.map{case (k,v) =>
      v.toExpr match {
        case Some(e) => (k,Expr.toVec(e))
        case None => throw NYI
      }
    }
    // insert one entry into base table containing all the constant coefficients
    insertRowCommand(baseEncoding(r), (keys, valVectors.map{case (k,v) => (k,Absyn.FloatV(v.getOrElse(None,0.0)))})) ::
      valVectors.toList.flatMap{case (k,vec) =>
        vec.flatMap{
          case (Some(x),coeff) =>
            List(insertRowCommand(fieldEncoding(r,k),(keys ++ Map("_var_" -> x), Map("_coeff_" -> Absyn.FloatV(coeff)))))
          case (None,coeff) => List()
        }
      }
  }

  def baseEncoding(r: String) = r+"_partitioning_ConstantTerms"
  def baseSchema(sch: Schema) = sch

  def fieldEncoding(r: String, vf: String) = r+"_partitioning_"+vf
  def fieldSchema(sch: Schema) =
    Schema(sch.keyFields ++ Set("_var_"), Set("_coeff_"), Set("_coeff_"))

  def constraintSchema() = Schema(Set("id"),Set("lhs","rhs"),Set())

  def schemaEncoding(r: String, sch: Schema) : Map[String,Schema] = {
    Map(baseEncoding(r) -> sch) ++
      sch.valFields.toList.map(vf =>
        (fieldEncoding(r,vf), fieldSchema(sch)))
  }

  def schemaEncodingWithSourceField(r: String, sch: Schema) : Map[String,(String,Schema)] = {
    Map(baseEncoding(r) -> ("THIS IS NOT USED IN THIS CASE",sch)) ++
      sch.valFields.toList.map(vf =>
        (fieldEncoding(r,vf), (vf,fieldSchema(sch))))
  }

  def encodeSchema(sch: Schema) : EncodedSchema = {
    (baseSchema(sch),Map() ++ sch.valFields.toList.map(vf => (vf,fieldSchema(sch))),(constraintSchema(),constraintSchema().valFields.toList.map(vf => (vf,fieldSchema(constraintSchema()))).toMap))
  }

  def instanceSchemaEncoding(sch: Map[String, Schema]) : Map[String, Schema] = {
    sch.flatMap{case (r,r_sch) => schemaEncoding(r, r_sch)}
  }

  def insertEncodedStream(r: String, sch:Schema, s: Iterator[Row]): Iterator[String] = {
    s.flatMap{row => encodeRow(r,row)}
  }

    // TODO: This works by patching the types of the translated tables, which is fragile because it depends on reversing  details of the encoding, it would be better to generate the view matching the translated schema(s) from the original schema.

  def schemaToViewDef(sourceName:String, r:String, sourceField:String, sch: Schema): Map[String,String] = {
    def DBDataType(attName: String) =
      s"CASE WHEN $attName IS NULL THEN 0.0::double precision ELSE $attName::double precision END"
    def DBNewVar(tableName: String, attName: String, pk: String) =
      s"(CASE WHEN $attName IS NULL then '_' else '' end) || '$tableName'||'_'||'$attName'||'_'||row_number() over (order by $pk)::text"
    def DBNewCoeff(attName: String) = s"(case when $attName IS NULL OR $attName=0 then 1.0 else $attName end)::double precision"


    val pk = sch.keyFields.filterNot(_=="_var_").mkString(",")
    val keyFields = sch.keyFields.filterNot(_=="_var_").map{f => (f,f+"::text")}
    val valFields = sch.valFields.diff(sch.varfreeFields).map{f => (f,DBDataType(f))}
    val varfreeFields = sch.varfreeFields.filterNot(_=="_coeff_").map(f => (f,f+"::double precision"))
    val varField = sch.keyFields.filter(_=="_var_").map{f => (f,DBNewVar(sourceName,sourceField,pk))}
    val coeffField = sch.varfreeFields.filter(_=="_coeff_").map{f => (f,DBNewCoeff(sourceField))}

    (keyFields ++ valFields ++ varfreeFields ++ varField ++ coeffField).toMap
  }

  def queryEncoding(q: Absyn.Query): EncodedQuery = {
    import Absyn._

    def variableConstraintsUnion(q10:Absyn.Query, q1m:Map[String,Absyn.Query], q20:Absyn.Query, q2m:Map[String,Absyn.Query]): (Absyn.Query,Map[String,Absyn.Query]) = {
        if (q10==Emptyset && q20==Emptyset) { (Emptyset,Map(("lhs",Emptyset),("rhs",Emptyset))) }
        else if (q10==Emptyset) { (q20,q2m) }
        else if (q20==Emptyset) { (q10,q1m) }
        else { 
          (UnionAll(q10,q20),
            q1m.map{case (f,qf) => (f,UnionAll(qf,q2m(f)))})
          }
        }
 
    def filterZeroCoeffs(q: Query): Query =
      Select(q, Neq(Var("_coeff_"),Num(0.0)))
    q match {
      case Relation(r) =>
        (Relation(baseEncoding(r)),
          q.schema.valFields.map(f => (f,Relation(fieldEncoding(r,f))))
            .iterator.to(Map),
            (Emptyset,Map(("lhs",Emptyset),("rhs",Emptyset))))

      case Select(q,p) =>
        val (q0,qm,vc) = queryEncoding(q)
        (Select(q0,p),
          qm.map{case(f,qf) => (f,Select(qf,p))},
          vc)

      case ProjectAway(q,fs) =>
        val (q0,qm,vc) = queryEncoding(q)
        (ProjectAway(q0,fs),
          qm.filterNot{case(f,_) => fs.contains(f)},
          vc)

      case Project(q,fs) =>
        val (q0,qm,vc) = queryEncoding(q)
        (Project(q0,fs),
          qm.filter{case(f,_) => fs.contains(f)},
          vc)

      case Rename(q,renaming) =>
        val (q0,qm,vc) = queryEncoding(q)
        def find(x: String):String = {
          renaming.find{case(x0,_) => x == x0} match {
            case None => x
            case Some((_,y)) => y
          }
        }
        (Rename(q0,renaming),
          qm.map{case (f,qf) => (find(f),qf)},
          vc)

        //TODO: Do not handle this case
      case UnionAll(q1,q2) =>
        val (q10,q1m,vc1) = queryEncoding(q1)
        val (q20,q2m,vc2) = queryEncoding(q2)
        (UnionAll(q10,q20),
          q1m.map{case (f,qf) => (f,UnionAll(qf,q2m(f)))},
        variableConstraintsUnion(vc1._1,vc1._2,vc2._1,vc2._2)
        )

      case DUnion(q1,q2,attr) =>
        val (q10,q1m,vc1) = queryEncoding(q1)
        val (q20,q2m,vc2) = queryEncoding(q2)
        (DUnion(q10,q20,attr),
          q1m.map{case (f,qf) => (f,DUnion(qf,q2m(f),attr))},
        variableConstraintsUnion(vc1._1,vc1._2,vc2._1,vc2._2)
        )

      case Derivation(q,List((f,Num(c)))) =>
        val (q0,qm,vc) = queryEncoding(q)
        val q_empty = Emptyset
        q_empty.schema = fieldSchema(q.schema)
        (Derivation(q0,List((f,Num(c)))),
          qm + (f -> q_empty),
          vc)

      case Derivation(q,List((f,Plus(Var(a),Var(b))))) =>
        val (q0,qm,vc) = queryEncoding(q)
        val schema = fieldSchema(q.schema)
        // TODO: this can lead to exponential blowup...
        (Derivation(q0,List((f,Plus(Var(a),Var(b))))),
          qm + (f ->
            filterZeroCoeffs(
              Aggregation(
                UnionAll(qm(a),qm(b)),
                schema.keyFields.toList,schema.valFields.toList))),
          vc)

      case Derivation(q,List((b,Var(a)))) =>
        val (q0,qm,vc) = queryEncoding(q)
        (Derivation(q0,List((b,Var(a)))),
          qm + (b -> qm(a)),
          vc)

      case Derivation(q,List((b,Times(Num(c),Var(a))))) =>
        if (c == 1.0) {queryEncoding(Derivation(q,List((b,Var(a)))))}
        else {
          val (q0,qm,vc) = queryEncoding(q)
          def inplaceDeriv(q: Query,c: Double) = {
            val q1 = Rename(q,List(("_coeff_","tmpcoeff")))
            val q2 = Derivation(q1,List(("_coeff_",Times(Num(c),Var("tmpcoeff")))))
            val q3 = Project(q2,List("_coeff_"))
            filterZeroCoeffs(q3)
          }
          // TODO: this can lead to exponential blowup...
          (Derivation(q0,List((b,Times(Num(c),Var(a))))),
            qm + (b -> inplaceDeriv(qm(a),c)),
            vc)
        }

      case Derivation(q,List((f,Times(Var(a),Var(b))))) =>
        val (q0,qm,vc) = queryEncoding(q)
        def varfreeDeriv(f_varfree: String, g: String) = {
          val q1 = Project(q0, List(f_varfree))
          val q2 = Rename(qm(g),List(("_coeff_","tmpcoeff")))
          val q3 = NaturalJoin(q1,q2)
          val q4 = Derivation(q3,List(("_coeff_",Times(Var(f_varfree),Var("tmpcoeff")))))
          filterZeroCoeffs(Project(q4,List("_coeff_")))
        }
        // TODO: this can lead to exponential blowup...
        val qm_f = (q.schema.varfreeFields.contains(a),q.schema.varfreeFields.contains(b)) match {
          case (true,true) => qm + (f -> Emptyset) // TODO: only needed because schema requires it
          case (true,false) => qm + (f -> varfreeDeriv(a,b))
          case (false,true) => qm + (f -> varfreeDeriv(b,a))
          case (false,false) => throw NYI // this case should not happen if query is well formed
        }
        (Derivation(q0,List((b,Times(Var(a), Var(b))))),
          qm_f,
          vc)
    
          // TODO: we should normalize things instead of handling special cases like this
      case Derivation(q,List((b,Times(Var(a),Num(c))))) =>
        queryEncoding(Derivation(q,List((b,Times(Num(c),Var(a))))))

      case Derivation(q,List((b,Times(UMinus(Num(c)),Var(a))))) =>
        queryEncoding(Derivation(q,List((b,Times(Num(-c),Var(a))))))

      case Derivation(q,List((b,Div(Var(c),Num(a))))) =>
        queryEncoding(Derivation(q,List((b,Times(Var(c),Num(1.0/a))))))

/* Case not really needed as soon as the parser does not generate "Inv"
      case Derivation(q,List((f,Inv(Var(a))))) =>
        val (q0,qm) = queryEncoding(q)
        def inplaceDeriv(q: Query) = {
          val q1 = Rename(q,List(("_coeff_","tmpcoeff")))
          val q2 = Derivation(q1,List(("_coeff_",Inv(Var("tmpcoeff")))))
          ProjectAway(q2,List("tmpcoeff"))
        }
        (Derivation(q0,List((f,Inv(Var(a))))),
          qm + (f -> inplaceDeriv(qm(a)) )
          )
 */
// this case is wrong... only works if the denominator is var-free and in that case we just
        // use the constant coefficient part.
      case Derivation(q,List((f,Div(Num(c),Var(a))))) =>
        val (q0,qm,vc) = queryEncoding(q)
        if (q.schema.varfreeFields.contains(a)) {
          (Derivation(q0,List((f,Div(Num(c),Var(a))))),
            qm + (f -> Emptyset ), // TODO: only needed because schema requires it
            vc)
        } else { // should not happen in a well formed query
          throw NYI
        }

      // The implementation assumes the second variable is actually constant
      case Derivation(q,List((f,Div(Var(a),Var(b))))) =>
        val (q0,qm,vc) = queryEncoding(q)
        def varfreeDeriv(numerator: String, denominator_varfree: String) = {
          val q1 = Project(q0, List(denominator_varfree))
          val q2 = Rename(qm(numerator),List(("_coeff_","tmpcoeff")))
          val q3 = NaturalJoin(q1,q2)
          val q4 = Derivation(q3,List(("_coeff_",Div(Var("tmpcoeff"),Var(denominator_varfree)))))
          filterZeroCoeffs(Project(q4,List("_coeff_")))
        }
        // TODO: this can lead to exponential blowup...
        val qm_f = (q.schema.varfreeFields.contains(a),q.schema.varfreeFields.contains(b)) match {
          case (true,true) => qm + (f -> Emptyset) // TODO: only needed because schema requires it
          case (false,true) => qm + (f -> varfreeDeriv(a,b))
          case (_,false) => throw NYI // this case should not happen if query is well formed
        }
        (Derivation(q0,List((f,Div(Var(a), Var(b))))),
          qm_f,
          vc)

      case Aggregation(q,grouped, aggregated) =>
        val (q0,qm,vc) = queryEncoding(q)
        (Aggregation(q0,grouped,aggregated),
          qm.filter{case (f,_) => aggregated.contains(f)}
            .map{case (f,qf) => (f,
              filterZeroCoeffs(Aggregation(qf,grouped ++ List("_var_"),List("_coeff_"))))
          },
          vc)

      case Coalesce(q, collapse) =>
        val (q0,qm,vc) = queryEncoding(q)
        val schema = baseSchema(q.schema)
        val id = Gensym.freshVar("_COAL_")
        val gbBefore = (schema.keyFields).mkString(",")
        val gbAfter = (schema.keyFields--(collapse.toList)).mkString(",")
        val eq = schema.valFields.toList.map{a => (a,"'"+id+"_ATTR_"+a+"_EQ_'||"+(if(gbBefore.isEmpty) {"0"} else {"row_number() over (order by "+gbBefore+")"})) }.toMap
        val lluns = schema.valFields.toList.map{a => (a,"'"+id+"_ATTR_"+a+"_LLUN_'||"+(if(gbAfter.isEmpty) {"0"} else {"row_number() over (order by "+gbAfter+")"})) }.toMap
        val constantTerms = schema.valFields.toList.map{a => (a,List(("lhs",Num(0.0)),("rhs",Var(a)))) }.toMap
        val zeros = schema.valFields.toList.map{a => (a,Num(0.0)) }
        val coalesced = Coalesce(Project(Relation(id),List()),collapse)
        val new_vc0 = DefineCTE(id,q0,qm.foldLeft(Emptyset:Query){(a,current) => if (a==Emptyset) {
                         Aggregation( NaturalJoin( AddSurrogate( Derivation(Relation(id),constantTerms(current._1)), "id", eq(current._1)), coalesced), List("id"), List("lhs","rhs"))
                        } else {
                        UnionAll(a, Aggregation( NaturalJoin( AddSurrogate( Derivation(Relation(id),constantTerms(current._1)), "id", eq(current._1)), coalesced), List("id"), List("lhs","rhs")))
                        }
                      })
        val new_vcm = Map(
                ("lhs"->  DefineCTE(id,q0,qm.foldLeft(Emptyset:Query){
                            (a,current) => 
                              if (a==Emptyset) {
                                Aggregation( NaturalJoin( AddSurrogate( Derivation(Relation(id),List(("_coeff_",Num(1.0)))), "id", eq(current._1)), AddSurrogate( coalesced, "_var_", lluns(current._1))), List("id","_var_"), List("_coeff_"))
                              } else {
                                UnionAll(a,Aggregation( NaturalJoin( AddSurrogate( Derivation(Relation(id),List(("_coeff_",Num(1.0)))), "id", eq(current._1)), AddSurrogate( coalesced, "_var_", lluns(current._1))), List("id","_var_"), List("_coeff_")))
                              }
                            }
                          )),
                ("rhs"->  DefineCTE(id,q0,qm.foldLeft(Emptyset:Query){
                            (a,current) => 
                              if (a==Emptyset) {
                                Aggregation( NaturalJoin( AddSurrogate(Relation(id), "id", eq(current._1)), NaturalJoin( current._2, coalesced )), List("id","_var_"), List("_coeff_"))
                              } else {
                                UnionAll(a,Aggregation( NaturalJoin( AddSurrogate(Relation(id), "id", eq(current._1)), NaturalJoin( current._2, coalesced )), List("id","_var_"), List("_coeff_")))
                              }
                            }
                           ))
                )

        (DefineCTE(id,q0,
          UnionAll(
            Derivation(coalesced,zeros),
            Singletons(Relation(id),collapse)
            )
          ),
          qm.map{case (f,qf) =>
            (f,
              DefineCTE(id,q0,
                UnionAll(
                  AddSurrogate(Derivation(coalesced,List(("_coeff_",Num(1.0)))),"_var_",lluns(f)),
                  NaturalJoin(Project(Singletons(Relation(id),collapse),List()),Aggregation(qf,(schema.keyFields--collapse++List("_var_")).toList,List("_coeff_")))
                  )
                )
              )
            },
          if (vc._1==Emptyset) {
            (new_vc0, new_vcm)
          } else {
            (UnionAll(vc._1,new_vc0), vc._2.map{case (f,qf) => (f,UnionAll(qf,new_vcm(f)))})
          }
          )

      case NaturalJoin(q1,q2) =>
        val (q10,q1m,vc1) = queryEncoding(q1)
        val (q20,q2m,vc2) = queryEncoding(q2)
        (NaturalJoin(q10,q20),
          (q1m.map{case(f,qf) =>
            (f,NaturalJoin(qf,ProjectAway(q20,q2.schema.valFields.toList)))
          }) ++ (q2m.map{case(f,qf) =>
          (f,NaturalJoin(qf,ProjectAway(q10,q1.schema.valFields.toList)))
        }),
        variableConstraintsUnion(vc1._1,vc1._2,vc2._1,vc2._2)
        )
    }
  }

  def getEncodedView(vtable: String, eq: EncodedQuery): Map[String,Absyn.Query] = {
    val (q,qm,_) = eq
    Map(baseEncoding(vtable) ->q) ++
      qm.map{case (f,qf) => (fieldEncoding(vtable,f),qf)}
  }

  /*
  def iterateEncodedRows(iter: Iterator[Row], iters:Map[String,Iterator[Row]]): Iterator[Row] = {
    // fold over the main iterator and in each step, pull off all of the matching  term structure 
    val result_iter = iter.scanLeft((iters,(Map(),Map()):Row)){case ((iters,_),(keys_0,vals_0)) =>
      // for each value iterator
      val terms_iters = iters.map{case (f,iter_f) =>
        // get the value field i.e. the scalar term
        val b = vals_0(f)
        if (iter_f.hasNext) {
          // span the iterator matching the keys
          val (pref, suff) = iter_f.span{case (keys_f,vals_f) =>
            keys_0.forall{case (kf,kv) => kv == keys_f(kf)}}
            // traverse the prefix to extract coeffs and vars, and build the term
            val term = pref.foldLeft(b.toExpr.get){case (t,(ks,vs)) =>
            Absyn.Plus(t,Absyn.Times(vs("_coeff_").toExpr.get,Absyn.Var(ks("_var_"))))}
          // return term and updated iterator
          (f,(suff,term))
        } else {
          // return term and same empty iterator
          (f,(iter_f,b.toExpr.get))
        }
      }
      val newiters = terms_iters.map{case(f,(it,_)) => (f,it)}
      val vals = terms_iters.map{case (f,(_,tm)) => (f,Absyn.ExprV(tm))}
      (newiters,(keys_0,vals))
    }
    result_iter.drop(1).map(_._2)
  }
   */

  def bufferize[A](iter: Iterator[A]): BufferedIterator[A] = {
    new BufferedIterator[A] {
      var current: Option[A] = None
      def hasNext = current match {
        case Some(x) => true
        case None => iter.hasNext
      }
      def head = current match {
        case Some(x) =>
          x
        case None =>
          val x = iter.next
          current = Some(x)
          x
      }
      def next = current match {
        case None => iter.next
        case Some(x) =>
          current = None
          x
      }
    }
  }

  def iterateEncodedRows(iter: Iterator[Row], iters:Map[String,Iterator[Row]]): Iterator[Row] = {
    // bufferize the iterators
    val buf_iters = iters.map{case (f,iter) => (f,bufferize(iter))}
        // fold over the main iterator and in each step, pull off all of the matching  term structure
    val result_iter = iter.scanLeft((buf_iters,(Map(),Map()):Row)){case ((iters,_),(keys_0,vals_0)) =>
      // for each value iterator
      val terms_iters = iters.map{case (f,iter_f) =>
        // get the value field i.e. the scalar term
        val b = vals_0(f)
        @tailrec
        def buildTerm(t: Absyn.Expr): (BufferedIterator[Row],Absyn.Expr) = {
          if (iter_f.hasNext) {
            // span the iterator matching the keys
            val (keys_f,_) = iter_f.head
            if (keys_0.forall{case (kf,kv) => kv == keys_f(kf)}) {
              val (ks,vs) = iter_f.next
              val term = Absyn.Plus(t,Absyn.Times(vs("_coeff_").toExpr.get,Absyn.Var(ks("_var_"))))
              // return term and updated iterator
              buildTerm(term)
            } else {
              (iter_f,t)
            }
          } else {
            // return term and same empty iterator
            (iter_f,t)
          }
        }
        (f,buildTerm(b.toExpr.get))
      }
      val newiters = terms_iters.map{case(f,(it,_)) => (f,it)}
      val vals = terms_iters.map{case (f,(_,tm)) => (f,Absyn.ExprV(tm))}
      (newiters,(keys_0,vals))
    }
    // this line is black magic to discard the initial "state" i.e. the empty row passed into
    // scanLeft, and then discard the iterators.
    result_iter.drop(1).map(_._2)
  }

  def iterateEncodedTable(conn: java.sql.Connection, r: String, sch: Schema): Iterator[Row] = {
    //build iterators for each table, using orderby to sort by the keys
    val blocksize = 4096
    val (sch_0,schm,_) = encodeSchema(sch)
    val keyfields = sch_0.keyFields.toList.mkString(",")
    val r_0 = baseEncoding(r)
    def ord_sql(x: String) =
      conn.prepareStatement("SELECT * FROM "+x+" ORDER BY "+ keyfields,
        ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)
    val iter: Iterator[Row] = Database.iterateRelation(Database.iterateQuery(ord_sql(r_0),blocksize), sch_0)
    val iters: Map[String,Iterator[Row]] = schm.map{case (f,sch_f) =>
      val r_f = fieldEncoding(r,f)
      (f,Database.iterateRelation(Database.iterateQuery(ord_sql(r_f),blocksize), sch_f))
    }
  iterateEncodedRows(iter, iters)
  }

  def iterateEncodedQuery(conn: java.sql.Connection, eq:EncodedQuery, es: Map[String, Schema]): Iterator[Row] = {
    //build iterators for each table, using orderby to sort by the keys
    val blocksize = 4096
    val (q0,qm,_) = eq
    val sch_0 = Absyn.Query.tc(es,q0)
    val schm = qm.map{case (f,qf) => (f, Absyn.Query.tc(es,qf))}.toMap
    val keyfields = sch_0.keyFields.toList.mkString(",")
    val sql0 = Absyn.Query.sql(q0)
    val sqlm = qm.map{case (f,qf) => (f, Absyn.Query.sql(qf))}.toMap
    def ord_sql(x: String) =
      conn.prepareStatement("SELECT * FROM ("+x+") _ ORDER BY "+ keyfields,
        ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)
    val iter: Iterator[Row] = Database.iterateRelation(Database.iterateQuery(ord_sql(sql0),blocksize), sch_0)
    val iters: Map[String,Iterator[Row]] = schm.map{case (f,sch_f) =>
      (f,Database.iterateRelation(Database.iterateQuery(ord_sql(sqlm(f)),blocksize), sch_f))
    }
    iterateEncodedRows(iter, iters)
  }

  def iterateEncodedConstraints(conn: java.sql.Connection, eq:EncodedQuery, es: Map[String, Schema]): Iterator[Equation] = {
    //build iterators for each table, using orderby to sort by the keys
    val blocksize = 4096
    val (_,_,vc) = eq
    val (q0,qm) = vc
    val sch_0 = Absyn.Query.tc(es,q0)
    val schm = qm.map{case (f,qf) => (f, Absyn.Query.tc(es,qf))}.toMap
    val keyfields = sch_0.keyFields.toList.mkString(",")
    val sql0 = Absyn.Query.sql(q0)
    val sqlm = qm.map{case (f,qf) => (f, Absyn.Query.sql(qf))}.toMap
    def ord_sql(x: String) =
      if (keyfields=="") { conn.prepareStatement(x, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)
      } else {
        conn.prepareStatement("SELECT * FROM ("+x+") _ ORDER BY "+ keyfields,
          ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY) }
    val iter: Iterator[Row] = Database.iterateRelation(Database.iterateQuery(ord_sql(sql0),blocksize), sch_0)
    val iters: Map[String,Iterator[Row]] = schm.map{case (f,sch_f) =>
      (f,Database.iterateRelation(Database.iterateQuery(ord_sql(sqlm(f)),blocksize), sch_f))
    }
    iterateEncodedRows(iter, iters).map{ r =>
        Equation(r._2("lhs").toExpr.getOrElse(Absyn.Num(0)), r._2("rhs").toExpr.getOrElse(Absyn.Num(0)))
    }
  }
}
