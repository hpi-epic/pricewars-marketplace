package de.hpi.epic.pricewars

import akka.actor.ActorSystem
import akka.http.javadsl.model.headers.HttpCredentials
import akka.http.scaladsl.testkit.Specs2RouteTest
import akka.http.scaladsl.model.StatusCodes
import org.specs2.mutable.SpecificationLike
import de.hpi.epic.pricewars.data.{Merchant, Offer, ShippingTime}
import de.hpi.epic.pricewars.services.{DatabaseStore, MarketplaceService}
import de.hpi.epic.pricewars.utils.JSONConverter._

class OfferTests extends BeforeAfterEach with SpecificationLike with Specs2RouteTest {
  sequential

  private val route = MarketplaceService.route

  private val offers = Seq(
    Offer(Some(1), 0, 0, 3, Some("merchant1_id"), 15, 12.4, ShippingTime(100), prime=false, Some("signature1")),
    Offer(Some(2), 0, 1, 3, Some("merchant2_id"), 10, 13.7, ShippingTime(2, Some(1)), prime=true, Some("signature2"))
  )
  private val merchants = Seq(
    Merchant("merchant1_endpoint:8080", "testuser1", "algo", None, None),
    Merchant("merchant2_endpoint:8090", "testuser2", "rythm", None, None)
  )

  private var merchant1_token = ""
  private var merchant2_token = ""

  def actorRefFactory: ActorSystem = system

  override def before(): Unit = {
    super.before()
    merchant1_token = DatabaseStore.addMerchant(merchants.head).get.merchant_token.get
    merchant2_token = DatabaseStore.addMerchant(merchants(1)).get.merchant_token.get
  }

  "The marketplace" should {

    "return an empty list of offers" in {
      Get("/offers") ~> route ~> check {
        response.status shouldEqual StatusCodes.OK
        response.entity shouldNotEqual None
        responseAs[Seq[Offer]].length mustEqual 0
      }
    }

    "insert a new offer" in {
      Post("/offers", offers.head) ~> route ~> check {
        response.status shouldEqual StatusCodes.Created
        response.entity shouldNotEqual None
        responseAs[Offer] must be equalTo offers(1)
      }
      Get("/offers") ~> route ~> check {
        response.status shouldEqual StatusCodes.OK
        response.entity shouldNotEqual None
        responseAs[Seq[Offer]] must be equalTo offers
      }
    }.pendingUntilFixed("Need to test with a producer key for signature validation")

    "get an existing offer by id" in {
      Get("/offers/1") ~> route  ~> check {
        response.status shouldEqual StatusCodes.OK
        response.entity shouldNotEqual None
        responseAs[Offer] must be equalTo offers.head
      }
    }.pendingUntilFixed("Need to test with a producer key for signature validation")

    "return Not Found error for a get on not existing offer" in {
      Get("/offers/123") ~> route  ~> check {
        response.status shouldEqual StatusCodes.NotFound
        response.entity shouldNotEqual None
      }
    }

    "update an existing offer" in {
      val updated = offers.head.copy(price = 10)
      Put("/offers/1", updated) ~> route ~> check {
        response.status shouldEqual StatusCodes.OK
        response.entity shouldNotEqual None
        responseAs[Offer] must be equalTo updated
      }
      Get("/offers/1") ~> route ~> check {
        response.status shouldEqual StatusCodes.OK
        response.entity shouldNotEqual None
        responseAs[Offer] must be equalTo updated
      }
    }.pendingUntilFixed("Need to test with a producer key for signature validation")

    "return Unauthorized error for a put without authorization header" in {
      Put("/offers/123", offers.head) ~> route ~> check {
        response.status shouldEqual StatusCodes.Unauthorized
        response.entity shouldNotEqual None
      }
    }

    "return Unauthorized error for a put with invalid authorization token" in {
      Put("/offers/123", offers.head).addCredentials(HttpCredentials.create("token", "invalidToken")) ~> route ~> check {
        response.status shouldEqual StatusCodes.Unauthorized
        response.entity shouldNotEqual None
      }
    }

    "return Not Found error for a put on not existing offer" in {
      val updated = offers.head.copy(price = 10)
      Put("/offers/123", updated).addCredentials(HttpCredentials.create("token", merchant1_token)) ~> route ~> check {
        response.status shouldEqual StatusCodes.NotFound
        response.entity shouldNotEqual None
      }
    }

    "delete an existing offer by id" in {
      Delete("/offers/1") ~> route ~> check {
        response.status shouldEqual StatusCodes.NoContent
        response.entity shouldNotEqual None
      }
    }.pendingUntilFixed("Need to test with a producer key for signature validation")

    "return Not Found error for a delete on not existing offer" in {
      Delete("/offers/123").addCredentials(HttpCredentials.create("token", merchant1_token)) ~> route ~> check {
        response.status shouldEqual StatusCodes.NotFound
        response.entity shouldNotEqual None
      }
    }.pendingUntilFixed("Need to test with a producer key for signature validation")
  }
}
