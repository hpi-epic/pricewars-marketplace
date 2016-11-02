package de.hpi.epic.pricewars

import org.specs2.mutable.Specification
import spray.testkit.Specs2RouteTest
import spray.http._
import StatusCodes._
import JSONConverter._

/**
  * Created by Jan on 02.11.2016.
  */
class MerchantTests extends Specification with BeforeAfterAll with Specs2RouteTest with MarketplaceService {
  sequential

  def actorRefFactory = system

  private val merchants = Seq(
    Merchant("testvm1:8080", "testuser1", "algo", Some("1")),
    Merchant("testvm2:8090", "testuser2", "rythm", Some("2"))
  )

  def beforeAll() {
    MerchantStore.add(merchants(0))
  }

  def afterAll(): Unit = {
    MerchantStore.clear()
  }

  "The marketplace" should {

    "return a list containing one offer on startup" in {
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

  }
}