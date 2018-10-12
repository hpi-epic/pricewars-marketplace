package de.hpi.epic.pricewars.utils

import akka.http.scaladsl.model.StatusCode
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import spray.json._

import scala.language.implicitConversions

object ResultConverter {
  implicit def toToResponseMarshallable[T](res: Result[T])
                                          (implicit successWriter: JsonWriter[T],
                                           failureWriter: JsonWriter[Failure[T]]): ToResponseMarshallable =
    res match {
      case s: Success[T] => s.value.toJson(successWriter).toString()
      case f: Failure[T] => StatusCode.int2StatusCode(f.code) -> f.toJson(failureWriter).toString()
    }

  implicit class ResultToResponseMarshallable[T](res: Result[T])
                                                (implicit successWriter: JsonWriter[T],
                                                 failureWriter: JsonWriter[Failure[T]]) {
    def successHttpCode(statusCode: StatusCode): ToResponseMarshallable =
      res match {
        // FIXME: Success Code is ignored
        case Success(value, _) => statusCode -> value.toJson(successWriter).toString()
        case f: Failure[T] => StatusCode.int2StatusCode(f.code) -> f.toJson(failureWriter).toString()
      }
  }

}
