package de.hpi.epic.pricewars

/**
  * Created by Jan on 01.11.2016.
  */
case class Offer ( offer_id: Option[Long],
                   product_id: String,
                   merchant_id: String,
                   amount: Int,
                   price: BigDecimal,
                   shipping_time: ShippingTime,
                   prime: Boolean = false )

case class ShippingTime ( standard: Int,
                          prime: Option[Int] = None )