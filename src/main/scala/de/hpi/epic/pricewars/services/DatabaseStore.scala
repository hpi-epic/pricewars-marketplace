package de.hpi.epic.pricewars.services

import cakesolutions.kafka.KafkaProducer.Conf
import cakesolutions.kafka.{KafkaProducer, KafkaProducerRecord}
import com.typesafe.config.{Config, ConfigFactory}
import de.hpi.epic.pricewars.connectors.{MerchantConnector, ProducerConnector}
import de.hpi.epic.pricewars.data
import de.hpi.epic.pricewars.data.{Consumer, Merchant, Offer, Product}
import de.hpi.epic.pricewars.utils.{Failure, Result, Success}
import org.apache.kafka.common.serialization.StringSerializer
import org.joda.time.DateTime
import scalikejdbc._
import scalikejdbc.config._
import spray.http.{StatusCode, StatusCodes}

import scala.util.Try

object DatabaseStore {
  DBs.setupAll()

  def setup(): Unit = {
    // Enable to reset database on every restart
    // reset()

    DB localTx { implicit session =>
      sql"""CREATE EXTENSION IF NOT EXISTS pgcrypto;""".execute.apply()
      sql"""CREATE OR REPLACE FUNCTION random_string(length INTEGER)
          RETURNS TEXT AS
        $$$$
        DECLARE
          chars  TEXT [] := '{0,1,2,3,4,5,6,7,8,9,A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U,V,W,X,Y,Z,a,b,c,d,e,f,g,h,i,j,k,l,m,n,o,p,q,r,s,t,u,v,w,x,y,z}';
          result TEXT := '';
          i      INTEGER := 0;
        BEGIN
          IF LENGTH < 0
          THEN
            RAISE EXCEPTION 'Given length cannot be less than 0';
          END IF;
          FOR i IN 1..LENGTH LOOP
            result := result || chars [1 + random() * (array_length(chars, 1) - 1)];
          END LOOP;
          RETURN result;
        END;
        $$$$ LANGUAGE plpgsql;""".execute.apply()
      sql"""CREATE TABLE IF NOT EXISTS merchants (
        merchant_id TEXT NOT NULL UNIQUE PRIMARY KEY,
        merchant_token TEXT UNIQUE,
        api_endpoint_url TEXT NOT NULL,
        merchant_name TEXT NOT NULL,
        algorithm_name TEXT NOT NULL
      )""".execute.apply()
      sql"""CREATE TABLE IF NOT EXISTS consumers (
        consumer_id TEXT NOT NULL UNIQUE PRIMARY KEY,
        consumer_token TEXT NOT NULL UNIQUE,
        api_endpoint_url TEXT NOT NULL,
        consumer_name TEXT NOT NULL,
        description TEXT NOT NULL
      )""".execute.apply()
      sql"""CREATE TABLE IF NOT EXISTS products (
         product_id SERIAL NOT NULL PRIMARY KEY,
         name TEXT NOT NULL,
         genre TEXT NOT NULL
      )""".execute.apply()
      sql"""CREATE TABLE IF NOT EXISTS offers (
        offer_id SERIAL NOT NULL UNIQUE PRIMARY KEY,
        uid INTEGER NOT NULL,
        product_id INTEGER NOT NULL,
        quality INTEGER NOT NULL,
        merchant_id TEXT NOT NULL REFERENCES merchants ( merchant_id ) ON DELETE CASCADE,
        amount INTEGER NOT NULL CHECK (amount >= 0),
        price NUMERIC(11,2) NOT NULL,
        shipping_time_standard INTEGER NOT NULL,
        shipping_time_prime INTEGER,
        prime BOOLEAN
      )""".execute.apply()
      sql"""CREATE TABLE IF NOT EXISTS used_signatures (
        signature TEXT NOT NULL UNIQUE PRIMARY KEY,
        used_amount INTEGER NOT NULL CHECK (used_amount >= 0)
      )""".execute.apply()
    }
  }

  val config: Config = ConfigFactory.load
  val kafka_producer = KafkaProducer(Conf(config.getConfig("kafka"), new StringSerializer, new StringSerializer))

  def reset(): Unit = {
    DB localTx { implicit session =>
      sql"""DROP TABLE IF EXISTS products""".execute.apply()
      sql"""DROP TABLE IF EXISTS offers""".execute.apply()
      sql"""DROP TABLE IF EXISTS merchants""".execute.apply()
      sql"""DROP TABLE IF EXISTS consumers""".execute.apply()
      sql"""DROP TABLE IF EXISTS used_signatures""".execute.apply()
    }
  }

  def addOffer(offer: Offer, merchant: Merchant): Result[Offer] = {
    val validSignature: Boolean = ProducerConnector.validSignature(offer.uid, offer.amount, offer.signature.getOrElse(""), merchant.merchant_id.getOrElse(""))

    if (validSignature) {
      val res = Try(DB localTx { implicit session =>
        sql"""INSERT INTO offers VALUES (
          DEFAULT,
          ${offer.uid},
          ${offer.product_id},
          ${offer.quality},
          ${merchant.merchant_id.get},
          ${offer.amount},
          ${offer.price},
          ${offer.shipping_time.standard},
          ${offer.shipping_time.prime.getOrElse(None)},
          ${offer.prime}
      )""".updateAndReturnGeneratedKey.apply()
      })
      res match {
        case scala.util.Success(id) => {
          kafka_producer.send(KafkaProducerRecord("addOffer", s"""{"offer_id": $id, "uid": ${offer.uid}, "product_id": ${offer.product_id}, "quality": ${offer.quality}, "merchant_id": ${merchant.merchant_id.get}, "amount": ${offer.amount}, "price": ${offer.price}, "shipping_time_standard": ${offer.shipping_time.standard}, "shipping_time_prime": ${offer.shipping_time.prime.getOrElse(0)}, "prime": ${offer.prime}, "signature": "${offer.signature.getOrElse("")}", "http_code": 200, "timestamp": "${new DateTime()}"}"""))
          logCurrentMarketSituation(offer.product_id, "addOffer", merchant.merchant_id.get)
          Success(offer.copy(offer_id = Some(id), signature = None, merchant_id = Some(merchant.merchant_id.get)))
        }
        case scala.util.Failure(e) => {
          kafka_producer.send(KafkaProducerRecord("addOffer", s"""{"uid": ${offer.uid}, "product_id": ${offer.product_id}, "quality": ${offer.quality}, "merchant_id": ${merchant.merchant_id.get}, "amount": ${offer.amount}, "price": ${offer.price}, "shipping_time_standard": ${offer.shipping_time.standard}, "shipping_time_prime": ${offer.shipping_time.prime.getOrElse(0)}, "prime": ${offer.prime}, "signature": "${offer.signature.getOrElse("")}", "http_code": 500, "timestamp": "${new DateTime()}"}"""))
          Failure(e.getMessage, 500)
        }
      }
    } else {
      kafka_producer.send(KafkaProducerRecord("addOffer", s"""{"uid": ${offer.uid}, "product_id": ${offer.product_id}, "quality": ${offer.quality}, "merchant_id": ${merchant.merchant_id.get}, "amount": ${offer.amount}, "price": ${offer.price}, "shipping_time_standard": ${offer.shipping_time.standard}, "shipping_time_prime": ${offer.shipping_time.prime.getOrElse(0)}, "prime": ${offer.prime}, "signature": "${offer.signature.getOrElse("")}", "http_code": 451, "timestamp": "${new DateTime()}"}"""))
      Failure("Invalid signature", 451)
    }
  }

  def addBulkOffers(offerArray: Array[Offer], merchant: Merchant): (Result[Array[Offer]], StatusCode) = {
    val res1 = offerArray.map(DatabaseStore.addOffer(_, merchant))
    res1.find(_.isFailure) match {
      case None => Success(res1.map(_.get)) -> StatusCodes.Created
      case Some(failure) => failure match {
        case f:Failure[Offer] => Success(
          res1.flatMap {
            case Success(v) => Some(v)
            case _ => None
          }) -> f.code
      }
    }

  }

  def deleteOffer(offer_id: Long, merchant: Merchant): Result[Unit] = {
    val res = Try(DB localTx { implicit session =>
      sql"DELETE FROM offers WHERE offer_id = $offer_id AND merchant_id = ${merchant.merchant_id.get}".executeUpdate().apply()
    })
    res match {
      case scala.util.Success(v) if v == 1 => {
        kafka_producer.send(KafkaProducerRecord("deleteOffer", s"""{"offer_id": $offer_id, "merchant_id": "${merchant.merchant_id.get}", "http_code": 200, "timestamp": "${new DateTime()}"}"""))
        Success((): Unit)
      }
      case scala.util.Success(v) if v != 1 => {
        kafka_producer.send(KafkaProducerRecord("deleteOffer", s"""{"offer_id": $offer_id, "merchant_id": "${merchant.merchant_id.get}", "http_code": 404, "timestamp": "${new DateTime()}"}"""))
        Failure(s"No product with id $offer_id", 404)
      }
      case scala.util.Failure(e) => {
        kafka_producer.send(KafkaProducerRecord("deleteOffer", s"""{"offer_id": $offer_id, "merchant_id": "${merchant.merchant_id.get}", "http_code": 500, "timestamp": "${new DateTime()}"}"""))
        Failure(e.getMessage, 500)
      }
    }
  }

  def getOffers(product_id: Option[Long], all_offers_from_merchant_id: Option[String]): Result[Seq[Offer]] = {
    val res = Try(DB readOnly { implicit session =>
      val sql = (product_id, all_offers_from_merchant_id) match {
        case (Some(id), Some(merchant_id)) => sql"SELECT offer_id, uid, product_id, quality, merchant_id, amount, price, shipping_time_standard, shipping_time_prime, prime FROM offers WHERE merchant_id = $merchant_id AND product_id = $id AND amount = 0 UNION SELECT offer_id, uid, product_id, quality, merchant_id, amount, price, shipping_time_standard, shipping_time_prime, prime FROM offers WHERE amount > 0 AND product_id = $id"
        case (Some(id), None) => sql"SELECT offer_id, uid, product_id, quality, merchant_id, amount, price, shipping_time_standard, shipping_time_prime, prime FROM offers WHERE amount > 0 AND product_id = $id"
        case (None, Some(merchant_id)) => sql"SELECT offer_id, uid, product_id, quality, merchant_id, amount, price, shipping_time_standard, shipping_time_prime, prime FROM offers WHERE merchant_id = $merchant_id AND amount = 0 UNION SELECT offer_id, uid, product_id, quality, merchant_id, amount, price, shipping_time_standard, shipping_time_prime, prime FROM offers WHERE amount > 0"
        case (None, None) => sql"SELECT offer_id, uid, product_id, quality, merchant_id, amount, price, shipping_time_standard, shipping_time_prime, prime FROM offers WHERE amount > 0"
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

  def buyOffer(offer_id: Long, price: BigDecimal, amount: Int, consumer: Consumer): Result[Unit] = {
    val offerResult = DatabaseStore.getOffer(offer_id)

    var offer: Option[Offer] = None
    offerResult match {
      case Success(offerFound) => offer = Some(offerFound)
    }

    var merchant: Option[Merchant] = None
    var merchant_id: String = ""
    offerResult.flatMap(offer => DatabaseStore.getMerchant(offer.merchant_id.get)) match {
      case Success(merchantFound) => {
        merchant = Some(merchantFound)
        merchant_id = merchantFound.merchant_id.getOrElse("")
      }
    }
    val res = Try(DB localTx { implicit session =>
      sql"UPDATE offers SET amount = amount - $amount WHERE offer_id = $offer_id AND price <= $price RETURNING offer_id, uid, product_id, quality, merchant_id, amount, price, shipping_time_standard, shipping_time_prime, prime".map(rs => Offer(rs)).list.apply().headOption
    })
    res match {
      case scala.util.Success(remaining_offer) if remaining_offer.isDefined => {
        kafka_producer.send(KafkaProducerRecord("buyOffer", s"""{"offer_id": $offer_id, "uid": ${offer.get.uid}, "product_id": ${offer.get.product_id}, "quality": ${offer.get.quality}, "price": $price, "amount": $amount, "merchant_id": "$merchant_id", "left_in_stock": ${remaining_offer.get.amount}, "consumer_id": "${consumer.consumer_id.get}", "http_code": 200, "timestamp": "${new DateTime()}"}"""))
        MerchantConnector.notifyMerchant(merchant.get, offer_id, amount, price, remaining_offer.get)
        Success((): Unit)
      }
      case scala.util.Success(v) if v.isEmpty => {
        kafka_producer.send(KafkaProducerRecord("buyOffer", s"""{"offer_id": $offer_id, "uid": ${offer.get.uid}, "product_id": ${offer.get.product_id}, "quality": ${offer.get.quality}, "price": $price, "amount": $amount, "merchant_id": "$merchant_id", "left_in_stock": 0, "consumer_id": "${consumer.consumer_id.get}", "http_code": 409, "timestamp": "${new DateTime()}"}"""))
        Failure("price changed or product not found", 409)
      } // TODO: Check why the update failed
      case scala.util.Failure(_) => {
        kafka_producer.send(KafkaProducerRecord("buyOffer", s"""{"offer_id": $offer_id, "uid": ${offer.get.uid}, "product_id": ${offer.get.product_id}, "quality": ${offer.get.quality}, "price": $price, "amount": $amount, "merchant_id": "$merchant_id", "left_in_stock": 0, "consumer_id": "${consumer.consumer_id.get}", "http_code": 410, "timestamp": "${new DateTime()}"}"""))
        Failure("out of stock", 410)
      }
    }
  }

  def updateOffer(offer_id: Long, offer: Offer, merchant: Merchant): Result[Offer] = {
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
        price = ${offer.price},
        shipping_time_standard = ${offer.shipping_time.standard},
        shipping_time_prime = ${offer.shipping_time.prime},
        prime = ${offer.prime}
        WHERE offer_id = $offer_id""".executeUpdate().apply()
          sql"""SELECT offer_id, uid, product_id, quality, merchant_id, amount, price, shipping_time_standard, shipping_time_prime, prime
        FROM offers
        WHERE offer_id = $offer_id AND merchant_id = ${merchant.merchant_id.get}"""
            .map(rs => Offer(rs)).list.apply().headOption
        }
      }
      res match {
        case scala.util.Success(Some(v)) => {
          kafka_producer.send(KafkaProducerRecord("updateOffer", s"""{"offer_id": $offer_id, "uid": ${offer.uid}, "product_id": ${offer.product_id}, "quality": ${offer.quality}, "merchant_id": "${merchant.merchant_id.get}", "amount": ${offer.amount}, "price": ${offer.price}, "shipping_time_standard": ${offer.shipping_time.standard}, "shipping_time_prime": ${offer.shipping_time.prime.getOrElse(0)}, "prime": ${offer.prime}, "signature": "${offer.signature.getOrElse("")}", "http_code": 200, "timestamp": "${new DateTime()}"}"""))
          logCurrentMarketSituation(offer.product_id, "updateOffer", merchant.merchant_id.get)
          Success(v)
        }
        case scala.util.Success(None) => {
          kafka_producer.send(KafkaProducerRecord("updateOffer", s"""{"offer_id": $offer_id, "uid": ${offer.uid}, "product_id": ${offer.product_id}, "quality": ${offer.quality}, "merchant_id": "${merchant.merchant_id.get}", "amount": ${offer.amount}, "price": ${offer.price}, "shipping_time_standard": ${offer.shipping_time.standard}, "shipping_time_prime": ${offer.shipping_time.prime.getOrElse(0)}, "prime": ${offer.prime}, "signature": "${offer.signature.getOrElse("")}", "http_code": 404, "timestamp": "${new DateTime()}"}"""))
          Failure("item not found", 404)
        }
        case scala.util.Failure(e) => {
          kafka_producer.send(KafkaProducerRecord("updateOffer", s"""{"offer_id": $offer_id, "uid": ${offer.uid}, "product_id": ${offer.product_id}, "quality": ${offer.quality}, "merchant_id": "${merchant.merchant_id.get}", "amount": ${offer.amount}, "price": ${offer.price}, "shipping_time_standard": ${offer.shipping_time.standard}, "shipping_time_prime": ${offer.shipping_time.prime.getOrElse(0)}, "prime": ${offer.prime}, "signature": "${offer.signature.getOrElse("")}", "http_code": 500, "timestamp": "${new DateTime()}"}"""))
          Failure(e.getMessage, 500)
        }
      }
    } else {
      kafka_producer.send(KafkaProducerRecord("updateOffer", s"""{"offer_id": $offer_id, "uid": ${offer.uid}, "product_id": ${offer.product_id}, "quality": ${offer.quality}, "merchant_id": "${merchant.merchant_id.get}", "amount": ${offer.amount}, "price": ${offer.price}, "shipping_time_standard": ${offer.shipping_time.standard}, "shipping_time_prime": ${offer.shipping_time.prime.getOrElse(0)}, "prime": ${offer.prime}, "signature": "${offer.signature.getOrElse("")}", "http_code": 451, "timestamp": "${new DateTime()}"}"""))
      Failure("Invalid signature", 451)
    }
  }

  def restockOffer(offer_id: Long, amount: Int, signature: String, merchant: Merchant): Result[Offer] = {
    var offerOption: Option[Offer] = None
    DatabaseStore.getOffer(offer_id) match {
      case Success(offerFound) => {
        offerOption = Some(offerFound)
      }
    }
    val offer = offerOption.get

    val validSignature: Boolean = ProducerConnector.validSignature(offer.uid, amount, signature, merchant.merchant_id.getOrElse(""))

    if (validSignature) {
      val res = Try {
        DB localTx { implicit session =>
          sql"UPDATE offers SET amount = amount + $amount WHERE offer_id = $offer_id".executeUpdate().apply()
          sql"""SELECT offer_id, uid, product_id, quality, merchant_id, amount, price, shipping_time_standard, shipping_time_prime, prime
        FROM offers
        WHERE offer_id = $offer_id AND merchant_id = ${merchant.merchant_id.get}"""
            .map(rs => Offer(rs)).list.apply().headOption
        }
      }
      res match {
        case scala.util.Success(Some(v)) => {
          kafka_producer.send(KafkaProducerRecord("restockOffer", s"""{"offer_id": $offer_id, "amount": $amount, "signature": "$signature", "merchant_id": ${merchant.merchant_id.get}, "http_code": 200, "timestamp": "${new DateTime()}"}"""))
          Success(v)
        }
        case scala.util.Success(None) => {
          kafka_producer.send(KafkaProducerRecord("restockOffer", s"""{"offer_id": $offer_id, "amount": $amount, "signature": "$signature", "merchant_id": ${merchant.merchant_id.get}, "http_code": 404, "timestamp": "${new DateTime()}"}"""))
          Failure("item not found", 404)
        }
        case scala.util.Failure(e) => {
          kafka_producer.send(KafkaProducerRecord("restockOffer", s"""{"offer_id": $offer_id, "amount": $amount, "signature": "$signature", "merchant_id": ${merchant.merchant_id.get}, "http_code": 417, "timestamp": "${new DateTime()}"}"""))
          Failure(e.getMessage, 417)
        }
      }
    } else {
      kafka_producer.send(KafkaProducerRecord("restockOffer", s"""{"offer_id": $offer_id, "amount": $amount, "signature": "$signature", "merchant_id": ${merchant.merchant_id.get}, "http_code": 451, "timestamp": "${new DateTime()}"}"""))
      Failure("Invalid signature", 451)
    }
  }

  def logCurrentMarketSituation(product_id: Long, trigger: String = "unknown", merchant_id: String = "unknown") = {
    val res = Try(DB localTx { implicit session =>
      sql"""SELECT offer_id, uid, product_id, quality, merchant_id, amount, price, shipping_time_standard, shipping_time_prime, prime
        FROM offers
        WHERE product_id = $product_id AND amount > 0""".map(rs => Offer(rs)).list.apply()
    })
    res match {
      case scala.util.Success(list) => {
        if (list.nonEmpty) {
          val buf = new StringBuilder
          buf ++= s"""{"timestamp": "${new DateTime()}", "trigger": "$trigger", "merchant_id": "$merchant_id", "product_id": $product_id, "offers": {"""
          list.foreach(offer => {
            buf ++= s""""${offer.merchant_id.get}": {"offer_id": ${offer.offer_id.get}, "uid": ${offer.uid}, "product_id": ${offer.product_id}, "quality": ${offer.quality}, "merchant_id": "${offer.merchant_id.get}", "amount": ${offer.amount}, "price": ${offer.price}, "shipping_time_standard": ${offer.shipping_time.standard}, "shipping_time_prime": ${offer.shipping_time.prime.getOrElse(0)}, "prime": ${offer.prime}"""
            if (offer != list.last) {
              buf ++= s"""}, """
            }
          })
          buf ++= s"""}}}"""
          kafka_producer.send(KafkaProducerRecord("marketSituation", buf.toString))
        }
      }
      case scala.util.Failure(e) => {
        println("Unable to fetch current market situation for uid $uid")
      }
    }
  }

  def addMerchant(merchant: Merchant): Result[Merchant] = {
    val res = Try(DB localTx { implicit session =>
      sql"""WITH token AS (SELECT random_string(64) AS value),
             token_hash AS (SELECT encode(digest(token.value, 'sha256'), 'base64') AS value FROM token)
         INSERT INTO merchants SELECT
           token_hash.value,
           token.value,
           ${merchant.api_endpoint_url},
           ${merchant.merchant_name},
           ${merchant.algorithm_name}
         FROM token_hash, token
         RETURNING merchant_id, merchant_token, api_endpoint_url, merchant_name, algorithm_name""".map(rs => Merchant(rs)).list.apply().headOption.get
    })
    res match {
      case scala.util.Success(created_merchant) => {
        kafka_producer.send(KafkaProducerRecord("addMerchant", s"""{"merchant_id": ${created_merchant.merchant_id.get} "api_endpoint_url": ${merchant.api_endpoint_url}, "merchant_name": ${merchant.merchant_name}, "algorithm_name": ${merchant.algorithm_name}, "http_code": 200, "timestamp": "${new DateTime()}"}"""))
        Success(merchant.copy(merchant_id = created_merchant.merchant_id, merchant_token = created_merchant.merchant_token))
      }
      case scala.util.Failure(e) => {
        kafka_producer.send(KafkaProducerRecord("addMerchant", s"""{"api_endpoint_url": ${merchant.api_endpoint_url}, "merchant_name": ${merchant.merchant_name}, "algorithm_name": ${merchant.algorithm_name}, "http_code": 500, "timestamp": "${new DateTime()}"}"""))
        Failure(e.getMessage, 500)
      }
    }
  }
  // UPDATE table SET user='$user', name='$name' where id ='$id'"
  def updateMerchant(token: String, merchant: Merchant): Result[Merchant] = {
    val res = Try(DB localTx { implicit session =>
      sql"""UPDATE merchants
        SET
          api_endpoint_url = ${merchant.api_endpoint_url},
          merchant_name = ${merchant.merchant_name},
          algorithm_name = ${merchant.algorithm_name}
        WHERE merchant_token = ${token}
        RETURNING merchant_id, merchant_token, api_endpoint_url, merchant_name, algorithm_name""".map(rs => Merchant(rs)).list.apply().headOption.get
    })
    res match {
      case scala.util.Success(created_merchant) => {
        kafka_producer.send(KafkaProducerRecord("updateMerchant", s"""{"merchant_id": ${created_merchant.merchant_id.get} "api_endpoint_url": ${merchant.api_endpoint_url}, "merchant_name": ${merchant.merchant_name}, "algorithm_name": ${merchant.algorithm_name}, "http_code": 200, "timestamp": "${new DateTime()}"}"""))
        Success(merchant.copy(merchant_id = created_merchant.merchant_id, merchant_token = created_merchant.merchant_token))
      }
      case scala.util.Failure(e) => {
        kafka_producer.send(KafkaProducerRecord("updateMerchant", s"""{"api_endpoint_url": ${merchant.api_endpoint_url}, "merchant_name": ${merchant.merchant_name}, "algorithm_name": ${merchant.algorithm_name}, "http_code": 500, "timestamp": "${new DateTime()}"}"""))
        Failure(e.getMessage, 500)
      }
    }
  }

  def deleteMerchant(delete_parameter: String, delete_with_token: Boolean = true): Result[Unit] = {
    var sql = sql""
    if (delete_with_token) {
      sql = sql"DELETE FROM merchants WHERE merchant_token = $delete_parameter RETURNING merchant_id, NULL AS merchant_token, api_endpoint_url, merchant_name, algorithm_name"
    } else {
      sql = sql"DELETE FROM merchants WHERE merchant_id = $delete_parameter RETURNING merchant_id, NULL AS merchant_token, api_endpoint_url, merchant_name, algorithm_name"
    }
    val res = Try(DB localTx { implicit session =>
      sql.map(rs => Merchant(rs)).list.apply().headOption.get.merchant_id.get
    })
    res match {
      case scala.util.Success(id) if id.length > 0 => {
        kafka_producer.send(KafkaProducerRecord("deleteMerchant", s"""{"merchant_id": "$id", "http_code": 200, "timestamp": "${new DateTime()}"}"""))
        Success((): Unit)
      }
      case scala.util.Success(id) if id.length == 0 => {
        if (delete_with_token) {
          kafka_producer.send(KafkaProducerRecord("deleteMerchant", s"""{"merchant_token": "$delete_parameter", "http_code": 404, "timestamp": "${new DateTime()}"}"""))
          Failure(s"No merchant with merchant_token $delete_parameter", 404)
        } else {
          kafka_producer.send(KafkaProducerRecord("deleteMerchant", s"""{"merchant_id": "$delete_parameter", "http_code": 404, "timestamp": "${new DateTime()}"}"""))
          Failure(s"No merchant with merchant_id $delete_parameter", 404)
        }
      }
      case scala.util.Failure(e) => {
        if (delete_with_token) {
          kafka_producer.send(KafkaProducerRecord("deleteMerchant", s"""{"merchant_token": "$delete_parameter", "http_code": 500, "timestamp": "${new DateTime()}"}"""))
          Failure(e.getMessage, 500)
        } else {
          kafka_producer.send(KafkaProducerRecord("deleteMerchant", s"""{"merchant_id": "$delete_parameter", "http_code": 500, "timestamp": "${new DateTime()}"}"""))
          Failure(e.getMessage, 500)
        }
      }
    }
  }

  def getMerchants: Result[Seq[Merchant]] = {
    val res = Try(DB readOnly { implicit session =>
      sql"SELECT merchant_id, api_endpoint_url, merchant_name, algorithm_name, NULL as merchant_token FROM merchants"
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

  def getMerchantByToken(token: String): Result[Merchant] = {
    val dbResult = Result(DB readOnly { implicit session => {
      val sqlQuery =
        sql"""SELECT merchant_id, api_endpoint_url, merchant_name, algorithm_name, NULL AS merchant_token
             FROM merchants
             WHERE merchant_token = $token
           """
      sqlQuery.map(rs => Merchant(rs)).list.apply().headOption
    }})
    dbResult.flatMap {
      case Some(merchant) => Success(merchant)
      case None => Failure("Not authorized!", 401)
    }
  }

  def getMerchant(search_parameter: String, search_with_token: Boolean = false): Result[Merchant] = {
    val res = Try(DB readOnly { implicit session =>
      var sql_query = sql""
      if (!search_with_token) {
        sql_query =
          sql"""SELECT merchant_id, api_endpoint_url, merchant_name, algorithm_name, NULL AS merchant_token
          FROM merchants
          WHERE merchant_id = $search_parameter"""
      } else {
        sql_query =
          sql"""SELECT merchant_id, api_endpoint_url, merchant_name, algorithm_name, NULL AS merchant_token
          FROM merchants
          WHERE merchant_token = $search_parameter"""
      }
      sql_query.map(rs => Merchant(rs)).list.apply().headOption
    })
    res match {
      case scala.util.Success(Some(v)) => {
        kafka_producer.send(KafkaProducerRecord("getMerchant", s"""{"merchant_id": "${v.merchant_id.get}", "http_code": 200, "timestamp": "${new DateTime()}"}"""))
        Success(v)
      }
      case scala.util.Success(None) => {
        if (!search_with_token) {
          kafka_producer.send(KafkaProducerRecord("getMerchant", s"""{"merchant_id": "$search_parameter", "http_code": 404, "timestamp": "${new DateTime()}"}"""))
          Failure(s"No merchant with key $search_parameter found", 404)
        } else {
          kafka_producer.send(KafkaProducerRecord("getMerchant", s"""{"merchant_token": "$search_parameter", "http_code": 404, "timestamp": "${new DateTime()}"}"""))
          Failure(s"No merchant with token $search_parameter found", 404)
        }
      }
      case scala.util.Failure(e) => {
        if (!search_with_token) {
          kafka_producer.send(KafkaProducerRecord("getMerchant", s"""{"merchant_id": "$search_parameter", "http_code": 500, "timestamp": "${new DateTime()}"}"""))
          Failure(e.getMessage, 500)
        } else {
          kafka_producer.send(KafkaProducerRecord("getMerchant", s"""{"merchant_token": "$search_parameter", "http_code": 500, "timestamp": "${new DateTime()}"}"""))
          Failure(e.getMessage, 500)
        }
      }
    }
  }

  def addConsumer(consumer: Consumer): Result[Consumer] = {
    val dbResult = Result(DB readOnly { implicit session => {
      val sqlQuery =
        sql"""SELECT COUNT(api_endpoint_url) FROM consumers WHERE api_endpoint_url != ${consumer.api_endpoint_url};
           """
      sqlQuery.map(rs => rs.long(1)).single.apply().get
    }})
    val consumerLimit = config.getLong("consumer_limit")
    if (consumerLimit <= 0 || (dbResult.isSuccess && dbResult.get < consumerLimit)) {
      val res = Try(DB localTx { implicit session =>
        sql"""BEGIN;
      SELECT consumer_id INTO TEMPORARY TABLE existing_consumer FROM consumers WHERE api_endpoint_url = ${consumer.api_endpoint_url};
      DELETE FROM consumers WHERE consumer_id = (SELECT consumer_id FROM existing_consumer);
      WITH token AS (SELECT random_string(64) AS value),
             token_hash AS (SELECT encode(digest(token.value, 'sha256'), 'base64') AS value FROM token)
      INSERT INTO consumers SELECT token_hash.value,
           token.value, ${consumer.api_endpoint_url}, ${consumer.consumer_name}, ${consumer.description}
      FROM token_hash, token;
      DROP TABLE existing_consumer;
      COMMIT;""".update().apply()
        sql"SELECT consumer_id, consumer_token, api_endpoint_url, consumer_name, description FROM consumers WHERE api_endpoint_url = ${consumer.api_endpoint_url}".map(rs => Consumer(rs)).list.apply().headOption.get
      })
      res match {
        case scala.util.Success(created_consumer) => {
          kafka_producer.send(KafkaProducerRecord("addConsumer", s"""{"consumer_id": ${created_consumer.consumer_id.get}, "api_endpoint_url": ${consumer.api_endpoint_url}, "consumer_name": ${consumer.consumer_name}, "description": ${consumer.description}, "http_code": 200, "timestamp": "${new DateTime()}"}"""))
          Success(consumer.copy(consumer_id = created_consumer.consumer_id, consumer_token = created_consumer.consumer_token))
        }
        case scala.util.Failure(e) => {
          kafka_producer.send(KafkaProducerRecord("addConsumer", s"""{"api_endpoint_url": ${consumer.api_endpoint_url}, "consumer_name": ${consumer.consumer_name}, "description": ${consumer.description}, "http_code": 500, "timestamp": "${new DateTime()}"}"""))
          Failure(e.getMessage, 500)
        }
      }
    } else {
      kafka_producer.send(KafkaProducerRecord("addConsumer", s"""{"api_endpoint_url": ${consumer.api_endpoint_url}, "consumer_name": ${consumer.consumer_name}, "description": ${consumer.description}, "http_code": 451, "timestamp": "${new DateTime()}"}"""))
      Failure("consumer limit reached", 451)
    }
  }

  def deleteConsumer(delete_parameter: String, delete_with_token: Boolean = true): Result[Unit] = {
    var sql = sql""
    if (delete_with_token) {
      sql = sql"DELETE FROM consumers WHERE consumer_token = $delete_parameter RETURNING consumer_id, NULL AS consumer_token, api_endpoint_url, consumer_name, description"
    } else {
      sql = sql"DELETE FROM consumers WHERE consumer_id = $delete_parameter RETURNING consumer_id, NULL AS consumer_token, api_endpoint_url, consumer_name, description"
    }
    val res = Try(DB localTx { implicit session =>
      sql.map(rs => Consumer(rs)).list.apply().headOption.get.consumer_id.get
    })
    res match {
      case scala.util.Success(id) if id.length > 0 => {
        kafka_producer.send(KafkaProducerRecord("deleteConsumer", s"""{"consumer_id": "$id", "http_code": 200, "timestamp": "${new DateTime()}"}"""))
        Success((): Unit)
      }
      case scala.util.Success(id) if id.length == 0 => {
        if (delete_with_token) {
          kafka_producer.send(KafkaProducerRecord("deleteConsumer", s"""{"consumer_token": "$delete_parameter", "http_code": 404, "timestamp": "${new DateTime()}"}"""))
          Failure(s"No consumer with token $delete_parameter", 404)
        } else {
          kafka_producer.send(KafkaProducerRecord("deleteConsumer", s"""{"consumer_id": "$delete_parameter", "http_code": 404, "timestamp": "${new DateTime()}"}"""))
          Failure(s"No consumer with id $delete_parameter", 404)
        }
      }
      case scala.util.Failure(e) => {
        if (delete_with_token) {
          kafka_producer.send(KafkaProducerRecord("deleteConsumer", s"""{"consumer_token": "$delete_parameter", "http_code": 500, "timestamp": "${new DateTime()}"}"""))
          Failure(e.getMessage, 500)
        } else {
          kafka_producer.send(KafkaProducerRecord("deleteConsumer", s"""{"consumer_id": "$delete_parameter", "http_code": 500, "timestamp": "${new DateTime()}"}"""))
          Failure(e.getMessage, 500)
        }
      }
    }
  }

  def getConsumers: Result[Seq[Consumer]] = {
    val res = Try(DB readOnly { implicit session =>
      sql"SELECT consumer_id, api_endpoint_url, consumer_name, description, NULL AS consumer_token FROM consumers"
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

  def getConsumerByToken(token: String): Result[Consumer] = {
    val dbResult = Result(DB readOnly { implicit session =>
      val sqlQuery = sql"""SELECT consumer_id, api_endpoint_url, consumer_name, description, NULL AS consumer_token
          FROM consumers
          WHERE consumer_token = $token"""
      sqlQuery.map(rs => Consumer(rs)).list.apply().headOption
    })
    dbResult.flatMap {
      case Some(consumer) => Success(consumer)
      case None => Failure("Not authorized!", 401)
    }
  }

  def getConsumer(search_parameter: String, search_with_token: Boolean = false): Result[Consumer] = {
    val res = Try(DB readOnly { implicit session =>
      var sql_query = sql""
      if (!search_with_token) {
        sql_query = sql"""SELECT consumer_id, api_endpoint_url, consumer_name, description, NULL AS consumer_token
          FROM consumers
          WHERE consumer_id = $search_parameter"""
      } else {
        sql_query = sql"""SELECT consumer_id, api_endpoint_url, consumer_name, description, NULL AS consumer_token
          FROM consumers
          WHERE consumer_token = $search_parameter"""
      }
        sql_query.map(rs => Consumer(rs)).list.apply().headOption
    })
    res match {
      case scala.util.Success(Some(v)) => {
        kafka_producer.send(KafkaProducerRecord("getConsumer", s"""{"consumer_id": "${v.consumer_id.get}", "http_code": 200, "timestamp": "${new DateTime()}"}"""))
        Success(v)
      }
      case scala.util.Success(None) => {
        if (!search_with_token) {
          kafka_producer.send(KafkaProducerRecord("getConsumer", s"""{"consumer_id": "$search_parameter", "http_code": 404, "timestamp": "${new DateTime()}"}"""))
          Failure(s"No consumer with key $search_parameter found", 404)
        } else {
          kafka_producer.send(KafkaProducerRecord("getConsumer", s"""{"consumer_token": "$search_parameter", "http_code": 404, "timestamp": "${new DateTime()}"}"""))
          Failure(s"No consumer with token $search_parameter found", 404)
        }
      }
      case scala.util.Failure(e) => {
        if (!search_with_token) {
          kafka_producer.send(KafkaProducerRecord("getConsumer", s"""{"consumer_id": "$search_parameter", "http_code": 500, "timestamp": "${new DateTime()}"}"""))
          Failure(e.getMessage, 500)
        } else {
          kafka_producer.send(KafkaProducerRecord("getConsumer", s"""{"consumer_token": "$search_parameter", "http_code": 500, "timestamp": "${new DateTime()}"}"""))
          Failure(e.getMessage, 500)
        }
      }
    }
  }

  def addProduct(product: data.Product): Result[data.Product] = {
    val res = Try(DB localTx { implicit session =>
      sql"INSERT INTO products VALUES (DEFAULT, ${product.name}, ${product.genre})"
        .updateAndReturnGeneratedKey.apply()
    })
    res match {
      case scala.util.Success(id) => {
        kafka_producer.send(KafkaProducerRecord("addProduct", s"""{"product_id": $id, "name": "${product.name}", "genre": "${product.genre}", "http_code": 200, "timestamp": "${new DateTime()}"}"""))
        Success(product.copy(product_id = Some(id)))
      }
      case scala.util.Failure(e) => {
        kafka_producer.send(KafkaProducerRecord("addProduct", s"""{"name": "${product.name}", "genre": "${product.genre}", "http_code": 500, "timestamp": "${new DateTime()}"}"""))
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

  def getProducts: Result[Seq[data.Product]] = {
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

  def getProduct(product_id: Long): Result[data.Product] = {
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
}
