

trait Encoding {
  type EncodedQuery
  def schemaEncoding(r: String, sch: Database.Schema) : Map[String,Database.Schema]
  def schemaEncodingWithSourceField(r: String, sch: Database.Schema) : Map[String,(String,Database.Schema)]

  def instanceSchemaEncoding(sch: Map[String, Database.Schema]): Map[String, Database.Schema]
  def insertEncodedStream(r: String, sch:Database.Schema, stream: Iterator[Database.Row]): Iterator[String]
  def queryEncoding(q: Absyn.Query): EncodedQuery
  def getEncodedView(vtable: String, eq: EncodedQuery): Map[String,Absyn.Query]
  def iterateEncodedTable(conn: java.sql.Connection, r: String, sch: Database.Schema):  Iterator[Database.Row]
  def iterateEncodedQuery(conn: java.sql.Connection, eq: EncodedQuery, es: Map[String, Database.Schema]): Iterator[Database.Row]
  def iterateEncodedConstraints(conn: java.sql.Connection, eq: EncodedQuery, es: Map[String, Database.Schema]): Iterator[Database.Equation]
  def schemaToViewDef(sourceName:String, r:String, sourceField:String, sch: Database.Schema): Map[String,String]

}

object Encoding {
  def encoder_to_use(arg: String) = arg match {
    case "partitioning" => { println("Partitioning encoding")
                    EncodePartitioning }
    case "nf2_sparsev" => { println("Non-First Normal Form encoding with sparse vectors")
                    EncodeNF2_SparseV }
    case _ => { println("A valid encoding needs to be provided instead of '"+arg+"'")
               throw Absyn.NYI
             }
  }
}
