package de.hpi.epic.pricewars

import org.specs2.mutable.Specification
import spray.testkit.Specs2RouteTest
import spray.http._
import StatusCodes._
import de.hpi.epic.pricewars.utils.JSONConverter._
import de.hpi.epic.pricewars.data.{Merchant, Offer, ShippingTime}
import de.hpi.epic.pricewars.services.{DatabaseStore, MarketplaceService}

/**
  * Created by Jan on 02.11.2016.
  */
class OfferTests extends Specification with BeforeAfterEach with Specs2RouteTest with MarketplaceService {
  sequential

  def actorRefFactory = system

  private val offers = Seq(
    Offer(Some(1), 0, 0, 10, Some("hash1"), 5, 12000000.0f, ShippingTime(100), false, Some("")),
    Offer(Some(2), 0, 1, 10, Some("hash2"), 100, 0, ShippingTime(2, Some(1)), true, Some(""))
  )

  private val merchants = Seq(
    Merchant("testvm1:8080", "testuser1", "algo", Some("token1"), Some("hash1")),
    Merchant("testvm2:8090", "testuser2", "rythm", Some("token2"), Some("hash2"))
  )

  override def before: Unit = {
    super.before
    DatabaseStore.addMerchant(merchants.head)
    DatabaseStore.addMerchant(merchants(1))
    DatabaseStore.addOffer(offers.head, merchants.head)
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
