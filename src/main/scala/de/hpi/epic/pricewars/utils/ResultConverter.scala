package de.hpi.epic.pricewars.utils

import spray.http.StatusCode
import spray.httpx.marshalling.ToResponseMarshallable
import spray.json._

object ResultConverter {
  implicit def toToResponseMarshallable[T](res: Result[T])
                                          (implicit successWriter: JsonWriter[T],
                                           failureWriter: JsonWriter[Failure[T]]): ToResponseMarshallable =
    res match {
      case Success(value) => value.toJson(successWriter).toString()
      case f: Failure[T] => StatusCode.int2StatusCode(f.code) -> f.toJson(failureWriter).toString()
    }

  implicit class ResultToResponseMarshallable[T](res: Result[T]) (implicit successWriter: JsonWriter[T],
                                                                  failureWriter: JsonWriter[Failure[T]]) {
    def successHttpCode(statusCode: StatusCode): ToResponseMarshallable =
      res match {
        case Success(value) => statusCode -> value.toJson(successWriter).toString()
        case f: Failure[T] => StatusCode.int2StatusCode(f.code) -> f.toJson(failureWriter).toString()
      }
  }
}
