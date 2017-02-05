package io

import org.apache.commons.io.monitor._
import rx.lang.scala.{Observable, Observer, Subscription}
import root._

/**
  * Provides Observable that monitors directory on io modifictions.
  * Note: Creates expensive FileMonitor each time new subscriber registers.
  */
object DirectoryWatcher extends App {
  def modified(directory: String): Observable[String] = {
    Observable
      .create { observer =>
      val fileMonitor = new FileAlterationMonitor(1000) // expensive!
      val fileObserver = new FileAlterationObserver(directory)
      val fileListener = new FileAlterationListenerAdaptor {
        override def onFileChange(file: java.io.File): Unit = {
          observer.onNext(s"File ${file.getName} was changed")
        }
        override def onFileCreate(file: java.io.File): Unit = {
          observer.onNext(s"File ${file.getName} was created")
        }
        override def onFileDelete(file: java.io.File): Unit = {
          observer.onNext(s"Fiel ${file.getName} was deleted")
        }
      }

      fileObserver.addListener(fileListener)
      fileMonitor.addObserver(fileObserver)
      fileMonitor.start()
      Subscription { fileMonitor.stop }
    }
  }

  import scala.io.StdIn

  val dirName: String = StdIn.readLine()

  modified(dirName)
    .subscribe(new Observer[String] {
      override def onNext(m: String) = log(m)
      override def onError(e: Throwable) = log(e.getMessage)
      override def onCompleted() = log("Directory Watcher is closed")
    })

}

/**
  * Provides Observable that monitors directory on io modifictions.
  * Note: Creates FileMonitor once.
  */
object LightDirectoryWatcher extends App {
  val fileMonitor = new FileAlterationMonitor(1000)
  fileMonitor.start

  def modified(directory: String): Observable[String] = {
    val fileObserver = new FileAlterationObserver(directory)
    fileMonitor.addObserver(fileObserver)
    Observable.create { observer =>
      val fileListener = new FileAlterationListenerAdaptor {
                override def onFileChange(file: java.io.File): Unit = {
          observer.onNext(s"File ${file.getName} was changed")
        }
        override def onFileCreate(file: java.io.File): Unit = {
          observer.onNext(s"File ${file.getName} was created")
        }
        override def onFileDelete(file: java.io.File): Unit = {
          observer.onNext(s"Fiel ${file.getName} was deleted")
        }
      }
      fileObserver.addListener(fileListener)
      Subscription { fileObserver.removeListener(fileListener) }
    }
  }

  import scala.io.StdIn

  val dirName: String = StdIn.readLine()

  modified(dirName)
    .subscribe(new Observer[String] {
      override def onNext(m: String) = log(m)
      override def onError(e: Throwable) = log(e.getMessage)
      override def onCompleted() = log("Directory Watcher is closed")
    })

}
