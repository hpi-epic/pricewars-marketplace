package de.hpi.epic.pricewars.connectors

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.commons.codec.binary.Base64
import spray.json._

import scala.concurrent.{Await, ExecutionContextExecutor}
import scala.concurrent.duration._
import de.hpi.epic.pricewars.data.Signature


object ProducerConnector {
  val config: Config = ConfigFactory.load

  val producer_url: String = config.getString("producer_url")
  var producer_key: Option[String] = None

  implicit val system: ActorSystem = ActorSystem()
  // needed for onComplete
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher
  // used for Unmarshal
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  def getProducerKey(url: String = producer_url): Option[String] = {
    val responseFuture = Http().singleRequest(HttpRequest(uri = url + "/decryption_key"))
    Await.result(responseFuture, 1.second) match {
      case response @ HttpResponse(StatusCodes.OK, _, _, _) =>
        Await.result(Unmarshal(response.entity).to[String].map { jsonString =>
          jsonString.parseJson.asJsObject.getFields("decryption_key") match {
            case Seq(JsString(decryption_key)) => Some(decryption_key)
            case _ => None
          }
        }, 1.second)
      case _ => None
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
