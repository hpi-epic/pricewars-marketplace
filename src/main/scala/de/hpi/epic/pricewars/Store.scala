package de.hpi.epic.pricewars

import scala.collection.mutable
import scalikejdbc._
import com.typesafe.config.ConfigFactory

import scala.util.Try

/**
  * Created by Jan on 01.11.2016.
  */

object DatabaseStore {
  val config = ConfigFactory.load()
  val username = config.getString("marketplace.database.username")
  val password = config.getString("marketplace.database.password")
  val host = config.getString("marketplace.database.host")
  val port = config.getInt("marketplace.database.port")
  val databaseName = config.getString("marketplace.database.databaseName")
  Class.forName("org.postgresql.Driver")
  ConnectionPool.singleton(s"jdbc:postgresql://$host:$port/$databaseName", username, password)
  implicit val session = AutoSession

  /*
  offer_id: Option[Long],
                   product_id: String,
                   merchant_id: String,
                   amount: Int,
                   price: BigDecimal,
                   shipping_time: ShippingTime,
                   prime: Boolean = false
   */

  def setup(): Unit = {
    println("run setup")
    DB localTx { implicit session =>
      sql"""CREATE TABLE IF NOT EXISTS offers (
        offer_id SERIAL NOT NULL PRIMARY KEY,
        product_id VARCHAR(25) NOT NULL,
        merchant_id VARCHAR(25) NOT NULL,
        amount INTEGER not null CHECK (amount >= 0),
        price NUMERIC(11,2) not null,
        shipping_time_standard INTEGER NOT NULL,
        shipping_time_prime INTEGER,
        prime BOOLEAN
      )"""
    }
  }

  def addOffer(offer: Offer): Offer = {
    val id = DB localTx { implicit session =>
      sql"""INSERT INTO offers VALUES (
          DEFAULT,
          ${offer.product_id},
          ${offer.merchant_id},
          ${offer.amount},
          ${offer.price},
          ${offer.shipping_time.standard},
          ${offer.shipping_time.prime},
          ${offer.prime}
      )""".updateAndReturnGeneratedKey.apply()
    }
    offer.copy(offer_id = Some(id))
  }

  def deleteOffer(offer_id: Long): Result[Unit] = {
    val res = Try(DB readOnly { implicit session =>
      sql"DELETE FROM offers WHERE offer_id = $offer_id".executeUpdate()
    })
    res match {
      case scala.util.Success(v) if v == 1 => Success((): Unit)
      case scala.util.Success(v) if v != 1 => Failure(s"No product with id $offer_id", 404)
      case scala.util.Failure(e) => Failure(e.getMessage, 500)
    }
  }

  def getOffers: Result[Seq[Offer]] = {
    val res = Try(DB readOnly { implicit session =>
      sql"SELECT offer_id, product_id, merchant_id, amount, price, shipping_time_standard, shipping_time_prime, prime FROM offers"
        .map(rs => Offer(rs)).list.apply()
    })
    res match {
      case scala.util.Success(v) => Success(v)
      case scala.util.Failure(e) => Failure(e.getMessage, 500)
    }
  }

  def getOffer(offer_id: Long): Result[Offer] = {
    val res = Try(DB readOnly { implicit session =>
      sql"""SELECT offer_id, product_id, merchant_id, amount, price, shipping_time_standard, shipping_time_prime, prime
        FROM offers
        WHERE offer_id = $offer_id"""
        .map(rs => Offer(rs)).list.apply().headOption
    })
    res match {
      case scala.util.Success(Some(v)) => Success(v)
      case scala.util.Success(None) => Failure(s"No object with key $offer_id found", 404)
      case scala.util.Failure(e) => Failure(e.getMessage, 500)
    }
  }

  def buyOffer(offer_id: Long, price: BigDecimal, amount: Int): Result[Unit] = {
    val res = Try(DB localTx { implicit session =>
      sql"UPDATE offers SET amount = amount - $amount WHERE id = $offer_id AND price = $price".update().apply()
    })
    res match {
      case scala.util.Success(v) if v == 1 => Success((): Unit)
      case scala.util.Success(v) if v != 1 => Failure("price changed", 409)
      case scala.util.Failure(_) => Failure("out of stock", 410)
    }
  }

  def updateOffer(offer_id: Long, offer: Offer): Result[Offer] = {
    val res = Try { DB localTx { implicit session =>
      sql"""UPDATE offers SET
        product_id = ${offer.product_id},
        merchant_id = ${offer.merchant_id},
        amount = ${offer.amount},
        price = ${offer.price},
        shipping_time_standard = ${offer.shipping_time.standard},
        shipping_time_prime = ${offer.shipping_time.prime},
        prime = ${offer.prime}
        WHERE offer_id = $offer_id"""
      sql"""SELECT offer_id, product_id, merchant_id, amount, price, shipping_time_standard, shipping_time_prime, prime
        FROM offers
        WHERE offer_id = $offer_id"""
        .map(rs => Offer(rs)).list.apply().headOption
    }}
    res match {
      case scala.util.Success(Some(v)) => Success(v)
      case scala.util.Success(None) => Failure("item not found", 404)
      case scala.util.Failure(e) => Failure(e.getMessage, 500)
    }
  }

  def restockOffer(offer_id: Long, amount: Int): Result[Offer] = {
    val res = Try { DB localTx { implicit session =>
      sql"UPDATE offers SET amount = amount + $amount WHERE offer_id = $offer_id".executeUpdate()
      sql"""SELECT offer_id, product_id, merchant_id, amount, price, shipping_time_standard, shipping_time_prime, prime
        FROM offers
        WHERE offer_id = $offer_id"""
        .map(rs => Offer(rs)).list.apply().headOption
    }}
    res match {
      case scala.util.Success(Some(v)) => Success(v)
      case scala.util.Success(None) => Failure("item not found", 404)
      case scala.util.Failure(e) => Failure(e.getMessage, 500)
    }
  }
}

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
