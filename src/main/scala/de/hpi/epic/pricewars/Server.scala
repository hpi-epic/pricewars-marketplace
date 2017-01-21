package de.hpi.epic.pricewars

import akka.actor.{ActorSystem, Props}
import de.hpi.epic.pricewars.services.{DatabaseStore, MarketplaceServiceActor}
import spray.servlet.WebBoot

class Server extends WebBoot {
  implicit val system = ActorSystem("marketplace")
  val serviceActor = system.actorOf(Props[MarketplaceServiceActor])
  DatabaseStore.setup()
}
