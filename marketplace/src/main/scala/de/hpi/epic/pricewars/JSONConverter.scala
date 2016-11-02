package de.hpi.epic.pricewars

import spray.httpx.SprayJsonSupport
import spray.json.DefaultJsonProtocol

/**
  * Created by Jan on 01.11.2016.
  */
object JSONConverter extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val offerFormat = jsonFormat7(Offer)
}
