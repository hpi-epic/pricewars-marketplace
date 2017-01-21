package de.hpi.epic.pricewars.data

import scalikejdbc._

/**
  * Created by Jan on 17.01.2017.
  */
object ShippingTime extends SQLSyntaxSupport[ShippingTime] {
  override val tableName = "offers"
  def apply(rs: WrappedResultSet) = new ShippingTime(rs.int("shipping_time_standard"), rs.intOpt("shipping_time_prime"))
}

case class ShippingTime ( standard: Int,
                          prime: Option[Int] = None )