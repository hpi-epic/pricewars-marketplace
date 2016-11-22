package de.hpi.epic.pricewars

/**
  * Created by sebastian on 09.11.16
  */
trait BeforeAfterEach extends org.specs2.specification.BeforeAfterEach {

  protected def before: Any = {
    DatabaseStore.setup()
  }

  protected def after: Any = {
  }
}
