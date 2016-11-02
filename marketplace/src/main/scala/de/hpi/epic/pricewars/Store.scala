package de.hpi.epic.pricewars

import scala.collection.mutable

/**
  * Created by Jan on 01.11.2016.
  */
object Store {
  private val db = mutable.ListBuffer.empty[Offer]
  db += Offer(Some(0), "Hana", "SAP", 5, 12000000.0f, 100, false)

  def get(id: Long = -1): Seq[Offer] = {
    val res = if (id == -1) db.toSeq else db.filter(_.offer_id.exists(_ == id)).toSeq
    println(res)
    res
  }

  def add(offer: Offer): Long = {
    val updated = offer.copy(offer_id = Some(db.length + 1))
    db += updated
    updated.offer_id.get
  }

  def update(id: Long, offer: Offer): Boolean = {
    db.find(_.offer_id.exists(_ == id)) match {
      case Some(db_offer) =>
        println(db_offer)
        db -= db_offer
        val updated = offer.copy(offer_id = db_offer.offer_id)
        db += updated
        println(updated)
        true
      case None => false
    }
  }

  //TODO: Use Try[Something] instead of Boolean
  def remove(id: Long): Boolean = {
    db.find(_.offer_id.exists(_ == id)) match {
      case Some(offer) =>
        println(offer)
        db -= offer
        true
      case None => false
    }
  }
}
