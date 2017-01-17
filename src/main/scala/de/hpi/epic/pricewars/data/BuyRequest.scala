package de.hpi.epic.pricewars.data

/**
  * Created by Jan on 17.01.2017.
  */
case class BuyRequest(price: BigDecimal,
                      amount: Int,
                      prime: Option[Boolean] )
