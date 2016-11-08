package de.hpi.epic.pricewars

/**
  * Created by Jan on 02.11.2016.
  */
trait Result[T] {
  def isSuccess: Boolean
  def isFailure: Boolean = !isSuccess
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
