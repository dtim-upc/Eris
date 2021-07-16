import scala.util.Random

// Creates random data given a scaling factor

object GenerateData {

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


  def main(args:Array[String]) : Unit = {
    val rand = new Random()

    Class.forName("org.postgresql.Driver")

    val hostname = args(0)
    val dbname = args(1)
    val username = args(2)
    val password = args(3)
    val n = args(4).toInt

    val c_mean = rand.nextDouble()*n
    val c_stdev = rand.nextGaussian()
    val d_mean = rand.nextDouble()*n
    val d_stdev = rand.nextGaussian()
    val e_mean = rand.nextDouble()*n
    val e_stdev = rand.nextGaussian()
    val f_mean = rand.nextDouble()*n
    val f_stdev = rand.nextGaussian()

    def gaussian(mean: Double, stdev: Double) = {
      mean + stdev * rand.nextGaussian()
    }

    val connector = Connector(hostname, dbname, username, password)


    val conn = connector.getConnection()
    conn.setAutoCommit(false)
    val updQ = UpdQueue(conn,1024)

    val st = conn.createStatement()
    st.executeUpdate("TRUNCATE r")
    st.executeUpdate("TRUNCATE s")

    var bi = 0
    for (i <- Range(0,n)) {
      bi = bi + rand.nextInt(2) + 1
      val b = bi.toString
      val e = gaussian(e_mean, e_stdev)
      val f = gaussian(f_mean, f_stdev)
      updQ.put(Database.insertRowCommand("s",(Map("b" -> b),Map("e" -> Absyn.FloatV(e), "f" -> Absyn.FloatV(f)))))
      var ai = 0
      for (j <- Range(0,rand.nextInt(java.lang.Math.ceil(java.lang.Math.sqrt(n)).toInt))) {
        ai = ai + rand.nextInt(2) + 1
        val a = ai.toString
        val c = gaussian(c_mean, c_stdev)
        val d = gaussian(d_mean, d_stdev)
        updQ.put(Database.insertRowCommand("r",(Map("a" -> a, "b" -> b),Map("c" -> Absyn.FloatV(c), "d" -> Absyn.FloatV(d)))))
      }
     if (i % 100 == 0) { println(i) }
    }
    updQ.close()
  }
}
