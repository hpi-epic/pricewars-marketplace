package de.hpi.epic.pricewars

import spray.httpx.SprayJsonSupport
import spray.json._

/**
  * Created by Jan on 01.11.2016.
  */
case class Offer ( offer_id: Option[Int],
                   product_id: String,
                   seller_id: String,
                   amount: Int,
                   price: Float,
                   shipping_time: Int,
                   prime: Boolean )

object OfferJsonProtocol extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val offerFormat = jsonFormat7(Offer)
}