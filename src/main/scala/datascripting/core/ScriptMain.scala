package datascripting.core

import scalaz.concurrent.Task
import scalaz.stream._
import argonaut._, Argonaut._
import java.util.concurrent.ExecutorService
import dispatch._
import com.ning.http.client._
import org.joda.time.DateTime
import org.joda.time.format._
import scalaz._

trait ScriptMain {
  import Options._
  import Options.Parser._

  type Input

  def argsParser: Parser[Input]

  def mainTask(input: Input): Task[Unit]

  def main(args: Array[String]): Unit = {
    argsParser.runWithArgs(args) match {
      case (_, _, Some(Options.ReadValue(input))) =>
        mainTask(input).run
      case t =>
        Options.printIssuesAndExit(t)
        System.exit(1)
    }
  }
}

