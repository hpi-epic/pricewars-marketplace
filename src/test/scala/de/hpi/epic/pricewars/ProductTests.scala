package de.hpi.epic.pricewars

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.Specs2RouteTest
import org.specs2.mutable.SpecificationLike
import de.hpi.epic.pricewars.data.Product
import de.hpi.epic.pricewars.services.{DatabaseStore, MarketplaceService}
import de.hpi.epic.pricewars.utils.JSONConverter._


class ProductTests extends BeforeAfterEach with SpecificationLike with Specs2RouteTest {
  sequential

  def actorRefFactory: ActorSystem = system

  private val route = MarketplaceService.route

  private val products = Seq(
    Product(Some(1), "CD 1", "Genre 1"),
    Product(Some(2), "CD 2", "Genre 2")
  )

  override def before(): Unit = {
    super.before()
    DatabaseStore.addProduct(products.head)
  }

  "The marketplace" should {
    "return a list containing one product on startup" in {
      Get("/products") ~> route ~> check {
        response.status shouldEqual StatusCodes.OK
        response.entity shouldNotEqual None
        responseAs[Seq[Product]] mustEqual Seq(products.head)
      }
    }

    "register a new product" in {
      Post("/products", products(1)) ~> route ~> check {
        response.status shouldEqual StatusCodes.Created
        response.entity shouldNotEqual None
        responseAs[Product] must be equalTo products(1)
      }
      Get("/products") ~> route ~> check {
        response.status shouldEqual StatusCodes.OK
        response.entity shouldNotEqual None
        responseAs[Seq[Product]] must be equalTo products
      }
    }

    "get an existing product by id" in {
      Get("/products/1") ~> route  ~> check {
        response.status shouldEqual StatusCodes.OK
        response.entity shouldNotEqual None
        responseAs[Product] must be equalTo products.head
      }
    }

    "return Not Found error for a get on not existing product" in {
      Get("/products/3") ~> route  ~> check {
        response.status shouldEqual StatusCodes.NotFound
        response.entity shouldNotEqual None
      }
    }

    "return Not Found error for a delete on not existing product" in {
      Delete("/products/3") ~> route ~> check {
        response.status shouldEqual StatusCodes.NotFound
        response.entity shouldNotEqual None
      }
    }

    "delete an existing product by id" in {
      Delete("/products/1") ~> route ~> check {
        response.status shouldEqual StatusCodes.NoContent
        response.entity shouldNotEqual None
      }
    }

    "return Not Found error for a delete on not existing products" in {
      Delete("/products/3") ~> route ~> check {
        response.status shouldEqual StatusCodes.NotFound
        response.entity shouldNotEqual None
      }
    }
  }
}
