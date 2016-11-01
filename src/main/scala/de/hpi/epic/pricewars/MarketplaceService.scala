package de.hpi.epic.pricewars

import akka.actor.Actor
import spray.json._
import spray.routing._
import spray.http._
import MediaTypes._

import OfferJsonProtocol._

class MarketplaceServiceActor extends Actor with MarketplaceService {
  override def actorRefFactory = context
  override def receive = runRoute(route)
}

/**
  * Created by Jan on 01.11.2016.
  */
trait MarketplaceService extends HttpService {
  val route = respondWithMediaType(MediaTypes.`application/json`) {
    path("") {
      get {
        respondWithMediaType(`text/html`)
        complete {
          <html>
            <body>
              <h1>This is our Marketplace!</h1>
            </body>
          </html>
        }
      }
    } ~
    path("offers") {
      get {
        val offer = Store.get()
        complete {
          offer.toJson.toString()
        }
      } ~
      post {
        entity(as[Offer]) { offer =>
          detach() {
            complete {
              Store.add(offer).toString()
            }
          }
        }
      }
    }
  }

}
