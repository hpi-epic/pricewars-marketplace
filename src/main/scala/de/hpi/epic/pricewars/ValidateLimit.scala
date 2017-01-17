package de.hpi.epic.pricewars

import com.redis._
import com.typesafe.config.{Config, ConfigFactory}
import org.joda.time.DateTime
import rx.{Rx, Var}
import spray.http.StatusCodes.ClientError
import spray.http.{StatusCode, StatusCodes}

object ValidateLimit {
  val timeToLiveSeconds = 100
  private val config: Config = ConfigFactory.load
  private val redis_hostname: String = config.getConfig("redis").getString("hostname")
  private val redis_port: Int = config.getConfig("redis").getInt("port")
  private val redis = new RedisClient(redis_hostname, redis_port)

  private val tick = Var(10.0)
  private val max_req_per_sec = Var(100)
  private val limitPerSecond = Rx { max_req_per_sec() / tick() }
  private val limit = Rx { limitPerSecond() + timeToLiveSeconds }

  def getTick: Double = {
    tick.now
  }

  def getMaxReqPerSec: Int = {
    max_req_per_sec.now
  }

  def setLimit(new_tick: Double, new_max_req_per_sec: Int): Double = {
    tick() = new_tick
    max_req_per_sec() = new_max_req_per_sec
    limit.now
  }

  def checkMerchant(AuthorizationHeader: Option[String]): Result[Merchant] = {
    val tokenOption = getTokenString(AuthorizationHeader)
    check(tokenOption) flatMap ( token => {
      DatabaseStore.getMerchantByToken(token)
    })
  }

  def checkConsumer(AuthorizationHeader: Option[String]): Result[Consumer] = {
    val tokenOption = getTokenString(AuthorizationHeader)
    check(tokenOption) flatMap ( token => {
      DatabaseStore.getConsumerByToken(token)
    })
  }

  def getTokenString(AuthorizationHeader: Option[String]): Option[String] = {
    AuthorizationHeader.flatMap( headerValue => {
      val values = headerValue.split(" ")
      if (values.length == 2 && values{0} == "Token") Some(values{1})
      else None
    })
  }

  private def check(tokenOption: Option[String]): Result[String] = {
    tokenOption match {
      case None => Failure("Not authorized! Cause: Empty authentification token.", StatusCodes.Unauthorized.intValue)
      case Some(token) =>
        if (count(token) < limit.now && addEntry(token)) Success(token)
        else Failure("API request limit reached!", StatusCodes.TooManyRequests.intValue)
    }
  }

  private def addEntry(key: String): Boolean = {
    val redis_key = key + "---" + new DateTime()
    redis.setex(redis_key, timeToLiveSeconds, 1)
  }

  private def count(key: String): Int = {
    val redis_key = key + "*"
    redis.keys(redis_key) match {
      case Some(s: List[Option[String]]) => s.size
      case None => 0
    }
  }
}
