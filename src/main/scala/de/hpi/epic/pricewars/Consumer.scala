package de.hpi.epic.pricewars

import scalikejdbc._

case class Consumer(api_endpoint_url: String,
                    consumer_name: String,
                    description: String,
                    consumer_id: Option[Long] )

object Consumer extends SQLSyntaxSupport[Consumer] {
  override val tableName = "consumers"
  def apply(rs: WrappedResultSet) = new Consumer(
    rs.string("api_endpoint_url"), rs.string("consumer_name"), rs.string("description"), Some(rs.long("consumer_id")))
}
