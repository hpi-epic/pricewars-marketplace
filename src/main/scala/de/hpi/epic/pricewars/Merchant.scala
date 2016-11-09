package de.hpi.epic.pricewars

import scalikejdbc._

/**
  * Created by Jan on 02.11.2016.
  */
case class Merchant( api_endpoint_url: String,
                     merchant_name: String,
                     algorithm_name: String,
                     merchant_id: Option[String] )

object Merchant extends SQLSyntaxSupport[Merchant] {
  override val tableName = "merchants"
  def apply(rs: WrappedResultSet) = new Merchant(
    rs.string("api_endpoint_url"), rs.string("merchant_name"), rs.string("algorithm_name"), Some(rs.long("merchant_id").toString))
}
