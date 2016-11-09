package de.hpi.epic.pricewars

import org.specs2.specification.BeforeAfterExample

/**
  * Created by sebastian on 09.11.16
  */
trait BeforeAfterEach extends BeforeAfterExample {

  protected def before: Any = {
    DatabaseStore.setup
  }

  protected def after: Any = {
    DatabaseStore.reset()
  }
}
