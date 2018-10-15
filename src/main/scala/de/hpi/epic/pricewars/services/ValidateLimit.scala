package de.hpi.epic.pricewars.services

import com.redis._
import com.typesafe.config.{Config, ConfigFactory}
import de.hpi.epic.pricewars.data.{Consumer, Merchant}
import de.hpi.epic.pricewars.utils.{Failure, Result, Success}
import rx.{Rx, Var}
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.Authorization

object ValidateLimit {
  val precisionFactor = 100
  val dateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_INSTANT
  private val config: Config = ConfigFactory.load
  private val redis_hostname: String = config.getConfig("redis").getString("hostname")
  private val redis_port: Int = config.getConfig("redis").getInt("port")
  private def redis: RedisClient = new RedisClient(redis_hostname, redis_port)

  private val tick = Var(1.0)
  private val consumer_per_minute = Var(30.0)
  private val max_updates_per_sale = Var(100.0)
  private val max_req_per_sec = Var(0.5)
  private val timeToLiveRedisKey = Rx { (tick() * precisionFactor).asInstanceOf[Long] }
  private val limit = Rx { max_req_per_sec() * precisionFactor }
  /*
    Short description of validation checking: we assume $tick-long ticks (in seconds, defaults to 1s ticks).
    Our goal is fine-granular request limits (e.g., per second), but still allowing rather "slow settings"
    (e.g., allowing 20 requests per minute). To accurately tracking such request limits <1/s,
    we multiply both the time-to-live for Redis as well as the given limit (accessible via the `/config` route)
    with $precisionFactor (defaults to 100). Thus, with the default settings and a request limit of 0.5 requests/s,
    we check whether we have seen more than 50 requests (0.5 * 100) with a time-to-live of 100s (1.0s * 100).
  */

  def getTick: Double = {
    tick.now
  }

  def getConsumerPerMinute: Double = {
    consumer_per_minute.now
  }

  def getMaxUpdatesPerSale: Double = {
    max_updates_per_sale.now
  }

  def getMaxReqPerSec: Double = {
    max_req_per_sec.now
  }

  def setLimit(consumer_per_minute: Double, max_updates_per_sale: Double, max_req_per_sec: Double): Double = {
    this.consumer_per_minute() = consumer_per_minute
    this.max_updates_per_sale() = max_updates_per_sale
    this.max_req_per_sec() = max_req_per_sec
    limit.now
  }

  def checkMerchant(AuthorizationHeader: Option[Authorization]): Result[Merchant] = {
    val tokenOption = getTokenString(AuthorizationHeader)
    check(tokenOption) flatMap ( token => {
      DatabaseStore.getMerchantByToken(token)
    })
  }

  def checkConsumer(AuthorizationHeader: Option[Authorization]): Result[Consumer] = {
    val tokenOption = getTokenString(AuthorizationHeader)
    check(tokenOption) flatMap ( token => {
      DatabaseStore.getConsumerByToken(token)
    })
  }

  def getTokenString(AuthorizationHeader: Option[Authorization]): Option[String] = {
    AuthorizationHeader match {
      case Some(authHeader) => Some(authHeader.credentials.token())
      case None => None
    }
  }

  private def check(tokenOption: Option[String]): Result[String] = {
    tokenOption match {
      case None => Failure("Not authorized! Cause: Empty authentication token.", StatusCodes.Unauthorized.intValue)
      case Some(token) =>
        if (count(token) < limit.now && addEntry(token)) Success(token)
        else Failure("API request limit reached!", StatusCodes.TooManyRequests.intValue)
    }
  }

  private def addEntry(key: String): Boolean = {
    val redis_key = key + "---" + ZonedDateTime.now().format(dateFormatter)
    redis.setex(redis_key, timeToLiveRedisKey.now, 1)
  }

  private def count(key: String): Int = {
    val redis_key = key + "*"
    redis.keys(redis_key) match {
      case Some(s: List[Option[String]]) => s.size
      case None => 0
    }
  }
}
