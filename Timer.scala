object Timer {
  
  def timeIt[A](e: => A): (A,Long) = {
    val startTime = System.nanoTime()
    val x = e
    val endTime = System.nanoTime()
    (x,endTime-startTime)
  }

}
