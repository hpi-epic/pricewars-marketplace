package de.hpi.epic.pricewars

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import de.hpi.epic.pricewars.services.{DatabaseStore, MarketplaceService}

object Server {
  def main(args: Array[String]) {

    implicit val system: ActorSystem = ActorSystem("marketplace")
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    DatabaseStore.setup()
    Http().bindAndHandle(MarketplaceService.route, "0.0.0.0", port = 8080)
  }
}
