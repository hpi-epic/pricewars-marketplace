package de.hpi.epic.pricewars

import scala.collection.mutable

/**
  * Created by Jan on 01.11.2016.
  */

object OfferStore extends Store[Offer] {
  override def setKey(key: Long, value: Offer): Offer = value.copy(offer_id = Some(key))
  override def getKey(value: Offer): Long = value.offer_id.getOrElse(-2)
}

object MerchantStore extends Store[Merchant] {
  override def setKey(key: Long, value: Merchant): Merchant = value.copy(merchant_id = Some(key.toString))
  override def getKey(value: Merchant): Long = value.merchant_id.map(_.toLong).getOrElse(-2)
}

trait Store[T] {
  private var counter = 0;
  private val db = mutable.ListBuffer.empty[T]
  def setKey(key: Long, value: T): T
  def getKey(value: T): Long

  def get: Result[Seq[T]] = Success(db)
  def get(key: Long): Result[T] = db.find(getKey(_) == key) match {
    case Some(value) => Success(value)
    case None => Failure(s"No object with key $key found", 404)
  }

  def add(value: T): Result[T] = {
    counter = counter + 1
    val updated = setKey(counter, value)
    db += updated
    Success(updated)
  }

  def update(key: Long, value: T): Result[T] = {
    db.find(getKey(_) == key) match {
      case Some(db_value) =>
        db -= db_value
        val updated = setKey(key, value)
        db += updated
        Success(updated)
      case None => Failure(s"No object with key $key found", 404)
    }
  }

  def remove(key: Long): Result[Unit] = {
    db.find(getKey(_) == key) match {
      case Some(value) =>
        db -= value
        Success()
      case None => Failure(s"No object with id $key found", 404)
    }
  }

  def clear() = {
    db.clear()
  }
}
