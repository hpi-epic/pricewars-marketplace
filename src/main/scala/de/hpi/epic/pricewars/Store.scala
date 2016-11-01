package de.hpi.epic.pricewars

import scala.collection.mutable

/**
  * Created by Jan on 01.11.2016.
  */
object Store {
  private val db = mutable.ListBuffer.empty[Offer]
  db += Offer(Some(0), "Hana", "SAP", 5, 12000000.0f, 100, false)
  def get(id: Long = -1): Seq[Offer] = {
    if (id == -1) db.toSeq else db.filter(_.offer_id.exists(_ == id)).toSeq
  }

  def add(offer: Offer): Long = {
    val updated = offer.copy(offer_id = Some(db.length + 1))
    db += updated
    updated.offer_id.get
  }
}
