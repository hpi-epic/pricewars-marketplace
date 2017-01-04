package de.hpi.epic.pricewars

trait BeforeAfterEach extends org.specs2.specification.BeforeAfterEach {

  protected def before: Any = {
    DatabaseStore.setup()
  }

  protected def after: Any = {
  }
}
