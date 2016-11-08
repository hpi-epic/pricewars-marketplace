package de.hpi.epic.pricewars

/**
  * Created by Jan on 02.11.2016.
  */
trait Result[T] {
  self =>
  def isSuccess: Boolean
  def isFailure: Boolean = !isSuccess

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
  def apply[T](res: T): Result[T] = {
    Success(res)
  }
}

case class Success[T](value: T) extends Result[T] {
  override def isSuccess: Boolean = true
}

case class Failure[T](msg: String, code: Int = 500) extends Result[T] {
  override def isSuccess: Boolean = false
}
