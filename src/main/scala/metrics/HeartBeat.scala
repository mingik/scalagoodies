package metrics

import scala.io.Source
import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import rx.lang.scala.{Observable, Observer, Subscription}
import root._

object HeartBeat extends App {
  def fetchService(url: String): Future[String] = Future {
    blocking {
      Source.fromURL(url).getLines.mkString
    }
  }

  def fetchServiceObservable = (url: String) => Observable.from(fetchService(url))

  def heartbeat = (url: String) => 
  Observable
    .interval(1 seconds)
    .take(5)
    .map { beat => fetchServiceObservable(url).map(txt => s"$beat) $txt") }

  import scala.io.StdIn

  val serviceUrl: String = StdIn.readLine()

  heartbeat(serviceUrl)
    .concat
    .retry(10) // number of reconnection attempts
    .subscribe(log _)

}
