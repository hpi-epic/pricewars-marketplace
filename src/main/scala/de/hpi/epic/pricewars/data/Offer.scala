package de.hpi.epic.pricewars.data

import scalikejdbc._

case class Offer ( offer_id: Option[Long],
                   uid: Long,
                   product_id: Long,
                   quality: Long,
                   merchant_id: Option[String],
                   amount: Int,
                   price: BigDecimal,
                   shipping_time: ShippingTime,
                   prime: Boolean = false,
                   signature: Option[String])

object Offer extends SQLSyntaxSupport[Offer] {
  override val tableName = "offers"
  def apply(rs: WrappedResultSet) = new Offer(
    rs.longOpt("offer_id"), rs.long("uid"), rs.long("product_id"), rs.long("quality"), rs.stringOpt("merchant_id"), rs.int("amount"),
    rs.bigDecimal("price"), ShippingTime(rs), rs.boolean("prime"), None)
}
