package de.hpi.epic.pricewars

import scalikejdbc._

case class Offer ( offer_id: Option[Long],
                   uid: Long,
                   product_id: Long,
                   quality: Long,
                   merchant_id: String,
                   amount: Int,
                   price: BigDecimal,
                   shipping_time: ShippingTime,
                   prime: Boolean = false,
                   signature: Option[String])

object Offer extends SQLSyntaxSupport[Offer] {
  override val tableName = "offers"
  def apply(rs: WrappedResultSet) = new Offer(
    rs.longOpt("offer_id"), rs.long("uid"), rs.long("product_id"), rs.long("quality"), rs.string("merchant_id"), rs.int("amount"),
    rs.bigDecimal("price"), ShippingTime(rs), rs.boolean("prime"), None)
}

case class OfferPatch (uid: Option[Long] = None,
                       product_id: Option[Long] = None,
                       quality: Option[Long] = None,
                       merchant_id: Option[String] = None,
                       amount: Option[Int] = None,
                       price: Option[BigDecimal] = None,
                       shipping_time: Option[ShippingTime] = None,
                       prime: Option[Boolean] = None,
                       signature: Option[String])

case class ShippingTime ( standard: Int,
                          prime: Option[Int] = None )

object ShippingTime extends SQLSyntaxSupport[ShippingTime] {
  override val tableName = "offers"
  def apply(rs: WrappedResultSet) = new ShippingTime(rs.int("shipping_time_standard"), rs.intOpt("shipping_time_prime"))
}

case class BuyRequest ( price: BigDecimal,
                        amount: Int,
                        consumer_id: Option[Long],
                        prime: Option[Boolean] )
