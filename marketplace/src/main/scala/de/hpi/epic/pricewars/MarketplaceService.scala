package de.hpi.epic.pricewars

import akka.actor.Actor
import spray.json._
import spray.routing._
import spray.http._
import MediaTypes._

import JSONConverter._
import ResultConverter._

class MarketplaceServiceActor extends Actor with MarketplaceService {
  override def actorRefFactory = context
  override def receive = runRoute(route)
}

/**
  * Created by Jan on 01.11.2016.
  */
trait MarketplaceService extends HttpService {
  val route = respondWithMediaType(MediaTypes.`application/json`) {
    path("offers") {
      get {
        complete {
          Store.get
        }
      } ~
      post {
        entity(as[Offer]) { offer =>
          detach() {
            complete {
              Store.add(offer)
            }
          }
        }
      }
    } ~
    path("offers" / LongNumber) { id =>
      get {
        complete {
          Store.get(id)
        }
      } ~
      delete {
        complete {
          val res = Store.remove(id)
          res match {
            case Success(v) => """{"result": "deleted"}"""
            case f : Failure[Unit] => StatusCode.int2StatusCode(f.code) -> f.toJson.toString()
          }
        }
      } ~
      put {
        entity(as[Offer]) { offer =>
          detach() {
            complete {
              Store.update(id, offer)
            }
          }
        }
      }
    }
  }

}
