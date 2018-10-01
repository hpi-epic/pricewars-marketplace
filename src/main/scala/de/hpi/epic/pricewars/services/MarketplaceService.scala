package de.hpi.epic.pricewars.services

import akka.actor.{Actor, ActorContext, ActorLogging}
import akka.event.Logging
import de.hpi.epic.pricewars.CORSSupport
import de.hpi.epic.pricewars.utils.JSONConverter._
import de.hpi.epic.pricewars.connectors.ProducerConnector
import de.hpi.epic.pricewars.data._
import de.hpi.epic.pricewars.utils.ResultConverter._
import spray.http._
import spray.routing._


class MarketplaceServiceActor extends Actor with ActorLogging with MarketplaceService {
  override def actorRefFactory: ActorContext = context

  override def receive: Receive = runRoute(route)
}

trait MarketplaceService extends HttpService with CORSSupport {
  var defaultHoldingCostRate: BigDecimal = 0
  val route: Route = respondWithMediaType(MediaTypes.`application/json`) {
    logRequestResponse("marketplace", Logging.InfoLevel) {
      cors {
        path("offers") {
          get {
            optionalHeaderValueByName(HttpHeaders.Authorization.name) { authorizationHeader =>
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
              optionalHeaderValueByName(HttpHeaders.Authorization.name) { authorizationHeader =>
                entity(as[Offer]) { offer =>
                  detach() {
                    complete {
                      DatabaseStore
                        .getMerchantByToken(ValidateLimit.getTokenString(authorizationHeader).getOrElse(""))
                        .flatMap(merchant => DatabaseStore.addOffer(offer, merchant))
                        .successHttpCode(StatusCodes.Created)
                    }
                  }
                } ~
                  entity(as[Array[Offer]]) { offerArray =>
                    detach() {
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
            }
        } ~
          path("offers" / LongNumber) { id =>
            get {
              complete {
                DatabaseStore.getOffer(id)
              }
            } ~
              delete {
                optionalHeaderValueByName(HttpHeaders.Authorization.name) { authorizationHeader =>
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
                optionalHeaderValueByName(HttpHeaders.Authorization.name) { authorizationHeader =>
                  entity(as[Offer]) { offer =>
                    detach() {
                      complete {
                        ValidateLimit
                          .checkMerchant(authorizationHeader)
                          .flatMap(merchant => DatabaseStore.updateOffer(id, offer, merchant))
                      }
                    }
                  }
                }
              }
          } ~
          path("offers" / LongNumber / "buy") { id =>
            post {
              optionalHeaderValueByName(HttpHeaders.Authorization.name) { authorizationHeader =>
                entity(as[BuyRequest]) { buyRequest =>
                  detach() {
                    complete {
                      val token = ValidateLimit.getTokenString(authorizationHeader)
                      DatabaseStore.getConsumerByToken(token.getOrElse(""))
                        .flatMap(consumer => DatabaseStore.buyOffer(id, buyRequest.price, buyRequest.amount, consumer))
                        .successHttpCode(StatusCodes.NoContent)
                    }
                  }
                }
              }
            }
          } ~
          path("offers" / LongNumber / "restock") { id =>
            patch {
              optionalHeaderValueByName(HttpHeaders.Authorization.name) { authorizationHeader =>
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
              complete {
                DatabaseStore.getMerchants
              }
            } ~
              post {
                entity(as[Merchant]) { merchant =>
                  detach() {
                    complete {
                      DatabaseStore.addMerchant(merchant, defaultHoldingCostRate).successHttpCode(StatusCodes.Created)
                    }
                  }
                }
              }
          } ~
          path("merchants" / "token" / Rest) { token =>
            put {
              entity(as[Merchant]) { merchant =>
                detach() {
                  complete {
                    DatabaseStore.updateMerchant(token, merchant).successHttpCode(StatusCodes.OK)
                  }
                }
              }
            } ~
              delete {
                complete {
                  DatabaseStore.deleteMerchant(token).successHttpCode(StatusCodes.NoContent)
                }
              }
          } ~
          path("merchants" / Rest) { id =>
            get {
              complete {
                DatabaseStore.getMerchant(id)
              }
            } ~
              delete {
                optionalHeaderValueByName(HttpHeaders.Authorization.name) { authorizationHeader =>
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
              complete {
                DatabaseStore.getConsumers
              }
            } ~
              post {
                entity(as[Consumer]) { consumer =>
                  detach() {
                    complete {
                      DatabaseStore.addConsumer(consumer).successHttpCode(StatusCodes.Created)
                    }
                  }
                }
              }
          } ~
          path("consumers" / "token" / Rest) { token =>
            delete {
              complete {
                DatabaseStore.deleteConsumer(token).successHttpCode(StatusCodes.NoContent)
              }
            }
          } ~
          path("consumers" / Rest) { id =>
            get {
              complete {
                DatabaseStore.getConsumer(id)
              }
            } ~
              delete {
                optionalHeaderValueByName(HttpHeaders.Authorization.name) { authorizationHeader =>
                  complete {
                    val token = ValidateLimit.getTokenString(authorizationHeader)
                    DatabaseStore
                      .getConsumerByToken(token.getOrElse(""))
                      .flatMap(consumer => DatabaseStore.deleteConsumer(consumer.consumer_id.get, delete_with_token = false))
                      .successHttpCode(StatusCodes.NoContent)
                  }
                }
              }
          } ~
          path("products") {
            get {
              complete {
                DatabaseStore.getProducts
              }
            } ~
              post {
                entity(as[Product]) { product =>
                  detach() {
                    complete {
                      DatabaseStore.addProduct(product).successHttpCode(StatusCodes.Created)
                    }
                  }
                }
              }
          } ~
          path("products" / LongNumber) { id =>
            get {
              complete {
                DatabaseStore.getProduct(id)
              }
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
                  detach() {
                    complete {
                      ValidateLimit.setLimit(
                        settings.consumer_per_minute,
                        settings.max_updates_per_sale,
                        settings.max_req_per_sec)
                      StatusCode.int2StatusCode(200) -> s"""{}"""
                    }
                  }
                }
              }
          } ~
          path("holding_cost_rate" / Rest) { merchant_id =>
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
  }
}
