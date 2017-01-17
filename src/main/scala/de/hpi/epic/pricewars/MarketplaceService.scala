package de.hpi.epic.pricewars

import akka.actor.{Actor, ActorContext, ActorLogging}
import akka.event.Logging
import de.hpi.epic.pricewars.JSONConverter._
import de.hpi.epic.pricewars.ResultConverter._
import spray.http._
import spray.json._
import spray.routing._

class MarketplaceServiceActor extends Actor with ActorLogging with MarketplaceService {
  override def actorRefFactory: ActorContext = context

  override def receive: Receive = runRoute(route)
}

trait MarketplaceService extends HttpService with CORSSupport {
  val route: Route = respondWithMediaType(MediaTypes.`application/json`) {
    logRequestResponse("marketplace", Logging.InfoLevel) {
      cors {
        path("offers") {
          get {
            parameter('product_id.as[Long] ?) { product_id =>
              complete {
                DatabaseStore.getOffers(product_id)
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
                        .flatMap( merchant => DatabaseStore.addOffer(offer, merchant))
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
                          statusCode -> s"""{"error": "Not authorized or API request limit reached! Status Code: $statusCode"}"""
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
                  complete {
                    ValidateLimit
                      .checkMerchant(authorizationHeader)
                      .flatMap(merchant => DatabaseStore.deleteOffer(id, merchant))
                      .successHttpCode(StatusCodes.NoContent)
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
                      ValidateLimit
                        .checkConsumer(authorizationHeader)
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
                      DatabaseStore.addMerchant(merchant).successHttpCode(StatusCodes.Created)
                    }
                  }
                }
              }
          } ~
          path("merchants" / "token" / Rest) { token =>
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
                    .flatMap( merchant => {
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
                val res = DatabaseStore.deleteConsumer(token)
                res match {
                  case Success(_) => StatusCodes.NoContent
                  case f: Failure[Unit] => StatusCode.int2StatusCode(f.code) -> f.toJson.toString()
                }
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
                  val consumer = DatabaseStore.getConsumerByToken(token.get).get
                  val res = DatabaseStore.deleteConsumer(consumer.consumer_id.get, delete_with_token = false)
                  res match {
                    case Success(_) => StatusCodes.NoContent
                    case f: Failure[Unit] => StatusCode.int2StatusCode(f.code) -> f.toJson.toString()
                  }
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
                  val res = DatabaseStore.deleteProduct(id)
                  res match {
                    case Success(v) => StatusCodes.NoContent
                    case f: Failure[Unit] => StatusCode.int2StatusCode(f.code) -> f.toJson.toString()
                  }
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
                StatusCodes.OK -> s"""{"tick": "${ValidateLimit.getTick}", "max_req_per_sec": "${ValidateLimit.getMaxReqPerSec}"}"""
              }
            } ~
              put {
                entity(as[Settings]) { settings =>
                  detach() {
                    complete {
                      ValidateLimit.setLimit(settings.tick, settings.max_req_per_sec)
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
