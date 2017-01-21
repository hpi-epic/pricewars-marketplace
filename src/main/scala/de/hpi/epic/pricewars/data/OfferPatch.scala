package de.hpi.epic.pricewars.data

/**
  * Created by Jan on 17.01.2017.
  */
case class OfferPatch(uid: Option[Long] = None,
                      product_id: Option[Long] = None,
                      quality: Option[Long] = None,
                      merchant_id: Option[String] = None,
                      amount: Option[Int] = None,
                      price: Option[BigDecimal] = None,
                      shipping_time: Option[ShippingTime] = None,
                      prime: Option[Boolean] = None,
                      signature: Option[String])
