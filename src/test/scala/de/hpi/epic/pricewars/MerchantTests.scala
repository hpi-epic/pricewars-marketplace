package de.hpi.epic.pricewars

import org.specs2.mutable.Specification
import spray.testkit.Specs2RouteTest
import spray.http._
import StatusCodes._
import JSONConverter._

/**
  * Created by Jan on 02.11.2016.
  */
class MerchantTests extends Specification with BeforeAfterEach with Specs2RouteTest with MarketplaceService {
  sequential

  def actorRefFactory = system

  private val merchants = Seq(
    Merchant("testvm1:8080", "testuser1", "algo", Some(1)),
    Merchant("testvm2:8090", "testuser2", "rythm", Some(2))
  )

  override def before: Unit = {
    super.before
    DatabaseStore.addMerchant(merchants.head)
  }

  "The marketplace" should {

    "return a list containing one merchant on startup" in {
      Get("/merchants") ~> route ~> check {
        response.status should be equalTo OK
        response.entity should not be equalTo(None)
        responseAs[Seq[Merchant]] must be equalTo Seq(merchants.head)
      }
    }

    "register a new merchant" in {
      Post("/merchants", merchants(1)) ~> route ~> check {
        response.status should be equalTo Created
        response.entity should not be equalTo(None)
        responseAs[Merchant] must be equalTo merchants(1)
      }
      Get("/merchants") ~> route ~> check {
        response.status should be equalTo OK
        response.entity should not be equalTo(None)
        responseAs[Seq[Merchant]] must be equalTo merchants
      }
    }

    "get an existing merchant by id" in {
      Get("/merchants/1") ~> route  ~> check {
        response.status should be equalTo OK
        response.entity should not be equalTo(None)
        responseAs[Merchant] must be equalTo merchants.head
      }
    }

    "return Not Found error for a get on not existing merchant" in {
      Get("/merchants/3") ~> route  ~> check {
        response.status should be equalTo NotFound
        response.entity should not be equalTo(None)
      }
    }

    "return Not Found error for a delete on not existing merchant" in {
      Delete("/merchants/3") ~> route ~> check {
        response.status should be equalTo NotFound
        response.entity should not be equalTo(None)
      }
    }

    "delete an existing merchant by id" in {
      Delete("/merchants/1") ~> route ~> check {
        response.status should be equalTo NoContent
        response.entity should be equalTo None
      }
    }

    "return Not Found error for a delete on not existing merchants" in {
      Delete("/merchants/3") ~> route ~> check {
        response.status should be equalTo NotFound
        response.entity should not be equalTo(None)
      }
    }
  }
}