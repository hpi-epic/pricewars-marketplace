package de.hpi.epic.pricewars

import scala.util.control.NonFatal

trait Result[T] {
  self =>
  def isSuccess: Boolean
  def isFailure: Boolean = !isSuccess
  def get: T

  def map[A](f: T => A): Result[A] = self match {
    case Success(value) => Success(f(value))
    case Failure(msg, code) => Failure(msg, code)
  }

  def flatMap[A](f: T => Result[A]): Result[A] = self.map(f) match {
    case Success(s) => s
    case Failure(msg, code) => Failure(msg, code)
  }
}

object Result {
  /*def apply[T](res: T): Result[T] = {
    Success(res)
  }*/

  def apply[T](r: => T): Result[T] =
    try Success(r) catch {
      case NonFatal(e) => Failure(e.getMessage, 500)
    }

}

case class Success[T](value: T) extends Result[T] {
  override def isSuccess: Boolean = true
  override def get: T = value
}

case class Failure[T](msg: String, code: Int = 500) extends Result[T] {
  override def isSuccess: Boolean = false
  override def get: T = throw new NoSuchElementException("Failure.get")
}
