package de.hpi.epic.pricewars

import org.specs2.mutable.Specification
import spray.testkit.Specs2RouteTest
import spray.http._
import StatusCodes._
import JSONConverter._

/**
  * Created by Jan on 02.11.2016.
  */
class OfferTests extends Specification with BeforeAfterEach with Specs2RouteTest with MarketplaceService {
  sequential

  def actorRefFactory = system

  private val offers = Seq(
    Offer(Some(1), 0, 0, 10, 1, 5, 12000000.0f, ShippingTime(100), false, ""),
    Offer(Some(2), 0, 1, 10, 2, 100, 0, ShippingTime(2, Some(1)), true, "")
  )

  private val merchants = Seq(
    Merchant("testvm1:8080", "testuser1", "algo", Some(1)),
    Merchant("testvm2:8090", "testuser2", "rythm", Some(2))
  )

  override def before: Unit = {
    super.before
    DatabaseStore.addMerchant(merchants.head)
    DatabaseStore.addMerchant(merchants(1))
    DatabaseStore.addOffer(offers.head)
  }

  "The marketplace" should {

    "return a list containing one offer on startup" in {
      Get("/offers") ~> route ~> check {
        response.status should be equalTo OK
        response.entity should not be equalTo(None)
        responseAs[Seq[Offer]] must be equalTo Seq(offers.head)
      }
    }

    "insert a new offer" in {
      Post("/offers", offers(1)) ~> route ~> check {
        response.status should be equalTo Created
        response.entity should not be equalTo(None)
        responseAs[Offer] must be equalTo offers(1)
      }
      Get("/offers") ~> route ~> check {
        response.status should be equalTo OK
        response.entity should not be equalTo(None)
        responseAs[Seq[Offer]] must be equalTo offers
      }
    }

    "get an existing offer by id" in {
      Get("/offers/1") ~> route  ~> check {
        response.status should be equalTo OK
        response.entity should not be equalTo(None)
        responseAs[Offer] must be equalTo offers.head
      }
    }

    "return Not Found error for a get on not existing offer" in {
      Get("/offers/3") ~> route  ~> check {
        response.status should be equalTo NotFound
        response.entity should not be equalTo(None)
      }
    }

    "update an existing offer" in {
      val updated = offers.head.copy(price = 10)
      Put("/offers/1", updated) ~> route ~> check {
        response.status should be equalTo OK
        response.entity should not be equalTo(None)
        responseAs[Offer] must be equalTo updated
      }
      Get("/offers/1") ~> route ~> check {
        response.status should be equalTo OK
        response.entity should not be equalTo(None)
        responseAs[Offer] must be equalTo updated
      }
    }

    "return Not Found error for a put on not existing offer" in {
      val updated = offers.head.copy(price = 10)
      Put("/offers/3", updated) ~> route ~> check {
        response.status should be equalTo NotFound
        response.entity should not be equalTo(None)
      }
    }

    "delete an existing offer by id" in {
      Delete("/offers/1") ~> route ~> check {
        response.status should be equalTo NoContent
        response.entity should be equalTo None
      }
    }

    "return Not Found error for a delete on not existing offer" in {
      Delete("/offers/3") ~> route ~> check {
        response.status should be equalTo NotFound
        response.entity should not be equalTo(None)
      }
    }
  }
}
