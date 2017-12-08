package de.hpi.epic.pricewars.data

import scalikejdbc._

case class Merchant( api_endpoint_url: String,
                     merchant_name: String,
                     algorithm_name: String,
                     merchant_id: Option[String],
                     merchant_token: Option[String])

object Merchant extends SQLSyntaxSupport[Merchant] {
  override val tableName = "merchants"
  def apply(rs: WrappedResultSet) = new Merchant(
    rs.string("api_endpoint_url"),
    rs.string("merchant_name"),
    rs.string("algorithm_name"),
    rs.stringOpt("merchant_id"),
    rs.stringOpt("merchant_token"))
}
