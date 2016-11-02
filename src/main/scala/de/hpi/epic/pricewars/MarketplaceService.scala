package de.hpi.epic.pricewars

import akka.actor.Actor
import spray.json._
import spray.routing._
import spray.http._
import MediaTypes._

import JSONConverter._

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
        complete {
          println("get")
          val offer = Store.get()
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
    } ~
    path("offers" / LongNumber) { id =>
      get {
        complete {
          println("single get")
          //TODO: return single value, not Sequence
          val offer = Store.get(id)
          offer.toJson.toString()
        }
      } ~
      delete {
        complete {
          println("delete")
          val res = Store.remove(id)
          //TODO: are these the right return values?

          if (res) {
            "deleted"
          } else {
            StatusCodes.NotFound -> "Your either not allowed or object doesn't exist."
          }
        }
      }
    }
  }

}
