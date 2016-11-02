package de.hpi.epic.pricewars

/**
  * Created by Jan on 02.11.2016.
  */
case class Merchant( api_endpoint_url: String,
                     merchant_name: String,
                     algorithm_name: String,
                     merchant_id: Option[String] )
