package de.hpi.epic.pricewars

import akka.actor.{Actor, ActorContext, ActorLogging}
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
            entity(as[Offer]) { offer =>
              detach() {
                complete {
                  DatabaseStore.addOffer(offer).successHttpCode(StatusCodes.Created)
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
              complete {
                val res = DatabaseStore.deleteOffer(id)
                res match {
                  case Success(v) => StatusCodes.NoContent
                  case f: Failure[Unit] => StatusCode.int2StatusCode(f.code) -> f.toJson.toString()
                }
              }
            } ~
            put {
              entity(as[Offer]) { offer =>
                detach() {
                  complete {
                    DatabaseStore.updateOffer(id, offer)
                  }
                }
              }
            }
        } ~
        path("offers" / LongNumber / "buy") { id =>
          post {
            entity(as[BuyRequest]) { buyRequest =>
              detach() {
                complete {
                  DatabaseStore.buyOffer(id, buyRequest.price, buyRequest.amount).successHttpCode(StatusCodes.NoContent)
                }
              }
            }
          }
        } ~
        path("offers" / LongNumber / "restock") { id =>
          patch {
            entity(as[OfferPatch]) { offer =>
              complete {
                DatabaseStore.restockOffer(id, offer.amount.getOrElse(0), offer.signature.getOrElse(""))
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
        path("merchants" / LongNumber) { id =>
          get {
            complete {
              DatabaseStore.getMerchant(id)
            }
          } ~
            delete {
              complete {
                val res = DatabaseStore.deleteMerchant(id)
                res match {
                  case Success(v) => StatusCodes.NoContent
                  case f: Failure[Unit] => StatusCode.int2StatusCode(f.code) -> f.toJson.toString()
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
        path("consumers" / LongNumber) { id =>
          get {
            complete {
              DatabaseStore.getConsumer(id)
            }
          } ~
            delete {
              complete {
                val res = DatabaseStore.deleteConsumer(id)
                res match {
                  case Success(v) => StatusCodes.NoContent
                  case f: Failure[Unit] => StatusCode.int2StatusCode(f.code) -> f.toJson.toString()
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
        }
    }
  }
}
