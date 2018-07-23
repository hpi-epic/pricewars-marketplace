package de.hpi.epic.pricewars.data

case class Signature (
  product_uid: Long,
  max_amount: Int,
  merchant_id: String) {

  def isValid(product_uid: Long, amount: Int, merchant_id: String): Boolean = {
    return (this.product_uid == product_uid
      && amount <= max_amount
      && this.merchant_id == merchant_id)
  }
}
