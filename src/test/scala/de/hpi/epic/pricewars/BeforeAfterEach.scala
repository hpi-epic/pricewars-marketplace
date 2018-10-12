package de.hpi.epic.pricewars

import de.hpi.epic.pricewars.services.DatabaseStore

trait BeforeAfterEach extends org.specs2.specification.BeforeAfterEach {

  protected def before(): Any = {
    DatabaseStore.reset()
    DatabaseStore.setup()
  }

  protected def after: Any = {
  }
}
