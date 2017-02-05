package object root {
  def log(msg: String) {
    println(s"${Thread.currentThread.getName}: $msg")
  }
}
