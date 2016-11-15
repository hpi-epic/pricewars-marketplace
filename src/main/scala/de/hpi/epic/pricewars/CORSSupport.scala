package de.hpi.epic.pricewars

import spray.http.AllOrigins
import spray.http.HttpHeaders._
import spray.routing._

/**
  * Created by sebastian on 15.11.16
  */

// see also https://developer.mozilla.org/en-US/docs/Web/HTTP/Access_control_CORS

// code snippet inspired by https://gist.github.com/joseraya/176821d856b43b1cfe19
trait CORSSupport {
  this: HttpService =>

  private val allowOriginHeader = `Access-Control-Allow-Origin`(AllOrigins)

  def cors[T]: Directive0 = mapRequestContext { ctx =>
    ctx.withHttpResponseHeadersMapped { headers =>
      allowOriginHeader :: headers
    }
  }
}
