object Debug {
  var level = 0
  def println(l: Int, s: String) = {
    if (l <= level) {
      System.out.println(s)
    }
  }
}
