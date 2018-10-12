package de.hpi.epic.pricewars
/*
import akka.actor.ActorSystem
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
    Merchant("merchant1_endpoint:8080", "testuser1", "algo", Some("hash1"), Some("token1")),
    Merchant("merchant2_endpoint:8090", "testuser2", "rythm", Some("hash2"), Some("token2"))
  )

  def actorRefFactory: ActorSystem = system

  override def before(): Unit = {
    super.before()
    DatabaseStore.addMerchant(merchants.head)
    DatabaseStore.addMerchant(merchants(1))
    println("add offer")
    DatabaseStore.addOffer(offers.head, merchants.head)
    println("added offer")
  }

  "The marketplace" should {

    "return a list containing one offer on startup" in {
      Get("/offers") ~> route ~> check {
        response.status shouldEqual StatusCodes.OK
        response.entity shouldNotEqual None
        responseAs[Seq[Offer]] mustEqual Seq(offers.head)
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
*/
