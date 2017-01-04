package de.hpi.epic.pricewars

import scalikejdbc._

case class Consumer(api_endpoint_url: String,
                    consumer_name: String,
                    description: String,
                    consumer_id: Option[String],
                    consumer_token: Option[String])

object Consumer extends SQLSyntaxSupport[Consumer] {
  override val tableName = "consumers"
  def apply(rs: WrappedResultSet) = new Consumer(
    rs.string("api_endpoint_url"), rs.string("consumer_name"), rs.string("description"), rs.stringOpt("consumer_id"), rs.stringOpt("consumer_token"))
}
