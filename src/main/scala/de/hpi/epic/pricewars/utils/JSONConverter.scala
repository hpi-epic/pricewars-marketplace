package de.hpi.epic.pricewars.utils

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, JsonFormat, RootJsonFormat}
import de.hpi.epic.pricewars.data._

object JSONConverter extends SprayJsonSupport with DefaultJsonProtocol {
  // The number after the jsonFormat function corresponds to the number of attributes contained in each class
  implicit val shippingTimeFormat: RootJsonFormat[ShippingTime] = jsonFormat2(ShippingTime.apply)
  implicit val offerFormat: RootJsonFormat[Offer] = jsonFormat10(Offer.apply)
  implicit val offerPatchFormat: RootJsonFormat[OfferPatch] = jsonFormat9(OfferPatch)
  implicit val merchantFormat: RootJsonFormat[Merchant] = jsonFormat6(Merchant.apply)
  implicit val consumerFormat: RootJsonFormat[Consumer] = jsonFormat5(Consumer.apply)
  implicit val productFormat: RootJsonFormat[Product] = jsonFormat3(Product.apply)
  implicit val buyRequestFormat: RootJsonFormat[BuyRequest] = jsonFormat3(BuyRequest)
  implicit val settingsFormat: RootJsonFormat[Settings] = jsonFormat3(Settings)
  implicit val holdingCostRateFormat: RootJsonFormat[HoldingCostRate] = jsonFormat2(HoldingCostRate)
  implicit val encryptedSignatureFormat: RootJsonFormat[EncryptedSignature] = jsonFormat1(EncryptedSignature)
  implicit def failureFormat[A :JsonFormat]: RootJsonFormat[Failure[A]] = jsonFormat2(Failure.apply[A])
}
