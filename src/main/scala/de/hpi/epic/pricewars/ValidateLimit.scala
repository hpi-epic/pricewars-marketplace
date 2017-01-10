package de.hpi.epic.pricewars

import com.redis._
import com.typesafe.config.{Config, ConfigFactory}
import org.joda.time.DateTime
import spray.http.StatusCodes.ClientError
import spray.http.{StatusCode, StatusCodes}

object ValidateLimit {
  val timeToLiveSeconds = 100
  private val config: Config = ConfigFactory.load
  private val redis_hostname: String = config.getConfig("redis").getString("hostname")
  private val redis_port: Int = config.getConfig("redis").getInt("port")
  private val redis = new RedisClient(redis_hostname, redis_port)
  private var tick: Double = 10.0
  private var max_req_per_sec: Int = 100
  private var limitPerSecond: Double = tick / max_req_per_sec
  private var limit: Double = limitPerSecond * timeToLiveSeconds

  def setLimit(new_tick: Double, new_max_req_per_sec: Int): Double = {
    tick = new_tick
    max_req_per_sec = new_max_req_per_sec
    limitPerSecond = tick / max_req_per_sec
    limit = limitPerSecond * timeToLiveSeconds
    limit
  }

  def checkMerchant(AuthorizationHeader: Option[String]): (Option[Merchant], StatusCode) = {
    val token = getTokenString(AuthorizationHeader)
    if (check(token)) {
      getMerchantFromToken(token.get) match {
        case Some(merchant) => (Some(merchant), StatusCodes.OK)
        case _ => (None, StatusCodes.Unauthorized)
      }
    } else {
      (None, StatusCodes.TooManyRequests)
    }
  }

  def checkConsumer(AuthorizationHeader: Option[String]): (Option[Consumer], StatusCode) = {
    val token = getTokenString(AuthorizationHeader)
    if (check(token)) {
      getConsumerFromToken(token.get) match {
        case Some(consumer) => (Some(consumer), StatusCodes.OK)
        case _ => (None, StatusCodes.Unauthorized)
      }
    } else {
      (None, StatusCodes.TooManyRequests)
    }
  }

  def getMerchantFromToken(token: String): Option[Merchant] = {
    DatabaseStore.getMerchant(token, search_with_token = true) match {
      case Success(merchant) => Some(merchant)
      case _ => None
    }
  }

  def getConsumerFromToken(token: String): Option[Consumer] = {
    DatabaseStore.getConsumer(token, search_with_token = true) match {
      case Success(consumer) => Some(consumer)
      case _ => None
    }
  }

  def getTokenString(AuthorizationHeader: Option[String]): Option[String] = {
    if (AuthorizationHeader.isEmpty) {
      return None
    }

    val headerValue: String = AuthorizationHeader.get
    val values = headerValue.split(" ")

    if (values.length == 2 && values{0} == "Token") {
      Some(values{1})
    } else {
      None
    }
  }

  private def check(tokenOption: Option[String]): Boolean = {
    if (tokenOption.isEmpty) {
      return false
    }

    val token = tokenOption.get

    if (count(token) < limit) {
      addEntry(token)
    } else {
      false
    }
  }

  private def addEntry(key: String): Boolean = {
    val redis_key = key + "---" + new DateTime()
    redis.setex(redis_key, timeToLiveSeconds, 1)
  }

  private def count(key: String): Int = {
    val redis_key = key + "*"
    redis.keys(redis_key)
    redis.keys(redis_key) match {
      case Some(s: List[Option[String]]) => s.size
      case None => 0
    }
  }
}
