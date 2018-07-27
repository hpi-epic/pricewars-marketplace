package de.hpi.epic.pricewars.connectors

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

import akka.actor.ActorSystem
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}
import de.hpi.epic.pricewars.services.DatabaseStore
import org.apache.commons.codec.binary.Base64
import org.joda.time.{DateTime, Minutes}
import spray.can.Http
import spray.http.HttpMethods._
import spray.http._
import spray.json._

import scala.concurrent.Await
import scala.concurrent.duration._

import de.hpi.epic.pricewars.data.Signature

object ProducerConnector {
  val config: Config = ConfigFactory.load

  val producer_url: String = config.getString("producer_url")
  var producer_key: Option[String] = None

  implicit val system: ActorSystem = ActorSystem()
  implicit val timeout: Timeout = Timeout(15.seconds)

  def getProducerKey(url: String = producer_url): Option[String] = {
    val request = (IO(Http) ? HttpRequest(GET, Uri(url + "/decryption_key"))).mapTo[HttpResponse]
    try {
      Await.result(request, Duration.Inf) match {
        case HttpResponse(status, entity, headers, protocol) =>
          val response_json = entity.asString.parseJson
          response_json.asJsObject.getFields("decryption_key") match {
            case Seq(JsString(decryption_key)) =>
              println("Producer Key updated!: " + decryption_key)
              Some(decryption_key)
          }
      }
    } catch {
      case e: Exception => None
    }
  }

  def parseSignature(encrypted_signature: String): Option[Signature] = {
    // A valid signature consists of: <product_uid> <amount> <merchant_id> <timestamp>
    if (producer_key.isEmpty) {
      producer_key = getProducerKey()
    }

    if (encrypted_signature.length == 0 || producer_key.isEmpty) {
      return None
    }

    val signature_bytes = Base64.decodeBase64(encrypted_signature)
    val decryption_key_bytes = Base64.decodeBase64(producer_key.get)
    val cipher: Cipher = Cipher.getInstance("AES/ECB/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(decryption_key_bytes, "AES"))
    val decrypted_signature = new String(cipher.doFinal(signature_bytes)).trim
    val signature_content = decrypted_signature.split(" ")

    if (signature_content.length != 4) {
      return None
    }

    try {
      return Some(Signature(signature_content{0}.toLong, signature_content{1}.toInt, signature_content{2}))
    } catch {
      case e: java.lang.NumberFormatException => {
        println("Signature invalid format")
        println(e)
      }
    }
    None
  }
}
