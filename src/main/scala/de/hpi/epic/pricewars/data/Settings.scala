package de.hpi.epic.pricewars.data

case class Settings(consumer_per_minute: Double,
					max_req_per_sec: Double,
					max_updates_per_sale: Double)
