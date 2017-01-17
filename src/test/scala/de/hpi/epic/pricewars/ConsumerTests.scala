package de.hpi.epic.pricewars

import org.specs2.mutable.Specification
import spray.testkit.Specs2RouteTest
import spray.http._
import StatusCodes._
import de.hpi.epic.pricewars.utils.JSONConverter._
import de.hpi.epic.pricewars.data.Consumer
import de.hpi.epic.pricewars.services.{DatabaseStore, MarketplaceService}

/**
  * Created by sebastian on 15.11.16
  */
class ConsumerTests extends Specification with BeforeAfterEach with Specs2RouteTest with MarketplaceService {
  sequential

  def actorRefFactory = system

  private val consumers = Seq(
    Consumer("testvm1:8080", "testuser1", "algo", Some("hash1"), Some("token1")),
    Consumer("testvm2:8090", "testuser2", "rythm", Some("hash2"), Some("token2"))
  )

  override def before: Unit = {
    super.before
    DatabaseStore.addConsumer(consumers.head)
  }

  "The marketplace" should {

    "return a list containing one consumer on startup" in {
      Get("/consumers") ~> route ~> check {
        response.status should be equalTo OK
        response.entity should not be equalTo(None)
        responseAs[Seq[Consumer]] must be equalTo Seq(consumers.head)
      }
    }

    "register a new consumer" in {
      Post("/consumers", consumers(1)) ~> route ~> check {
        response.status should be equalTo Created
        response.entity should not be equalTo(None)
        responseAs[Consumer] must be equalTo consumers(1)
      }
      Get("/consumers") ~> route ~> check {
        response.status should be equalTo OK
        response.entity should not be equalTo(None)
        responseAs[Seq[Consumer]] must be equalTo consumers
      }
    }

    "get an existing consumer by id" in {
      Get("/consumers/1") ~> route  ~> check {
        response.status should be equalTo OK
        response.entity should not be equalTo(None)
        responseAs[Consumer] must be equalTo consumers.head
      }
    }

    "return Not Found error for a get on not existing consumer" in {
      Get("/consumers/3") ~> route  ~> check {
        response.status should be equalTo NotFound
        response.entity should not be equalTo(None)
      }
    }

    "return Not Found error for a delete on not existing consumer" in {
      Delete("/consumers/3") ~> route ~> check {
        response.status should be equalTo NotFound
        response.entity should not be equalTo(None)
      }
    }

    "delete an existing consumer by id" in {
      Delete("/consumers/1") ~> route ~> check {
        response.status should be equalTo NoContent
        response.entity should be equalTo None
      }
    }

    "return Not Found error for a delete on not existing consumers" in {
      Delete("/consumers/3") ~> route ~> check {
        response.status should be equalTo NotFound
        response.entity should not be equalTo(None)
      }
    }
  }
}