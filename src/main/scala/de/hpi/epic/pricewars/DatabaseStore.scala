package de.hpi.epic.pricewars

import scalikejdbc._
import scalikejdbc.config._
import cakesolutions.kafka.{KafkaProducer, KafkaProducerRecord}
import KafkaProducer.Conf
import com.typesafe.config.ConfigFactory
import org.apache.kafka.common.serialization.StringSerializer
import org.joda.time.DateTime

import scala.util.Try

object DatabaseStore {
  DBs.setupAll()

  def setup(): Unit = {
    reset()

    DB localTx { implicit session =>
      sql"""CREATE TABLE IF NOT EXISTS merchants (
        merchant_id SERIAL UNIQUE,
        api_endpoint_url VARCHAR(255) UNIQUE NOT NULL,
        merchant_name VARCHAR(255) NOT NULL,
        algorithm_name VARCHAR(255) NOT NULL
      )""".execute.apply()
      sql"""CREATE TABLE IF NOT EXISTS consumers (
        consumer_id SERIAL UNIQUE,
        api_endpoint_url VARCHAR(255) NOT NULL,
        consumer_name VARCHAR(255) NOT NULL,
        description VARCHAR(255) NOT NULL
      )""".execute.apply()
      sql"""CREATE TABLE IF NOT EXISTS products (
         product_id SERIAL NOT NULL PRIMARY KEY,
         name VARCHAR(255) NOT NULL,
         genre VARCHAR(255) NOT NULL
      )""".execute.apply()
      sql"""CREATE TABLE IF NOT EXISTS offers (
        offer_id SERIAL NOT NULL PRIMARY KEY,
        uid INTEGER NOT NULL,
        product_id INTEGER NOT NULL,
        quality INTEGER NOT NULL,
        merchant_id INTEGER NOT NULL REFERENCES merchants ( merchant_id ) ON DELETE CASCADE,
        amount INTEGER NOT NULL CHECK (amount >= 0),
        price NUMERIC(11,2) NOT NULL,
        shipping_time_standard INTEGER NOT NULL,
        shipping_time_prime INTEGER,
        prime BOOLEAN
      )""".execute.apply()
      sql"""CREATE TABLE IF NOT EXISTS used_signatures (
        signature VARCHAR(255) NOT NULL UNIQUE PRIMARY KEY,
        used_amount INTEGER NOT NULL CHECK (used_amount >= 0)
      )""".execute.apply()
    }
  }

  val config = ConfigFactory.load

  val kafka_producer = KafkaProducer(Conf(config.getConfig("kafka"), new StringSerializer, new StringSerializer))
  val producer_key: String = ProducerConnector.getProducerKey(config.getString("producer_url"))

  def addOffer(offer: Offer): Result[Offer] = {
    val validSignature: Boolean = ProducerConnector.validSignature(offer.uid, offer.amount, offer.signature.getOrElse(""), producer_key)

    if (validSignature) {
      val res = Try(DB localTx { implicit session =>
        sql"""INSERT INTO offers VALUES (
          DEFAULT,
          ${offer.uid},
          ${offer.product_id},
          ${offer.quality},
          ${offer.merchant_id},
          ${offer.amount},
          ${offer.price},
          ${offer.shipping_time.standard},
          ${offer.shipping_time.prime},
          ${offer.prime}
      )""".updateAndReturnGeneratedKey.apply()
      })
      res match {
        case scala.util.Success(id) => {
          kafka_producer.send(KafkaProducerRecord("addOffer", s"""{"offer_id": $id, "uid": ${offer.uid}, "product_id": ${offer.product_id}, "quality": ${offer.quality}, "merchant_id": ${offer.merchant_id}, "amount": ${offer.amount}, "price": ${offer.price}, "shipping_time_standard": ${offer.shipping_time.standard}, "shipping_time_prime": ${offer.shipping_time.prime.getOrElse(0)}, "prime": ${offer.prime}, "signature": "${offer.signature.getOrElse("")}", "http_code": 200, "timestamp": "${new DateTime()}"}"""))
          Success(offer.copy(offer_id = Some(id), signature = None))
        }
        case scala.util.Failure(e) => {
          kafka_producer.send(KafkaProducerRecord("addOffer", s"""{"uid": ${offer.uid}, "product_id": ${offer.product_id}, "quality": ${offer.quality}, "merchant_id": ${offer.merchant_id}, "amount": ${offer.amount}, "price": ${offer.price}, "shipping_time_standard": ${offer.shipping_time.standard}, "shipping_time_prime": ${offer.shipping_time.prime.getOrElse(0)}, "prime": ${offer.prime}, "signature": "${offer.signature.getOrElse("")}", "http_code": 500, "timestamp": "${new DateTime()}"}"""))
          Failure(e.getMessage, 500)
        }
      }
    } else {
      kafka_producer.send(KafkaProducerRecord("addOffer", s"""{"uid": ${offer.uid}, "product_id": ${offer.product_id}, "quality": ${offer.quality}, "merchant_id": ${offer.merchant_id}, "amount": ${offer.amount}, "price": ${offer.price}, "shipping_time_standard": ${offer.shipping_time.standard}, "shipping_time_prime": ${offer.shipping_time.prime.getOrElse(0)}, "prime": ${offer.prime}, "signature": "${offer.signature.getOrElse("")}", "http_code": 451, "timestamp": "${new DateTime()}"}"""))
      Failure("Invalid signature", 451)
    }
  }

  def deleteOffer(offer_id: Long): Result[Unit] = {
    val res = Try(DB localTx { implicit session =>
      sql"DELETE FROM offers WHERE offer_id = $offer_id".executeUpdate().apply()
    })
    res match {
      case scala.util.Success(v) if v == 1 => {
        kafka_producer.send(KafkaProducerRecord("deleteOffer", s"""{"offer_id": $offer_id, "http_code": 200, "timestamp": "${new DateTime()}"}"""))
        Success((): Unit)
      }
      case scala.util.Success(v) if v != 1 => {
        kafka_producer.send(KafkaProducerRecord("deleteOffer", s"""{"offer_id": $offer_id, "http_code": 404, "timestamp": "${new DateTime()}"}"""))
        Failure(s"No product with id $offer_id", 404)
      }
      case scala.util.Failure(e) => {
        kafka_producer.send(KafkaProducerRecord("deleteOffer", s"""{"offer_id": $offer_id, "http_code": 500, "timestamp": "${new DateTime()}"}"""))
        Failure(e.getMessage, 500)
      }
    }
  }

  def getOffers(product_id: Option[Long]): Result[Seq[Offer]] = {
    val res = Try(DB readOnly { implicit session =>
      val sql = product_id match {
        case Some(id) => sql"SELECT offer_id, uid, product_id, quality, merchant_id, amount, price, shipping_time_standard, shipping_time_prime, prime FROM offers WHERE amount > 0 AND product_id = $id"
        case None => sql"SELECT offer_id, uid, product_id, quality, merchant_id, amount, price, shipping_time_standard, shipping_time_prime, prime FROM offers WHERE amount > 0"
      }
      sql.map(rs => Offer(rs)).list.apply()
    })
    res match {
      case scala.util.Success(v) => {
        kafka_producer.send(KafkaProducerRecord("getOffers", s"""{"product_id": $product_id, "http_code": 200, "timestamp": "${new DateTime()}"}"""))
        Success(v)
      }
      case scala.util.Failure(e) => {
        kafka_producer.send(KafkaProducerRecord("getOffers", s"""{"product_id": $product_id, "http_code": 500, "timestamp": "${new DateTime()}"}"""))
        Failure(e.getMessage, 500)
      }
    }
  }

  def getOffer(offer_id: Long): Result[Offer] = {
    val res = Try(DB readOnly { implicit session =>
      sql"""SELECT offer_id, uid, product_id, quality, merchant_id, amount, price, shipping_time_standard, shipping_time_prime, prime
        FROM offers
        WHERE offer_id = $offer_id"""
        .map(rs => Offer(rs)).list.apply().headOption
    })
    res match {
      case scala.util.Success(Some(v)) => {
        kafka_producer.send(KafkaProducerRecord("getOffer", s"""{"offer_id": $offer_id, "http_code": 200, "timestamp": "${new DateTime()}"}"""))
        Success(v)
      }
      case scala.util.Success(None) => {
        kafka_producer.send(KafkaProducerRecord("getOffer", s"""{"offer_id": $offer_id, "http_code": 404, "timestamp": "${new DateTime()}"}"""))
        Failure(s"No object with key $offer_id found", 404)
      }
      case scala.util.Failure(e) => {
        kafka_producer.send(KafkaProducerRecord("getOffer", s"""{"offer_id": $offer_id, "http_code": 500, "timestamp": "${new DateTime()}"}"""))
        Failure(e.getMessage, 500)
      }
    }
  }

  def buyOffer(offer_id: Long, price: BigDecimal, amount: Int): Result[Unit] = {
    var merchant: Option[Merchant] = None
    var merchant_id: Long = -1
    DatabaseStore.getOffer(offer_id).flatMap(offer => DatabaseStore.getMerchant(offer.merchant_id)) match {
      case Success(merchantFound) => {
        merchant = Some(merchantFound)
        merchant_id = merchantFound.merchant_id.getOrElse(-1)
      }
    }
    val res = Try(DB localTx { implicit session =>
      sql"UPDATE offers SET amount = amount - $amount WHERE offer_id = $offer_id AND price <= $price".executeUpdate().apply()
    })
    res match {
      case scala.util.Success(v) if v == 1 => {
        kafka_producer.send(KafkaProducerRecord("buyOffer", s"""{"offer_id": $offer_id, "price": $price, "amount": $amount, "merchant_id": $merchant_id, "http_code": 200, "timestamp": "${new DateTime()}"}"""))
        MerchantConnector.notifyMerchant(merchant.get, offer_id, amount, price)
        Success((): Unit)
      }
      case scala.util.Success(v) if v != 1 => {
        kafka_producer.send(KafkaProducerRecord("buyOffer", s"""{"offer_id": $offer_id, "price": $price, "amount": $amount, "merchant_id": $merchant_id, "http_code": 409, "timestamp": "${new DateTime()}"}"""))
        Failure("price changed or product not found", 409)
      } // TODO: Check why the update failed
      case scala.util.Failure(_) => {
        kafka_producer.send(KafkaProducerRecord("buyOffer", s"""{"offer_id": $offer_id, "price": $price, "amount": $amount, "merchant_id": $merchant_id, "http_code": 410, "timestamp": "${new DateTime()}"}"""))
        Failure("out of stock", 410)
      }
    }
  }

  def updateOffer(offer_id: Long, offer: Offer): Result[Offer] = {
    // val validSignature: Boolean = ProducerConnector.validSignature(offer.uid, offer.amount, offer.signature.getOrElse(""), producer_key)
    // amount = ${offer.amount},

    val validSignature = true

    if (validSignature) {
      val res = Try {
        DB localTx { implicit session =>
          sql"""UPDATE offers SET
        uid = ${offer.uid},
        product_id = ${offer.product_id},
        quality = ${offer.quality},
        merchant_id = ${offer.merchant_id},
        price = ${offer.price},
        shipping_time_standard = ${offer.shipping_time.standard},
        shipping_time_prime = ${offer.shipping_time.prime},
        prime = ${offer.prime}
        WHERE offer_id = $offer_id""".executeUpdate().apply()
          sql"""SELECT offer_id, uid, product_id, quality, merchant_id, amount, price, shipping_time_standard, shipping_time_prime, prime
        FROM offers
        WHERE offer_id = $offer_id"""
            .map(rs => Offer(rs)).list.apply().headOption
        }
      }
      res match {
        case scala.util.Success(Some(v)) => {
          kafka_producer.send(KafkaProducerRecord("updateOffer", s"""{"offer_id": $offer_id, "uid": ${offer.uid}, "product_id": ${offer.product_id}, "quality": ${offer.quality}, "merchant_id": ${offer.merchant_id}, "amount": ${offer.amount}, "price": ${offer.price}, "shipping_time_standard": ${offer.shipping_time.standard}, "shipping_time_prime": ${offer.shipping_time.prime.getOrElse(0)}, "prime": ${offer.prime}, "signature": "${offer.signature.getOrElse("")}", "http_code": 200, "timestamp": "${new DateTime()}"}"""))
          Success(v)
        }
        case scala.util.Success(None) => {
          kafka_producer.send(KafkaProducerRecord("updateOffer", s"""{"offer_id": $offer_id, "uid": ${offer.uid}, "product_id": ${offer.product_id}, "quality": ${offer.quality}, "merchant_id": ${offer.merchant_id}, "amount": ${offer.amount}, "price": ${offer.price}, "shipping_time_standard": ${offer.shipping_time.standard}, "shipping_time_prime": ${offer.shipping_time.prime.getOrElse(0)}, "prime": ${offer.prime}, "signature": "${offer.signature.getOrElse("")}", "http_code": 404, "timestamp": "${new DateTime()}"}"""))
          Failure("item not found", 404)
        }
        case scala.util.Failure(e) => {
          kafka_producer.send(KafkaProducerRecord("updateOffer", s"""{"offer_id": $offer_id, "uid": ${offer.uid}, "product_id": ${offer.product_id}, "quality": ${offer.quality}, "merchant_id": ${offer.merchant_id}, "amount": ${offer.amount}, "price": ${offer.price}, "shipping_time_standard": ${offer.shipping_time.standard}, "shipping_time_prime": ${offer.shipping_time.prime.getOrElse(0)}, "prime": ${offer.prime}, "signature": "${offer.signature.getOrElse("")}", "http_code": 500, "timestamp": "${new DateTime()}"}"""))
          Failure(e.getMessage, 500)
        }
      }
    } else {
      kafka_producer.send(KafkaProducerRecord("updateOffer", s"""{"offer_id": $offer_id, "uid": ${offer.uid}, "product_id": ${offer.product_id}, "quality": ${offer.quality}, "merchant_id": ${offer.merchant_id}, "amount": ${offer.amount}, "price": ${offer.price}, "shipping_time_standard": ${offer.shipping_time.standard}, "shipping_time_prime": ${offer.shipping_time.prime.getOrElse(0)}, "prime": ${offer.prime}, "signature": "${offer.signature.getOrElse("")}", "http_code": 451, "timestamp": "${new DateTime()}"}"""))
      Failure("Invalid signature", 451)
    }
  }

  def restockOffer(offer_id: Long, amount: Int, signature: String): Result[Offer] = {
    var offerOption: Option[Offer] = None
    DatabaseStore.getOffer(offer_id) match {
      case Success(offerFound) => {
        offerOption = Some(offerFound)
      }
    }
    val offer = offerOption.get

    val validSignature: Boolean = ProducerConnector.validSignature(offer.uid, offer.amount, offer.signature.getOrElse(""), producer_key)

    if (validSignature) {
      val res = Try {
        DB localTx { implicit session =>
          sql"UPDATE offers SET amount = amount + $amount WHERE offer_id = $offer_id".executeUpdate().apply()
          sql"""SELECT offer_id, uid, product_id, quality, merchant_id, amount, price, shipping_time_standard, shipping_time_prime, prime
        FROM offers
        WHERE offer_id = $offer_id"""
            .map(rs => Offer(rs)).list.apply().headOption
        }
      }
      res match {
        case scala.util.Success(Some(v)) => {
          kafka_producer.send(KafkaProducerRecord("restockOffer", s"""{"offer_id": $offer_id, "amount": $amount, "signature": "$signature", "http_code": 200, "timestamp": "${new DateTime()}"}"""))
          Success(v)
        }
        case scala.util.Success(None) => {
          kafka_producer.send(KafkaProducerRecord("restockOffer", s"""{"offer_id": $offer_id, "amount": $amount, "signature": "$signature", "http_code": 404, "timestamp": "${new DateTime()}"}"""))
          Failure("item not found", 404)
        }
        case scala.util.Failure(e) => {
          kafka_producer.send(KafkaProducerRecord("restockOffer", s"""{"offer_id": $offer_id, "amount": $amount, "signature": "$signature", "http_code": 500, "timestamp": "${new DateTime()}"}"""))
          Failure(e.getMessage, 417)
        }
      }
    } else {
      kafka_producer.send(KafkaProducerRecord("restockOffer", s"""{"uid": ${offer.uid}, "product_id": ${offer.product_id}, "quality": ${offer.quality}, "merchant_id": ${offer.merchant_id}, "amount": ${offer.amount}, "price": ${offer.price}, "shipping_time_standard": ${offer.shipping_time.standard}, "shipping_time_prime": ${offer.shipping_time.prime.getOrElse(0)}, "prime": ${offer.prime}, "signature": "${offer.signature.getOrElse("")}", "http_code": 451, "timestamp": "${new DateTime()}"}"""))
      Failure("Invalid signature", 451)
    }
  }

  def addMerchant(merchant: Merchant): Result[Merchant] = {
    val res = Try(DB localTx { implicit session =>
      sql"""BEGIN;
      SELECT merchant_id INTO TEMPORARY TABLE existing_merchant FROM merchants WHERE api_endpoint_url = ${merchant.api_endpoint_url};
      DELETE FROM merchants WHERE merchant_id = (SELECT merchant_id FROM existing_merchant);
      INSERT INTO merchants VALUES (DEFAULT, ${merchant.api_endpoint_url}, ${merchant.merchant_name}, ${merchant.algorithm_name});
      DROP TABLE existing_merchant;
      COMMIT;""".update().apply()
      sql"SELECT merchant_id, api_endpoint_url, merchant_name, algorithm_name FROM merchants WHERE api_endpoint_url = ${merchant.api_endpoint_url}".map(rs => Merchant(rs)).list.apply().headOption.get.merchant_id.get
    })
    res match {
      case scala.util.Success(id) => {
        kafka_producer.send(KafkaProducerRecord("addMerchant", s"""{"merchant_id": $id, "api_endpoint_url": ${merchant.api_endpoint_url}, "merchant_name": ${merchant.merchant_name}, "algorithm_name": ${merchant.algorithm_name}, "http_code": 200, "timestamp": "${new DateTime()}"}"""))
        Success(merchant.copy(merchant_id = Some(id)))
      }
      case scala.util.Failure(e) => {
        kafka_producer.send(KafkaProducerRecord("addMerchant", s"""{"api_endpoint_url": ${merchant.api_endpoint_url}, "merchant_name": ${merchant.merchant_name}, "algorithm_name": ${merchant.algorithm_name}, "http_code": 500, "timestamp": "${new DateTime()}"}"""))
        Failure(e.getMessage, 500)
      }
    }
  }

  def deleteMerchant(merchant_id: Long): Result[Unit] = {
    val res = Try(DB localTx { implicit session =>
      sql"DELETE FROM merchants WHERE merchant_id = $merchant_id".executeUpdate().apply()
    })
    res match {
      case scala.util.Success(v) if v == 1 => {
        kafka_producer.send(KafkaProducerRecord("deleteMerchant", s"""{"merchant_id": $merchant_id, "http_code": 200, "timestamp": "${new DateTime()}"}"""))
        Success((): Unit)
      }
      case scala.util.Success(v) if v != 1 => {
        kafka_producer.send(KafkaProducerRecord("deleteMerchant", s"""{"merchant_id": $merchant_id, "http_code": 404, "timestamp": "${new DateTime()}"}"""))
        Failure(s"No merchant with id $merchant_id", 404)
      }
      case scala.util.Failure(e) => {
        kafka_producer.send(KafkaProducerRecord("deleteMerchant", s"""{"merchant_id": $merchant_id, "http_code": 500, "timestamp": "${new DateTime()}"}"""))
        Failure(e.getMessage, 500)
      }
    }
  }

  def getMerchants: Result[Seq[Merchant]] = {
    val res = Try(DB readOnly { implicit session =>
      sql"SELECT merchant_id, api_endpoint_url, merchant_name, algorithm_name FROM merchants"
        .map(rs => Merchant(rs)).list.apply()
    })
    res match {
      case scala.util.Success(v) => {
        kafka_producer.send(KafkaProducerRecord("getMerchants", s"""{"http_code": 200, "timestamp": "${new DateTime()}"}"""))
        Success(v)
      }
      case scala.util.Failure(e) => {
        kafka_producer.send(KafkaProducerRecord("getMerchants", s"""{"http_code": 500, "timestamp": "${new DateTime()}"}"""))
        Failure(e.getMessage, 500)
      }
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
      case scala.util.Success(Some(v)) => {
        kafka_producer.send(KafkaProducerRecord("getMerchant", s"""{"merchant_id": $merchant_id, "http_code": 200, "timestamp": "${new DateTime()}"}"""))
        Success(v)
      }
      case scala.util.Success(None) => {
        kafka_producer.send(KafkaProducerRecord("getMerchant", s"""{"merchant_id": $merchant_id, "http_code": 404, "timestamp": "${new DateTime()}"}"""))
        Failure(s"No merchant with key $merchant_id found", 404)
      }
      case scala.util.Failure(e) => {
        kafka_producer.send(KafkaProducerRecord("getMerchant", s"""{"merchant_id": $merchant_id, "http_code": 500, "timestamp": "${new DateTime()}"}"""))
        Failure(e.getMessage, 500)
      }
    }
  }

  def addConsumer(consumer: Consumer): Result[Consumer] = {
    val res = Try(DB localTx { implicit session =>
      sql"INSERT INTO consumers VALUES (DEFAULT, ${consumer.api_endpoint_url}, ${consumer.consumer_name}, ${consumer.description})"
        .updateAndReturnGeneratedKey.apply()
    })
    res match {
      case scala.util.Success(id) => {
        kafka_producer.send(KafkaProducerRecord("addConsumer", s"""{"consumer_id": $id, "api_endpoint_url": ${consumer.api_endpoint_url}, "consumer_name": ${consumer.consumer_name}, "description": ${consumer.description}, "http_code": 200, "timestamp": "${new DateTime()}"}"""))
        Success(consumer.copy(consumer_id = Some(id)))
      }
      case scala.util.Failure(e) => {
        kafka_producer.send(KafkaProducerRecord("addConsumer", s"""{"api_endpoint_url": ${consumer.api_endpoint_url}, "consumer_name": ${consumer.consumer_name}, "description": ${consumer.description}, "http_code": 500, "timestamp": "${new DateTime()}"}"""))
        Failure(e.getMessage, 500)
      }
    }
  }

  def deleteConsumer(consumer_id: Long): Result[Unit] = {
    val res = Try(DB localTx { implicit session =>
      sql"DELETE FROM consumers WHERE consumer_id = $consumer_id".executeUpdate().apply()
    })
    res match {
      case scala.util.Success(v) if v == 1 => {
        kafka_producer.send(KafkaProducerRecord("deleteConsumer", s"""{"consumer_id": $consumer_id, "http_code": 200, "timestamp": "${new DateTime()}"}"""))
        Success((): Unit)
      }
      case scala.util.Success(v) if v != 1 => {
        kafka_producer.send(KafkaProducerRecord("deleteConsumer", s"""{"consumer_id": $consumer_id, "http_code": 404, "timestamp": "${new DateTime()}"}"""))
        Failure(s"No consumer with id $consumer_id", 404)
      }
      case scala.util.Failure(e) => {
        kafka_producer.send(KafkaProducerRecord("deleteConsumer", s"""{"consumer_id": $consumer_id, "http_code": 500, "timestamp": "${new DateTime()}"}"""))
        Failure(e.getMessage, 500)
      }
    }
  }

  def getConsumers: Result[Seq[Consumer]] = {
    val res = Try(DB readOnly { implicit session =>
      sql"SELECT consumer_id, api_endpoint_url, consumer_name, description FROM consumers"
        .map(rs => Consumer(rs)).list.apply()
    })
    res match {
      case scala.util.Success(v) => {
        kafka_producer.send(KafkaProducerRecord("getConsumers", s"""{"http_code": 200, "timestamp": "${new DateTime()}"}"""))
        Success(v)
      }
      case scala.util.Failure(e) => {
        kafka_producer.send(KafkaProducerRecord("getConsumers", s"""{"http_code": 500, "timestamp": "${new DateTime()}"}"""))
        Failure(e.getMessage, 500)
      }
    }
  }

  def getConsumer(consumer_id: Long): Result[Consumer] = {
    val res = Try(DB readOnly { implicit session =>
      sql"""SELECT consumer_id, api_endpoint_url, consumer_name, description
        FROM consumers
        WHERE consumer_id = $consumer_id"""
        .map(rs => Consumer(rs)).list.apply().headOption
    })
    res match {
      case scala.util.Success(Some(v)) => {
        kafka_producer.send(KafkaProducerRecord("getConsumer", s"""{"consumer_id": $consumer_id, "http_code": 200, "timestamp": "${new DateTime()}"}"""))
        Success(v)
      }
      case scala.util.Success(None) => {
        kafka_producer.send(KafkaProducerRecord("getConsumer", s"""{"consumer_id": $consumer_id, "http_code": 404, "timestamp": "${new DateTime()}"}"""))
        Failure(s"No consumer with key $consumer_id found", 404)
      }
      case scala.util.Failure(e) => {
        kafka_producer.send(KafkaProducerRecord("getConsumer", s"""{"consumer_id": $consumer_id, "http_code": 500, "timestamp": "${new DateTime()}"}"""))
        Failure(e.getMessage, 500)
      }
    }
  }

  def addProduct(product: Product): Result[Product] = {
    val res = Try(DB localTx { implicit session =>
      sql"INSERT INTO products VALUES (DEFAULT, ${product.name}, ${product.genre})"
        .updateAndReturnGeneratedKey.apply()
    })
    res match {
      case scala.util.Success(id) => {
        kafka_producer.send(KafkaProducerRecord("addProduct", s"""{"product_id": $id, "name": ${product.name}, "genre": ${product.genre}, "http_code": 200, "timestamp": "${new DateTime()}"}"""))
        Success(product.copy(product_id = Some(id)))
      }
      case scala.util.Failure(e) => {
        kafka_producer.send(KafkaProducerRecord("addProduct", s"""{"name": ${product.name}, "genre": ${product.genre}, "http_code": 500, "timestamp": "${new DateTime()}"}"""))
        Failure(e.getMessage, 500)
      }
    }
  }

  def deleteProduct(product_id: Long): Result[Unit] = {
    val res = Try(DB localTx { implicit session =>
      sql"DELETE FROM products WHERE product_id = $product_id".executeUpdate().apply()
    })
    res match {
      case scala.util.Success(v) if v == 1 => {
        kafka_producer.send(KafkaProducerRecord("deleteProduct", s"""{"product_id": $product_id, "http_code": 200, "timestamp": "${new DateTime()}"}"""))
        Success((): Unit)
      }
      case scala.util.Success(v) if v != 1 => {
        kafka_producer.send(KafkaProducerRecord("deleteProduct", s"""{"product_id": $product_id, "http_code": 404, "timestamp": "${new DateTime()}"}"""))
        Failure(s"No product with id $product_id", 404)
      }
      case scala.util.Failure(e) => {
        kafka_producer.send(KafkaProducerRecord("deleteProduct", s"""{"product_id": $product_id, "http_code": 500, "timestamp": "${new DateTime()}"}"""))
        Failure(e.getMessage, 500)
      }
    }
  }

  def getProducts: Result[Seq[Product]] = {
    val res = Try(DB readOnly { implicit session =>
      sql"SELECT product_id, name, genre FROM products"
        .map(rs => Product(rs)).list.apply()
    })
    res match {
      case scala.util.Success(v) => {
        kafka_producer.send(KafkaProducerRecord("getProducts", s"""{"http_code": 200, "timestamp": "${new DateTime()}"}"""))
        Success(v)
      }
      case scala.util.Failure(e) => {
        kafka_producer.send(KafkaProducerRecord("getProducts", s"""{"http_code": 500, "timestamp": "${new DateTime()}"}"""))
        Failure(e.getMessage, 500)
      }
    }
  }

  def getProduct(product_id: Long): Result[Product] = {
    val res = Try(DB readOnly { implicit session =>
      sql"""SELECT product_id, name, genre
        FROM products
        WHERE product_id = $product_id"""
        .map(rs => Product(rs)).list.apply().headOption
    })
    res match {
      case scala.util.Success(Some(v)) => {
        kafka_producer.send(KafkaProducerRecord("getProduct", s"""{"product_id": $product_id, "http_code": 200, "timestamp": "${new DateTime()}"}"""))
        Success(v)
      }
      case scala.util.Success(None) => {
        kafka_producer.send(KafkaProducerRecord("getProduct", s"""{"product_id": $product_id, "http_code": 404, "timestamp": "${new DateTime()}"}"""))
        Failure(s"No product with key $product_id found", 404)
      }
      case scala.util.Failure(e) => {
        kafka_producer.send(KafkaProducerRecord("getProduct", s"""{"product_id": $product_id, "http_code": 500, "timestamp": "${new DateTime()}"}"""))
        Failure(e.getMessage, 500)
      }
    }
  }

  def getUsedAmountForSignature(signature: String): Int = {
    val amount: Option[Int] = DB readOnly { implicit session =>
      sql"""SELECT used_amount
        FROM used_signatures
        WHERE signature = $signature"""
        .map(rs => rs.int("used_amount")).single.apply()
    }
    amount.getOrElse(0)
  }

  def setUsedAmountForSignature(signature: String, amount: Int): Boolean = {
    val res = Try(DB localTx { implicit session =>
      sql"""INSERT INTO used_signatures VALUES
            ($signature,
            $amount) ON CONFLICT (signature) DO UPDATE SET used_amount = $amount"""
        .executeUpdate().apply()
    })
    res match {
      case scala.util.Success(v) if v == 1 => {
        true
      }
      case scala.util.Success(v) if v != 1 => {
        false
      }
      case scala.util.Failure(v) => {
        false
      }
    }
  }

  def reset(): Unit = {
    DB localTx { implicit session =>
      sql"""DROP TABLE IF EXISTS products""".execute.apply()
      sql"""DROP TABLE IF EXISTS offers""".execute.apply()
      sql"""DROP TABLE IF EXISTS merchants""".execute.apply()
      sql"""DROP TABLE IF EXISTS consumers""".execute.apply()
      sql"""DROP TABLE IF EXISTS used_signatures""".execute.apply()
    }
  }
}
