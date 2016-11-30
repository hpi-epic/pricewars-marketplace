package de.hpi.epic.pricewars

import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.util.Timeout
import akka.pattern.ask
import akka.io.IO

import spray.can.Http
import spray.http._
import HttpMethods._


/**
  * Created by sebastian on 08.11.16
  */
object MerchantConnector {

  implicit val system: ActorSystem = ActorSystem()
  implicit val timeout: Timeout = Timeout(15.seconds)
  import system.dispatcher // implicit execution context

  def notifyMerchant(merchant: Merchant, offer_id: Long, amount: Int, price: BigDecimal) = {
    val request = (IO(Http) ? HttpRequest(POST,
      Uri(merchant.api_endpoint_url + "/sold"),
      entity = s"""{"offer_id": $offer_id, "amount": $amount, "price": $price}""",
      headers = List[HttpHeader](HttpHeaders.`Content-Type`(ContentTypes.`application/json`))
    )).mapTo[HttpResponse]
    request.onFailure{ case _ =>
      println("kill merchant: " + merchant.algorithm_name)
      DatabaseStore.deleteMerchant(merchant.merchant_id.get)
    }
    request.onSuccess{ case _ => println("ok")}
  }
}
