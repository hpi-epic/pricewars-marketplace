package de.hpi.epic.pricewars


import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

import akka.actor.ActorSystem
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import org.apache.commons.codec.binary.Base64
import spray.can.Http
import spray.http.HttpMethods._
import spray.http._
import spray.json._

import scala.concurrent.Await
import scala.concurrent.duration._

object ProducerConnector {
  implicit val system: ActorSystem = ActorSystem()
  implicit val timeout: Timeout = Timeout(15.seconds)

  def getProducerKey(producer_url: String): String = {
    val request = (IO(Http) ? HttpRequest(GET,
      Uri(producer_url + "/decryption_key")
    )).mapTo[HttpResponse]
    try {
      Await.result(request, Duration.Inf) match {
        case HttpResponse(status, entity, headers, protocol) =>
          val response_json = entity.asString.parseJson
          response_json.asJsObject.getFields("decryption_key") match {
            case Seq(JsString(decryption_key)) =>
              decryption_key
          }
      }
    } catch {
      case e: Exception => ""
    }
  }

  def validSignature(uid: Long, amount: Int, signature: String, decryption_key: String): Boolean = {
    return true
    val signature_bytes = Base64.decodeBase64(signature)
    val decryption_key_bytes = Base64.decodeBase64(decryption_key)
    val cipher: Cipher = Cipher.getInstance("AES/ECB/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(decryption_key_bytes, "AES"))
    val encrypted_signature = new String(cipher.doFinal(signature_bytes)).trim
    val producer_infos = encrypted_signature.split(" ")

    if (producer_infos{0}.toLong == uid && producer_infos{1}.toInt == amount) {
      val totalAmountUsed = DatabaseStore.getUsedAmountForSignature(signature) + amount

      if (totalAmountUsed <= producer_infos{1}.toInt) {
        DatabaseStore.setUsedAmountForSignature(signature, totalAmountUsed)
      } else {
        false
      }
    } else {
      false
    }
  }
}
