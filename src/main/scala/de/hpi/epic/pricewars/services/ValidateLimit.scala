package de.hpi.epic.pricewars.services

import com.redis._
import com.typesafe.config.{Config, ConfigFactory}
import de.hpi.epic.pricewars.data.{Consumer, Merchant}
import de.hpi.epic.pricewars.utils.{Failure, Result, Success}
import org.joda.time.DateTime
import rx.{Rx, Var}
import spray.http.StatusCodes

object ValidateLimit {
  val precisionFactor = 100
  private val config: Config = ConfigFactory.load
  private val redis_hostname: String = config.getConfig("redis").getString("hostname")
  private val redis_port: Int = config.getConfig("redis").getInt("port")
  private def redis: RedisClient = new RedisClient(redis_hostname, redis_port)

  private val tick = Var(1.0)
  private val consumer_per_minute = Var(30.0)
  private val max_updates_per_sale = Var(20.0)
  private val max_req_per_sec = Var(10.0)
  private val timeToLiveRedisKey = Rx { (tick() * precisionFactor).asInstanceOf[Long] }
  private val limit = Rx { max_req_per_sec() * precisionFactor }

  def getTick: Double = {
    tick.now
  }

  def getConsumerPerMinute: Double = {
    consumer_per_minute.now
  }

  def getMaxUpdatesPerSale: Double = {
    max_updates_per_sale.now
  }

  def getMaxReqPerSec: Int = {
    max_req_per_sec.now
  }

  def setLimit(consumer_per_minute: Double, max_updates_per_sale: Double, new_max_req_per_sec: Double): Double = {
    consumer_per_minute() = consumer_per_minute
    max_updates_per_sale() = max_updates_per_sale
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
      case None => Failure("Not authorized! Cause: Empty authentication token.", StatusCodes.Unauthorized.intValue)
      case Some(token) =>
        if (count(token) < limit.now && addEntry(token)) Success(token)
        else Failure("API request limit reached!", StatusCodes.TooManyRequests.intValue)
    }
  }

  private def addEntry(key: String): Boolean = {
    val redis_key = key + "---" + new DateTime()
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
