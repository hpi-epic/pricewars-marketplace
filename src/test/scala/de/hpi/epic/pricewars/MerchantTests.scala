package de.hpi.epic.pricewars

import akka.actor.ActorSystem
import akka.http.javadsl.model.headers.HttpCredentials
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.Specs2RouteTest
import org.specs2.mutable.SpecificationLike
import de.hpi.epic.pricewars.utils.JSONConverter._
import de.hpi.epic.pricewars.data.Merchant
import de.hpi.epic.pricewars.services.{DatabaseStore, MarketplaceService}

class MerchantTests extends BeforeAfterEach with SpecificationLike with Specs2RouteTest {
  sequential

  def actorRefFactory: ActorSystem = system

  private val route = MarketplaceService.route

  private val merchants = Seq(
    Merchant("endpoint1:8080", "merchant1", "lala", "#000000", None, None),
    Merchant("endpoint2:8090", "merchant2", "blub", "#0F0000", None, None)
  )

  override def before(): Unit = {
    super.before()
    DatabaseStore.addMerchant(merchants.head)
  }

  "The marketplace" should {

    "return a list containing one merchant on startup" in {
      Get("/merchants") ~> route ~> check {
        response.status shouldEqual StatusCodes.OK
        response.entity shouldNotEqual None
        val merchants = responseAs[Seq[Merchant]]
        merchants.length mustEqual 1
        merchants.head.api_endpoint_url mustEqual "endpoint1:8080"
        merchants.head.merchant_name mustEqual "merchant1"
        merchants.head.algorithm_name mustEqual "lala"
        merchants.head.color mustEqual "#000000"
        merchants.head.merchant_id mustNotEqual None
        merchants.head.merchant_token must beNone
      }
    }

    "register a new merchant" in {
      Post("/merchants", merchants(1)) ~> route ~> check {
        response.status shouldEqual StatusCodes.Created
        response.entity shouldNotEqual None
        val merchant = responseAs[Merchant]
        merchant.api_endpoint_url mustEqual "endpoint2:8090"
        merchant.merchant_name mustEqual "merchant2"
        merchant.algorithm_name mustEqual "blub"
        merchant.color mustEqual "#0F0000"
        merchant.merchant_id mustNotEqual None
        merchant.merchant_token mustNotEqual None
      }
      Get("/merchants") ~> route ~> check {
        response.status shouldEqual StatusCodes.OK
        response.entity shouldNotEqual None
        responseAs[Seq[Merchant]].length mustEqual 2
      }
    }

    "get an existing merchant by id" in {
      var merchant_id = ""
      Post("/merchants", merchants(1)) ~> route ~> check {
        response.status shouldEqual StatusCodes.Created
        response.entity shouldNotEqual None
        merchant_id = responseAs[Merchant].merchant_id.get
      }
      Get("/merchants/" + merchant_id) ~> route ~> check {
        response.status shouldEqual StatusCodes.OK
        response.entity shouldNotEqual None
        val merchant = responseAs[Merchant]
        merchant.api_endpoint_url mustEqual "endpoint2:8090"
        merchant.merchant_name mustEqual "merchant2"
        merchant.algorithm_name mustEqual "blub"
        merchant.color mustEqual "#0F0000"
        merchant.merchant_id.get mustEqual merchant_id
        merchant.merchant_token must beNone
      }
    }

    "return Not Found error for a get on not existing merchant" in {
      Get("/merchants/nonExistingId") ~> route ~> check {
        response.status shouldEqual StatusCodes.NotFound
        response.entity shouldNotEqual None
      }
    }

    "return Unauthorized error for a delete without authorization header" in {
      Delete("/merchants/anyId") ~> route ~> check {
        response.status shouldEqual StatusCodes.Unauthorized
        response.entity shouldNotEqual None
      }
    }

    "delete an existing merchant by id" in {
      var merchant_id = ""
      var token = ""
      Post("/merchants", merchants(1)) ~> route ~> check {
        response.status shouldEqual StatusCodes.Created
        response.entity shouldNotEqual None
        merchant_id = responseAs[Merchant].merchant_id.get
        token = responseAs[Merchant].merchant_token.get
      }
      Delete("/merchants/" + merchant_id)
        .addCredentials(HttpCredentials.create("token", token)) ~> route ~> check {
        response.status shouldEqual StatusCodes.NoContent
        response.entity shouldNotEqual None
      }
    }
  }
}
