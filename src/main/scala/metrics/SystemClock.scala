package metrics

object TimeModule {
  import rx.lang.scala.Observable._
  import scala.concurrent.duration._
  val systemClock = interval(1.seconds).map(t => s"systime: $t")
  val longPoll = interval(1.minutes).map(m => s"minute: $m")
}
