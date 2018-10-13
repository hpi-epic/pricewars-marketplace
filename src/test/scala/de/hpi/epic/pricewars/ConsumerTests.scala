package de.hpi.epic.pricewars

import akka.actor.ActorSystem
import akka.http.javadsl.model.headers.HttpCredentials
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.Specs2RouteTest
import org.specs2.mutable.SpecificationLike
import de.hpi.epic.pricewars.utils.JSONConverter._
import de.hpi.epic.pricewars.data.Consumer
import de.hpi.epic.pricewars.services.{DatabaseStore, MarketplaceService}

class ConsumerTests extends BeforeAfterEach with SpecificationLike with Specs2RouteTest {
  sequential

  def actorRefFactory: ActorSystem = system

  private val route = MarketplaceService.route

  private val consumers = Seq(
    Consumer("endpoint1:8080", "testuser1", "algo", None, None),
    Consumer("endpoint2:8090", "testuser2", "rythm", None, None)
  )

  override def before(): Unit = {
    super.before()
    DatabaseStore.addConsumer(consumers.head)
  }

  "The marketplace" should {

    "return a list containing one consumer on startup" in {
      Get("/consumers") ~> route ~> check {
        response.status shouldEqual StatusCodes.OK
        response.entity shouldNotEqual None
        val consumers = responseAs[Seq[Consumer]]
        consumers.length mustEqual 1
        consumers.head.api_endpoint_url mustEqual "endpoint1:8080"
        consumers.head.consumer_name mustEqual "testuser1"
        consumers.head.description mustEqual "algo"
        consumers.head.consumer_id mustNotEqual None
        consumers.head.consumer_token must beNone
      }
    }

    "register a new consumer" in {
      Post("/consumers", consumers(1)) ~> route ~> check {
        response.status shouldEqual StatusCodes.Created
        response.entity shouldNotEqual None
        val consumer = responseAs[Consumer]
        consumer.api_endpoint_url mustEqual "endpoint2:8090"
        consumer.consumer_name mustEqual "testuser2"
        consumer.description mustEqual "rythm"
        consumer.consumer_id mustNotEqual None
        consumer.consumer_token mustNotEqual None
      }
      Get("/consumers") ~> route ~> check {
        response.status shouldEqual StatusCodes.OK
        response.entity shouldNotEqual None
        val consumers = responseAs[Seq[Consumer]]
        consumers.length mustEqual 2
      }
    }

    "get an existing consumer by id" in {
      var consumer_id = ""
      Post("/consumers", consumers(1)) ~> route ~> check {
        response.status shouldEqual StatusCodes.Created
        response.entity shouldNotEqual None
        consumer_id = responseAs[Consumer].consumer_id.get
      }
      Get("/consumers/" + consumer_id) ~> route ~> check {
        response.status shouldEqual StatusCodes.OK
        response.entity shouldNotEqual None
        val consumer = responseAs[Consumer]
        consumer.api_endpoint_url mustEqual "endpoint2:8090"
        consumer.consumer_name mustEqual "testuser2"
        consumer.description mustEqual "rythm"
        consumer.consumer_id mustNotEqual consumer_id
        consumer.consumer_token must beNone
      }
    }

    "return Not Found error for a get on not existing consumer" in {
      Get("/consumers/nonExistingId") ~> route ~> check {
        response.status shouldEqual StatusCodes.NotFound
        response.entity shouldNotEqual None
      }
    }

    "return Unauthorized for a delete without a token" in {
      Delete("/consumers") ~> route ~> check {
        response.status shouldEqual StatusCodes.Unauthorized
        response.entity shouldNotEqual None
      }
    }

    "return Unauthorized for a delete with a wrong token" in {
      Delete("/consumers").addCredentials(HttpCredentials.create("token", "nonExistingToken")) ~> route ~> check {
        response.status shouldEqual StatusCodes.Unauthorized
        response.entity shouldNotEqual None
      }
    }
  }
}
