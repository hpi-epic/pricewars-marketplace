package de.hpi.epic.pricewars

import scalikejdbc._

/**
  * Created by Jan on 01.11.2016.
  */
case class Offer ( offer_id: Option[Long],
                   product_id: Long,
                   merchant_id: Long,
                   amount: Int,
                   price: BigDecimal,
                   shipping_time: ShippingTime,
                   prime: Boolean = false )

object Offer extends SQLSyntaxSupport[Offer] {
  override val tableName = "offers"
  def apply(rs: WrappedResultSet) = new Offer(
    Some(rs.long("offer_id")), rs.long("product_id"), rs.long("merchant_id"), rs.int("amount"),
    rs.bigDecimal("price"), ShippingTime(rs), rs.boolean("prime"))
}

case class OfferPatch (product_id: Option[Long] = None,
                       merchant_id: Option[Long] = None,
                       amount: Option[Int] = None,
                       price: Option[BigDecimal] = None,
                       shipping_time: Option[ShippingTime] = None,
                       prime: Option[Boolean] = None )

case class ShippingTime ( standard: Int,
                          prime: Option[Int] = None )

object ShippingTime extends SQLSyntaxSupport[ShippingTime] {
  override val tableName = "offers"
  def apply(rs: WrappedResultSet) = new ShippingTime(rs.int("shipping_time_standard"), rs.intOpt("shipping_time_prime"))
}

case class BuyRequest ( price: BigDecimal,
                        amount: Int,
                        customer_id: Option[Long],
                        prime: Option[Boolean] )
