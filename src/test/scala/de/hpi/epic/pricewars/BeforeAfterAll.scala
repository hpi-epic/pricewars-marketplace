package de.hpi.epic.pricewars

import org.specs2.mutable.Specification
import org.specs2.specification.core.Fragments

/**
  * Created by Jan on 02.11.2016.
  */
trait BeforeAfterAll extends Specification {
  override def map(fragments: => Fragments) =
    step(beforeAll) ^ fragments ^ step(afterAll)

  protected def beforeAll()
  protected def afterAll()
}
