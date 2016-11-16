package de.hpi.epic.pricewars

import de.hpi.epic.pricewars.JSONConverter._
import org.specs2.mutable.Specification
import spray.http.StatusCodes._
import spray.http._
import spray.testkit.Specs2RouteTest

/**
  * Created by sebastian on 15.11.16
  */
class ProductTests extends Specification with BeforeAfterEach with Specs2RouteTest with MarketplaceService {
  sequential

  def actorRefFactory = system

  private val products = Seq(
    Product(Some(1), "CD 1", "Genre 1"),
    Product(Some(2), "CD 2", "Genre 2")
  )

  override def before: Unit = {
    super.before
    DatabaseStore.addProduct(products.head)
  }

  "The marketplace" should {

    "return a list containing one prodcut on startup" in {
      Get("/products") ~> route ~> check {
        response.status should be equalTo OK
        response.entity should not be equalTo(None)
        responseAs[Seq[Product]] must be equalTo Seq(products.head)
      }
    }

    "register a new product" in {
      Post("/products", products(1)) ~> route ~> check {
        response.status should be equalTo Created
        response.entity should not be equalTo(None)
        responseAs[Product] must be equalTo products(1)
      }
      Get("/products") ~> route ~> check {
        response.status should be equalTo OK
        response.entity should not be equalTo(None)
        responseAs[Seq[Product]] must be equalTo products
      }
    }

    "get an existing product by id" in {
      Get("/products/1") ~> route  ~> check {
        response.status should be equalTo OK
        response.entity should not be equalTo(None)
        responseAs[Product] must be equalTo products.head
      }
    }

    "return Not Found error for a get on not existing product" in {
      Get("/products/3") ~> route  ~> check {
        response.status should be equalTo NotFound
        response.entity should not be equalTo(None)
      }
    }

    "return Not Found error for a delete on not existing product" in {
      Delete("/products/3") ~> route ~> check {
        response.status should be equalTo NotFound
        response.entity should not be equalTo(None)
      }
    }

    "delete an existing product by id" in {
      Delete("/products/1") ~> route ~> check {
        response.status should be equalTo NoContent
        response.entity should be equalTo None
      }
    }

    "return Not Found error for a delete on not existing products" in {
      Delete("/products/3") ~> route ~> check {
        response.status should be equalTo NotFound
        response.entity should not be equalTo(None)
      }
    }
  }
}
