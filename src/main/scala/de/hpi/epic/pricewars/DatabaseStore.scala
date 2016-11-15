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

  def setup(): Unit = {
    DB localTx { implicit session =>
      sql"""CREATE TABLE IF NOT EXISTS merchants (
        merchant_id SERIAL UNIQUE,
        api_endpoint_url VARCHAR(250) NOT NULL,
        merchant_name VARCHAR(100) NOT NULL,
        algorithm_name VARCHAR(100) NOT NULL
      )""".execute.apply()
      sql"""CREATE TABLE IF NOT EXISTS offers (
        offer_id SERIAL NOT NULL PRIMARY KEY,
        product_id INTEGER NOT NULL,
        merchant_id INTEGER NOT NULL REFERENCES merchants ( merchant_id ),
        amount INTEGER NOT NULL CHECK (amount >= 0),
        price NUMERIC(11,2) NOT NULL,
        shipping_time_standard INTEGER NOT NULL,
        shipping_time_prime INTEGER,
        prime BOOLEAN
      )""".execute.apply()
    }
  }

  def addOffer(offer: Offer): Result[Offer] = {
    val res = Try(DB localTx { implicit session =>
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
    })
    res match {
      case scala.util.Success(id) => Success(offer.copy(offer_id = Some(id)))
      case scala.util.Failure(e) => Failure(e.getMessage, 500)
    }
  }

  def deleteOffer(offer_id: Long): Result[Unit] = {
    val res = Try(DB localTx { implicit session =>
      sql"DELETE FROM offers WHERE offer_id = $offer_id".executeUpdate().apply()
    })
    res match {
      case scala.util.Success(v) if v == 1 => Success((): Unit)
      case scala.util.Success(v) if v != 1 => Failure(s"No product with id $offer_id", 404)
      case scala.util.Failure(e) => Failure(e.getMessage, 500)
    }
  }

  def getOffers(product_id: Option[Long]): Result[Seq[Offer]] = {
    val res = Try(DB readOnly { implicit session =>
      val sql = product_id match {
        case Some(id) => sql"SELECT offer_id, product_id, merchant_id, amount, price, shipping_time_standard, shipping_time_prime, prime FROM offers WHERE amount > 0 AND product_id = $id"
        case None => sql"SELECT offer_id, product_id, merchant_id, amount, price, shipping_time_standard, shipping_time_prime, prime FROM offers WHERE amount > 0"
      }
      sql.map(rs => Offer(rs)).list.apply()
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
      sql"UPDATE offers SET amount = amount - $amount WHERE offer_id = $offer_id AND price <= $price".executeUpdate().apply()
    })
    res match {
      case scala.util.Success(v) if v == 1 => Success((): Unit)
      case scala.util.Success(v) if v != 1 => Failure("price changed or product not found", 409) // TODO: Check why the update failed
      case scala.util.Failure(_) => Failure("out of stock", 410)
    }
  }

  def updateOffer(offer_id: Long, offer: Offer): Result[Offer] = {
    val res = Try {
      DB localTx { implicit session =>
        sql"""UPDATE offers SET
        product_id = ${offer.product_id},
        merchant_id = ${offer.merchant_id},
        amount = ${offer.amount},
        price = ${offer.price},
        shipping_time_standard = ${offer.shipping_time.standard},
        shipping_time_prime = ${offer.shipping_time.prime},
        prime = ${offer.prime}
        WHERE offer_id = $offer_id""".executeUpdate().apply()
        sql"""SELECT offer_id, product_id, merchant_id, amount, price, shipping_time_standard, shipping_time_prime, prime
        FROM offers
        WHERE offer_id = $offer_id"""
          .map(rs => Offer(rs)).list.apply().headOption
      }
    }
    res match {
      case scala.util.Success(Some(v)) => Success(v)
      case scala.util.Success(None) => Failure("item not found", 404)
      case scala.util.Failure(e) => Failure(e.getMessage, 500)
    }
  }

  def restockOffer(offer_id: Long, amount: Int): Result[Offer] = {
    val res = Try {
      DB localTx { implicit session =>
        sql"UPDATE offers SET amount = amount + $amount WHERE offer_id = $offer_id".executeUpdate().apply()
        sql"""SELECT offer_id, product_id, merchant_id, amount, price, shipping_time_standard, shipping_time_prime, prime
        FROM offers
        WHERE offer_id = $offer_id"""
          .map(rs => Offer(rs)).list.apply().headOption
      }
    }
    res match {
      case scala.util.Success(Some(v)) => Success(v)
      case scala.util.Success(None) => Failure("item not found", 404)
      case scala.util.Failure(e) => Failure(e.getMessage, 417)
    }
  }

  def addMerchant(merchant: Merchant): Result[Merchant] = {
    val res = Try(DB localTx { implicit session =>
      sql"INSERT INTO merchants VALUES (DEFAULT, ${merchant.api_endpoint_url}, ${merchant.merchant_name}, ${merchant.algorithm_name})"
        .updateAndReturnGeneratedKey.apply()
    })
    res match {
      case scala.util.Success(id) => Success(merchant.copy(merchant_id = Some(id)))
      case scala.util.Failure(e) => Failure(e.getMessage, 500)
    }
  }

  def deleteMerchant(merchant_id: Long): Result[Unit] = {
    val res = Try(DB localTx { implicit session =>
      sql"DELETE FROM merchants WHERE merchant_id = $merchant_id".executeUpdate().apply()
    })
    res match {
      case scala.util.Success(v) if v == 1 => Success((): Unit)
      case scala.util.Success(v) if v != 1 => Failure(s"No merchant with id $merchant_id", 404)
      case scala.util.Failure(e) => Failure(e.getMessage, 500)
    }
  }

  def getMerchants: Result[Seq[Merchant]] = {
    val res = Try(DB readOnly { implicit session =>
      sql"SELECT merchant_id, api_endpoint_url, merchant_name, algorithm_name FROM merchants"
        .map(rs => Merchant(rs)).list.apply()
    })
    res match {
      case scala.util.Success(v) => Success(v)
      case scala.util.Failure(e) => Failure(e.getMessage, 500)
    }
  }

  def getMerchant(merchant_id: Long): Result[Merchant] = {
    val res = Try(DB readOnly { implicit session =>
      sql"""SELECT merchant_id, api_endpoint_url, merchant_name, algorithm_name
        FROM merchants
        WHERE merchant_id = $merchant_id"""
        .map(rs => Merchant(rs)).list.apply().headOption
    })
    res match {
      case scala.util.Success(Some(v)) => Success(v)
      case scala.util.Success(None) => Failure(s"No merchant with key $merchant_id found", 404)
      case scala.util.Failure(e) => Failure(e.getMessage, 500)
    }
  }

  def reset(): Unit = {
    DB localTx { implicit session =>
      sql"""DROP TABLE IF EXISTS offers""".execute.apply()
      sql"""DROP TABLE IF EXISTS merchants""".execute.apply()
    }
  }
}
