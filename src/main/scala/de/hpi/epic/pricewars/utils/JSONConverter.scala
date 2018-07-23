package de.hpi.epic.pricewars.utils

import de.hpi.epic.pricewars.data._
import spray.httpx.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, JsonFormat, RootJsonFormat}

object JSONConverter extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val shippingTimeFormat = jsonFormat2(ShippingTime.apply)
  implicit val offerFormat = jsonFormat10(Offer.apply)
  implicit val offerPatchFormat = jsonFormat9(OfferPatch)
  implicit val merchantFormat = jsonFormat5(Merchant.apply)
  implicit val consumerFormat = jsonFormat5(Consumer.apply)
  implicit val productFormat = jsonFormat3(Product.apply)
  implicit val buyRequestFormat = jsonFormat3(BuyRequest)
  implicit val settingsFormat = jsonFormat3(Settings)
  implicit val holdingCostRateFormat: RootJsonFormat[HoldingCostRate] = jsonFormat2(HoldingCostRate)
  implicit val encryptedSignatureFormat = jsonFormat1(EncryptedSignature)
  implicit def failureFormat[A :JsonFormat] = jsonFormat2(Failure.apply[A])
}
