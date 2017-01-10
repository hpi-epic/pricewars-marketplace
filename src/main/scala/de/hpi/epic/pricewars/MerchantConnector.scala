package de.hpi.epic.pricewars

import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.util.Timeout
import akka.pattern.ask
import akka.io.IO

import spray.can.Http
import spray.http._
import HttpMethods._

object MerchantConnector {

  implicit val system: ActorSystem = ActorSystem()
  implicit val timeout: Timeout = Timeout(15.seconds)
  import system.dispatcher // implicit execution context

  def notifyMerchant(merchant: Merchant, offer_id: Long, amount: Int, price: BigDecimal) = {
    val json = s"""{"offer_id": $offer_id, "amount": $amount, "price": $price}"""
    val request = (IO(Http) ? HttpRequest(POST,
      Uri(merchant.api_endpoint_url + "/sold"),
      entity = HttpEntity(MediaTypes.`application/json`, json)
    )).mapTo[HttpResponse]
    request.onFailure{ case _ =>
      println("kill merchant: " + merchant.algorithm_name)
      DatabaseStore.deleteMerchant(merchant.merchant_id.get, merchant.merchant_token.get)
    }
    request.onSuccess{ case HttpResponse(status, _, _, _) => {
      if (status == StatusCodes.PreconditionRequired) {
        println("merchant requested to be deleted: " + merchant.algorithm_name)
        DatabaseStore.deleteMerchant(merchant.merchant_id.get, merchant.merchant_token.get)
      } else {
        println("ok")
      }
    }}
  }
}
