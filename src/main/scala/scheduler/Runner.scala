package scheduler

import java.util.concurrent.ThreadPoolExecutor.AbortPolicy
import java.util.concurrent._

import scala.concurrent.Promise
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._
import scala.language.implicitConversions
import scala.util._
import scala.concurrent.ExecutionContext.Implicits.global

object Scheduler {
  private val defaultHandler: RejectedExecutionHandler = new AbortPolicy
  private val corePoolSize: Int = 1 // singlethreaded for now
  private val threadFactory: ThreadFactory = Executors.defaultThreadFactory
  private val underlying: ScheduledExecutorService = new ScheduledThreadPoolExecutor(corePoolSize, threadFactory, defaultHandler)

  class ScheduledEvent[T] {
    var cancel: (Boolean) => Boolean = x => false
    var completionHook: (Try[T] => Unit) = x => Unit
    def setCancelMethod(c: (Boolean) ⇒ Boolean): Unit = {cancel = c}
    def submitCompletionHook(hook: Try[T] => Unit) = {
      completionHook = hook
    }
    def submitCancelation: Unit = {
      cancel(true)
    }
  }

  def scheduleOnce[T](when: FiniteDuration)(tryOperation: ⇒ Try[T]): ScheduledEvent[T] = {
    val scheduledEvent = new ScheduledEvent[T]
    val scheduledFuture: ScheduledFuture[_] = underlying.schedule(
      new Runnable {
        override def run() = {
          // log start
          scheduledEvent.completionHook(tryOperation)
          // log finish IllegalStateException!
        }
      },
      when.length,
      when.unit)
    scheduledEvent.setCancelMethod(scheduledFuture.cancel)
    scheduledEvent
  }

  class PeriodicEvent[T] {
    def submitCompletionHook(hook: Try[T] => Unit) = {
      completionHook = hook
    }
    def submitCancelation: Unit = {
      cancel(true)
    }
    
    var cancel: (Boolean) => Boolean = x => false
    var completionHook: (Try[T] => Unit) = x => Unit
    def setCancelMethod(c: (Boolean) ⇒ Boolean): Unit = {cancel = c}
  }

  def schedulePeriodically[T](when: FiniteDuration, period: FiniteDuration)(tryOperation: ⇒ Try[T]) = {
    val periodicEvent = new PeriodicEvent[T]
    val scheduledFuture: ScheduledFuture[_] = underlying.scheduleAtFixedRate(
      new Runnable {
        override def run() = {
          // log start
          periodicEvent.completionHook(tryOperation)
          // log finish
        }
      },
      when.toSeconds,
      period.toSeconds,
      SECONDS)
    periodicEvent.setCancelMethod(scheduledFuture.cancel)
    periodicEvent
  }

  def scheduleSequentially[T](when: FiniteDuration, delay: FiniteDuration = FiniteDuration(0, SECONDS))(tryOperation: ⇒ Try[T]) = {
    val periodicEvent = new PeriodicEvent[T]
    val scheduledFuture: ScheduledFuture[_] = underlying.scheduleWithFixedDelay(
      new Runnable {
        override def run() = {
          // log start
          periodicEvent.completionHook(tryOperation)
          // log finish
        }
      },
      when.toSeconds,
      delay.toSeconds,
      SECONDS)
    periodicEvent.setCancelMethod(scheduledFuture.cancel)
    periodicEvent
  }

  def shutdown(): Unit = {
    try {
      underlying.shutdown()
      underlying.awaitTermination(5, SECONDS)
    } catch {
      case e: InterruptedException => System.err.println("tasks interrupted")
    } finally {
      underlying.shutdownNow()
      println("Should be down")
    }
  }
}

object App extends App {

  import Scheduler._

  def uuid = java.util.UUID.randomUUID.toString

  val r = scala.util.Random

  def act1(): Try[String] = {
    println(s"On thread ${Thread.currentThread.getName}")
    if (r.nextInt(10) % 2 == 1)
      Try(uuid)
    else
      Try(r.nextInt(10).toString)
  }

  def act2(s: String): Try[Int] = {
    println(s"On thread ${Thread.currentThread.getName}")
    Try(s.toInt)
  }

  def act() = for {
    s <- act1()
    i <- act2(s)
  } yield i


  val scheduler = Scheduler

  //val se: ScheduledEvent[String] = scheduler.scheduleOnce(FiniteDuration(5, SECONDS))(act1)

  //val se = scheduler.scheduleOnce(FiniteDuration(3, SECONDS))(act)
  //se.promise.future onSuccess { case s => println(s"result: $s") }
  //se.promise.future onFailure { case s => println(s"failure: $s") }

  val pe = scheduler.schedulePeriodically(FiniteDuration(1, SECONDS), FiniteDuration(2, SECONDS))(act)
  pe.submitCompletionHook({ x: Try[Int] =>
    println(s"on thread: ${Thread.currentThread.getName}, final value: ${x}")
  })


  println("going to sleep")
  Thread.sleep(10000)

  println("attempting to cancel")
  pe.submitCancelation

  println("going to sleep, no events should be produced")
  Thread.sleep(10000)

  println("shutting down sheduler")
  scheduler.shutdown

  println("going to sleep")
  Thread.sleep(1000)

  println("Done")
}
