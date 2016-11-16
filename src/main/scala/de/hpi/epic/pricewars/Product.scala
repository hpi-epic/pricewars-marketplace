package de.hpi.epic.pricewars

import scalikejdbc._

/**
  * Created by sebastian on 15.11.16
  */
case class Product ( product_id: Option[Long],
                     name: String,
                     genre: String )

object Product extends SQLSyntaxSupport[Product] {
  override val tableName = "products"
  def apply(rs: WrappedResultSet) = new Product(
    Some(rs.long("product_id")), rs.string("name"), rs.string("genre"))
}
