package de.hpi.epic.pricewars

import org.specs2.mutable.Specification
import spray.testkit.Specs2RouteTest
import spray.http._
import StatusCodes._
import JSONConverter._

/**
  * Created by Jan on 02.11.2016.
  */
class OfferTests extends Specification with BeforeAfterAll with Specs2RouteTest with MarketplaceService {
  sequential

  def actorRefFactory = system

  private val offers = Seq(
    Offer(Some(1), "Hana", "SAP", 5, 12000000.0f, 100, false),
    Offer(Some(2), "MySQL", "Oracle", 100, 0, 1, true)
  )

  def beforeAll() {
    OfferStore.add(offers(0))
  }

  def afterAll(): Unit = {
    OfferStore.clear()
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
        response.entity should not be equalTo(None)
        responseAs[String] must be equalTo """{"result": "deleted"}"""
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
