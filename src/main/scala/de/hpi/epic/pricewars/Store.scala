package de.hpi.epic.pricewars

import scala.collection.mutable

/**
  * Created by Jan on 01.11.2016.
  */
object Store {
  private val db = mutable.ListBuffer.empty[Offer]
  db += Offer(Some(0), "Hana", "SAP", 5, 12000000.0f, 100, false)
  def get(id: String = ""): Seq[Offer] = {
    if (id == "") db.toSeq else db.filter(_.product_id == id).toSeq
  }

  def add(offer: Offer): Int = {
    val updated = offer.copy(offer_id = Some(db.length + 1))
    db += updated
    updated.offer_id.get
  }
}
