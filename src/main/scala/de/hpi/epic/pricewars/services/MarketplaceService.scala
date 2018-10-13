package de.hpi.epic.pricewars.services

import scala.language.postfixOps
import akka.event.Logging
import akka.http.scaladsl.marshalling.Marshaller
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.DebuggingDirectives
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.headers.Authorization
import de.hpi.epic.pricewars.utils.JSONConverter._
import de.hpi.epic.pricewars.connectors.ProducerConnector
import de.hpi.epic.pricewars.data._
import de.hpi.epic.pricewars.utils.ResultConverter._


object MarketplaceService {
  //DebuggingDirectives.logRequestResult("logging_test_test", Logging.InfoLevel)

  var defaultHoldingCostRate: BigDecimal = 0

  val route: Route = {
    path("offers") {
      get {
        optionalHeaderValueByType(classOf[Authorization]) { authorizationHeader =>
          parameter('product_id.as[Long] ?) { product_id =>
            parameter('include_empty_offer.as[Boolean] ?) { include_empty_offer =>
              complete {
                if (include_empty_offer.getOrElse(false)) {
                  val merchant = DatabaseStore.getMerchantByToken(ValidateLimit.getTokenString(authorizationHeader).getOrElse(""))
                  if (merchant.isSuccess) {
                    DatabaseStore.getOffers(product_id, merchant.get.merchant_id)
                  } else {
                    DatabaseStore.getOffers(product_id, None)
                  }
                } else {
                  DatabaseStore.getOffers(product_id, None)
                }
              }
            }
          }
        }
      } ~
        post {
          optionalHeaderValueByType(classOf[Authorization]) { authorizationHeader =>
            entity(as[Offer]) { offer =>
              complete {
                DatabaseStore
                  .getMerchantByToken(ValidateLimit.getTokenString(authorizationHeader).getOrElse(""))
                  .flatMap(merchant => DatabaseStore.addOffer(offer, merchant))
                  .successHttpCode(StatusCodes.Created)
              }
            } ~
              entity(as[Array[Offer]]) { offerArray =>
                complete {
                  //TODO: refactor this with map
                  val merchant = DatabaseStore.getMerchantByToken(
                    ValidateLimit.getTokenString(authorizationHeader).getOrElse("")
                  )
                  val statusCode = StatusCodes.Unauthorized
                  if (merchant.isSuccess) {
                    val (bulkResult, status) = DatabaseStore.addBulkOffers(offerArray, merchant.get)
                    bulkResult.successHttpCode(status)
                  } else {
                    statusCode -> s"""{"error": "Not authorized! Status Code: $statusCode"}"""
                  }
                }
              }
          }
        }
    } ~
      path("offers" / LongNumber) { id =>
        get {
          complete {
            DatabaseStore.getOffer(id)
          }
        } ~
          delete {
            optionalHeaderValueByType(classOf[Authorization]) { authorizationHeader =>
              entity(as[EncryptedSignature]) { signature =>
                complete {
                  ValidateLimit
                    .checkMerchant(authorizationHeader)
                    .flatMap(merchant => DatabaseStore.deleteOffer(id, merchant, signature))
                }
              }
            }
          } ~
          put {
            optionalHeaderValueByType(classOf[Authorization]) { authorizationHeader =>
              entity(as[Offer]) { offer =>
                complete {
                  ValidateLimit
                    .checkMerchant(authorizationHeader)
                    .flatMap(merchant => DatabaseStore.updateOffer(id, offer, merchant))
                }
              }
            }
          }
      } ~
      path("offers" / LongNumber / "buy") { id =>
        post {
          optionalHeaderValueByType(classOf[Authorization]) { authorizationHeader =>
            entity(as[BuyRequest]) { buyRequest =>
              complete {
                val token = ValidateLimit.getTokenString(authorizationHeader)
                DatabaseStore.getConsumerByToken(token.getOrElse(""))
                  .flatMap(consumer => DatabaseStore.buyOffer(id, buyRequest.price, buyRequest.amount, consumer))
                  .successHttpCode(StatusCodes.NoContent)
              }
            }
          }
        }
      } ~
      path("offers" / LongNumber / "restock") { id =>
        patch {
          optionalHeaderValueByType(classOf[Authorization]) { authorizationHeader =>
            entity(as[OfferPatch]) { offer =>
              complete {
                DatabaseStore
                  .getMerchantByToken(ValidateLimit.getTokenString(authorizationHeader).getOrElse(""))
                  .flatMap(merchant =>
                    DatabaseStore.restockOffer(id, offer.amount.getOrElse(0), offer.signature.getOrElse(""), merchant)
                  )
              }
            }
          }
        }
      } ~
      path("merchants") {
        get {
          val result = DatabaseStore.getMerchants
          complete(StatusCode.int2StatusCode(result.code),
            HttpEntity(ContentTypes.`application/json`, result.toHttpResponseString))
        } ~
          post {
            entity(as[Merchant]) { merchant =>
              val result = DatabaseStore.addMerchant(merchant, defaultHoldingCostRate)
              complete(StatusCode.int2StatusCode(result.code),
                HttpEntity(ContentTypes.`application/json`, result.toHttpResponseString))
            }
          }
      } ~
      path("merchants" / "token" / Remaining) { token =>
        put {
          entity(as[Merchant]) { merchant =>
            complete {
              DatabaseStore.updateMerchant(token, merchant).successHttpCode(StatusCodes.OK)
            }
          }
        } ~
          delete {
            complete {
              DatabaseStore.deleteMerchant(token).successHttpCode(StatusCodes.NoContent)
            }
          }
      } ~
      // TODO: Instead of route merchants/<id> use route merchants/ and get the id from the authorization header
      path("merchants" / Remaining) { id =>
        get {
          val result = DatabaseStore.getMerchant(id)
          complete(StatusCode.int2StatusCode(result.code),
            HttpEntity(ContentTypes.`application/json`, result.toHttpResponseString))
        } ~
          delete {
            optionalHeaderValueByType(classOf[Authorization]) { authorizationHeader =>
              complete {
                val token = ValidateLimit.getTokenString(authorizationHeader)
                DatabaseStore
                  .getMerchantByToken(token.getOrElse(""))
                  .flatMap(merchant => {
                    DatabaseStore.deleteMerchant(merchant.merchant_id.get, delete_with_token = false)
                  })
                  .successHttpCode(StatusCodes.NoContent)
              }
            }
          }
      } ~
      path("consumers") {
        get {
          val result = DatabaseStore.getConsumers
          complete(StatusCode.int2StatusCode(result.code),
            HttpEntity(ContentTypes.`application/json`, result.toHttpResponseString))
        } ~
          post {
            entity(as[Consumer]) { consumer =>
              val result = DatabaseStore.addConsumer(consumer)
              complete(StatusCode.int2StatusCode(result.code),
                HttpEntity(ContentTypes.`application/json`, result.toHttpResponseString))
            }
          } ~
          delete {
            optionalHeaderValueByType(classOf[Authorization]) { authorizationHeader =>
              complete {
                val token = ValidateLimit.getTokenString(authorizationHeader)
                DatabaseStore
                  .getConsumerByToken(token.getOrElse(""))
                  .flatMap(consumer => DatabaseStore.deleteConsumer(consumer.consumer_id.get))
                  .successHttpCode(StatusCodes.NoContent)
              }
            }
          }
      } ~
      path("consumers" / Remaining) { id =>
        get {
          val result = DatabaseStore.getConsumer(id)
          complete(StatusCode.int2StatusCode(result.code),
            HttpEntity(ContentTypes.`application/json`, result.toHttpResponseString))
        }
      } ~
      path("products") {
        get {
          complete(HttpEntity(ContentTypes.`application/json`, DatabaseStore.getProducts.toHttpResponseString))
        } ~
          post {
            entity(as[Product]) { product =>
              complete(StatusCodes.Created,
                HttpEntity(ContentTypes.`application/json`, DatabaseStore.addProduct(product).toHttpResponseString))
            }
          }
      } ~
      path("products" / LongNumber) { id =>
        get {
          val result = DatabaseStore.getProduct(id)
          complete(StatusCode.int2StatusCode(result.code),
            HttpEntity(ContentTypes.`application/json`, result.toHttpResponseString))
        } ~
          delete {
            complete {
              DatabaseStore.deleteProduct(id).successHttpCode(StatusCodes.NoContent)
            }
          }
      } ~
      path("producer" / "key") {
        put {
          complete {
            val old_key = ProducerConnector.producer_key.getOrElse("")
            ProducerConnector.producer_key = ProducerConnector.getProducerKey()
            if (ProducerConnector.producer_key.isDefined) {
              StatusCode.int2StatusCode(200) -> s"""{"old producer key": "$old_key", "new producer key": "${ProducerConnector.producer_key.getOrElse("")}"}"""
            }
            else {
              StatusCode.int2StatusCode(500) -> s"""{"old producer key": "$old_key", "new producer key": "${ProducerConnector.producer_key.getOrElse("")}"}"""
            }
          }
        }
      } ~
      path("config") {
        get {
          complete {
            StatusCodes.OK ->
              s"""{
                  "consumer_per_minute": ${ValidateLimit.getConsumerPerMinute},
                  "max_updates_per_sale": ${ValidateLimit.getMaxUpdatesPerSale},
                  "max_req_per_sec": ${ValidateLimit.getMaxReqPerSec}
                }"""
          }
        } ~
          put {
            entity(as[Settings]) { settings =>
              complete {
                ValidateLimit.setLimit(
                  settings.consumer_per_minute,
                  settings.max_updates_per_sale,
                  settings.max_req_per_sec)
                StatusCode.int2StatusCode(200) -> s"""{}"""
              }
            }
          }
      } ~
      path("holding_cost_rate" / Remaining) { merchant_id =>
        get {
          complete {
            DatabaseStore.getHoldingCostRate(merchant_id)
          }
        }
      } ~
      path("holding_cost_rate") {
        put {
          entity(as[HoldingCostRate]) { holdingCostRate =>
            complete {
              holdingCostRate.merchant_id match {
                case Some(id) =>
                  DatabaseStore.changeHoldingCostRate(holdingCostRate.rate, id)
                case None =>
                  defaultHoldingCostRate = holdingCostRate.rate
                  StatusCode.int2StatusCode(200) -> s"""{}"""
              }
            }
          }
        }
      }
  }
}
