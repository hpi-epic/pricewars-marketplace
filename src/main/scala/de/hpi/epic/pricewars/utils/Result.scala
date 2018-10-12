package de.hpi.epic.pricewars.utils

import spray.json._

import scala.util.control.NonFatal

trait Result[T] {
  self =>
  def isSuccess: Boolean

  def isFailure: Boolean = !isSuccess

  def get: T

  def map[A](f: T => A): Result[A] = self match {
    case Success(value) => Success(f(value))
    case Failure(message, code) => Failure(message, code)
  }

  def flatMap[A](f: T => Result[A]): Result[A] = self.map(f) match {
    case Success(s) => s
    case Failure(message, code) => Failure(message, code)
  }

  def toHttpResponseString()
                          (implicit successWriter: JsonWriter[T],
                           failureWriter: JsonWriter[Failure[T]]): String =
    self match {
      case Success(value) => value.toJson(successWriter).toString()
      case f: Failure[T] => f.toJson(failureWriter).toString()
    }

}

object Result {
  def apply[T](r: => T): Result[T] =
    try Success(r) catch {
      case NonFatal(e) => Failure(e.getMessage)
    }
}

case class Success[T](value: T) extends Result[T] {
  override def isSuccess: Boolean = true

  override def get: T = value
}

case class Failure[T](message: String, code: Int = 500) extends Result[T] {
  override def isSuccess: Boolean = false

  override def get: T = throw new NoSuchElementException("Failure.get")
}
