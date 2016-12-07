package de.hpi.epic.pricewars

import akka.actor.{Props, ActorSystem}
import spray.servlet.WebBoot

class Server extends WebBoot {
  implicit val system = ActorSystem("marketplace")
  val serviceActor = system.actorOf(Props[MarketplaceServiceActor])
  DatabaseStore.setup()
}
