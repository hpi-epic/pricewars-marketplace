package de.hpi.epic.pricewars

import akka.actor.{Props, ActorSystem}
import spray.servlet.WebBoot

/**
  * Created by Jan on 01.11.2016.
  */
class Server extends WebBoot {
  implicit val system = ActorSystem("marketplace")
  val serviceActor = system.actorOf(Props[MarketplaceServiceActor])
  DatabaseStore.setup()
}
