package de.hpi.epic.pricewars

import scala.collection.mutable

/**
  * Created by Jan on 01.11.2016.
  */
object Store {
  private var counter = 0;
  private val db = mutable.ListBuffer.empty[Offer]

  def get: Result[Seq[Offer]] = {
    Success(db)
  }

  def get(id: Long): Result[Offer] = {
    db.find(_.offer_id.exists(_ == id)) match {
      case Some(offer) => Success(offer)
      case None => Failure(s"No offer with id $id found", 404)
    }
  }

  def add(offer: Offer): Result[Offer] = {
    counter = counter + 1
    val updated = offer.copy(offer_id = Some(counter))
    db += updated
    Success(updated)
  }

  def update(id: Long, offer: Offer): Result[Offer] = {
    db.find(_.offer_id.exists(_ == id)) match {
      case Some(db_offer) =>
        db -= db_offer
        val updated = offer.copy(offer_id = db_offer.offer_id)
        db += updated
        Success(updated)
      case None => Failure(s"No offer with id $id found", 404)
    }
  }

  //TODO: Use Try[Something] instead of Boolean
  def remove(id: Long): Result[Unit] = {
    db.find(_.offer_id.exists(_ == id)) match {
      case Some(offer) =>
        db -= offer
        Success()
      case None => Failure(s"No offer with id $id found", 404)
    }
  }

  def clear() = {
    db.clear()
  }
}
