package de.hpi.epic.pricewars

import spray.http.{AllOrigins, HttpMethod, HttpMethods, HttpResponse}
import spray.http.HttpHeaders._
import spray.routing._


// see also https://developer.mozilla.org/en-US/docs/Web/HTTP/Access_control_CORS

// code snippet inspired by https://gist.github.com/joseraya/176821d856b43b1cfe19
trait CORSSupport {
  this: HttpService =>
  private val allowOriginHeader = `Access-Control-Allow-Origin`(AllOrigins)
  private val allowMethodHeader = `Access-Control-Allow-Methods`(HttpMethods.GET, HttpMethods.POST, HttpMethods.OPTIONS, HttpMethods.DELETE,
    HttpMethods.CONNECT, HttpMethods.HEAD, HttpMethods.PATCH, HttpMethods.PUT, HttpMethods.TRACE)
  private val allowHeadersHeader = `Access-Control-Allow-Headers`("Origin, X-Requested-With, Content-Type, Accept, Accept-Encoding, Accept-Language, Host," +
    " Referer, User-Agent, Overwrite, Destination, Depth, X-Token, X-File-Size, If-Modified-Since, X-File-Name, Cache-Control")

  def cors[T]: Directive0 = mapRequestContext { ctx =>
    ctx.withRouteResponseHandling({
      //It is an option request for a resource that responds to some other method
      case Rejected(x) if ctx.request.method.equals(HttpMethods.OPTIONS) && x.exists(_.isInstanceOf[MethodRejection]) => {
        val allowedMethods: List[HttpMethod] = x.filter(_.isInstanceOf[MethodRejection]).map(rejection => {
          rejection.asInstanceOf[MethodRejection].supported
        })
        ctx.complete(HttpResponse().withHeaders(
          `Access-Control-Allow-Methods`(HttpMethods.OPTIONS, allowedMethods: _*) ::
            List(allowOriginHeader, allowHeadersHeader)
        ))
      }
    }).withHttpResponseHeadersMapped { headers =>
      allowOriginHeader :: allowMethodHeader :: allowHeadersHeader :: headers
    }
  }
}
