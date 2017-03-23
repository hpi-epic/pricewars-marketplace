# Marktplace

This repository contains the Marketplace-component of the Price Wars simulation. The marketplace represents the central trading place for all offers sold by the merchants and is therefore the first contact point for consumers to look and buy offers. In addition to that, it serves as the central and trust-worthy configuration store for the simulation (e.g. for the time settings or limits).

The meta repository containing general information can be found [here](https://github.com/hpi-epic/masterproject-pricewars)

## Application Overview

| Repo | Branch 	| Deployment to  	| Status | Description |
|--- |---	|---	|---  |---   |
| [UI](https://github.com/hpi-epic/pricewars-mgmt-ui) | master  	|  [vm-mpws2016hp1-02.eaalab.hpi.uni-potsdam.de](http://vm-mpws2016hp1-02.eaalab.hpi.uni-potsdam.de) 	| [ ![Codeship Status for hpi-epic/pricewars-mgmt-ui](https://app.codeship.com/projects/d91a8460-88c2-0134-a385-7213830b2f8c/status?branch=master)](https://app.codeship.com/projects/184009) | Stable |
| [Consumer](https://github.com/hpi-epic/pricewars-consumer) | master  	|  [vm-mpws2016hp1-01.eaalab.hpi.uni-potsdam.de](http://vm-mpws2016hp1-01.eaalab.hpi.uni-potsdam.de) | [ ![Codeship Status for hpi-epic/pricewars-consumer](https://app.codeship.com/projects/96f32950-7824-0134-c83e-5251019101b9/status?branch=master)](https://app.codeship.com/projects/180119) | Stable |
| [Producer](https://github.com/hpi-epic/pricewars-producer) | master  	|  [vm-mpws2016hp1-03.eaalab.hpi.uni-potsdam.de](http://vm-mpws2016hp1-03.eaalab.hpi.uni-potsdam.de) | [ ![Codeship Status for hpi-epic/pricewars-producer](https://app.codeship.com/projects/0328e450-88c6-0134-e3d6-7213830b2f8c/status?branch=master)](https://app.codeship.com/projects/184016) | Stable |
| [Marketplace](https://github.com/hpi-epic/pricewars-marketplace) | master  	|  [vm-mpws2016hp1-04.eaalab.hpi.uni-potsdam.de/marketplace](http://vm-mpws2016hp1-04.eaalab.hpi.uni-potsdam.de/marketplace/offers) 	| [ ![Codeship Status for hpi-epic/pricewars-marketplace](https://app.codeship.com/projects/e9d9b3e0-88c5-0134-6167-4a60797e4d29/status?branch=master)](https://app.codeship.com/projects/184015) | Stable |
| [Merchant](https://github.com/hpi-epic/pricewars-merchant) | master  	|  [vm-mpws2016hp1-06.eaalab.hpi.uni-potsdam.de/](http://vm-mpws2016hp1-06.eaalab.hpi.uni-potsdam.de/) 	| [ ![Codeship Status for hpi-epic/pricewars-merchant](https://app.codeship.com/projects/a7d3be30-88c5-0134-ea9c-5ad89f4798f3/status?branch=master)](https://app.codeship.com/projects/184013) | Stable |
| [Kafka RESTful API](https://github.com/hpi-epic/pricewars-kafka-rest) | master  	|  [vm-mpws2016hp1-05.eaalab.hpi.uni-potsdam.de](http://vm-mpws2016hp1-05.eaalab.hpi.uni-potsdam.de) 	| [ ![Codeship Status for hpi-epic/pricewars-kafka-rest](https://app.codeship.com/projects/f59aa150-92f0-0134-8718-4a1d78af514c/status?branch=master)](https://app.codeship.com/projects/186252) | Stable |

## Requirements

The marketplace is written in Scala and managed with sbt. Ensure to have a sbt installed and set up on your computer (see [the reference](http://www.scala-sbt.org/0.13/docs/Setup.html) for more information on getting started).

## Setup

First, create a new database for the marketplace to connect to on `localhost:5432` or change the corresponding settings: `CREATE DATABASE marketplace;`
* Database Name: `marketplace`
* Database User: `postgres`
* Database User password: ` ` (none)

After cloning the repository, run `sbt update` and `sbt compile` to download all required dependencies and compile the sorce code. Once you're done, you might start the server by either running `sbt ~tomcat:start` or `sbt tomcat:start`. The difference between the two commands is, that the first one with the ~ reloads all source code files (incl. compiling) when changes are made to any of these, while the second doesn't reflect changes made to files after the server has been started.

In the default configuration, you can access the server with `http://localhost:8080/` in your browser or by using your preferred REST GUI. All available routes are documented in our global API definition, available here: https://hpi-epic.github.io/masterproject-pricewars.

## Configuration

All pre-defined config settings are located in `/src/main/resources/application.conf`. Where appropiate, values from the environment are used (for the Docker setup) and a specialized config file `/src/main/resources/application.deployment.conf` is present for our VM deployment.

## Concept

The marketplace acts as the main platform to trade products offered by different merchants, to enforce time limits and is the main source to feed Kafka with various logs. Therefore (and due to the authorization required for several actions), it is the main source of trust within a simulation. 

### Authorization

Some routes require an authorization by the merchant or consumer in order to perform the requested operation. For simplicity reasons and to allow other components, we decided to use an ID that can be automatically calculated from the token (as described below). The required tokens can be created using the UI (in case of a merchant) or directly by performing a POST to the corresponing endpoint. The token shown should be copied and stored permanently as it should be used for a merchant regardless of Re-deployments and can't be reset (nor shown again). Whenever authorization is required, the token has to be sent in the HTTP `Authorization` header as `Token <value>`, otherwise it will be rejected. In some cases, a request limit is enforced by the marketplace to limit the amount of requests. 

The limit is enforced for:

* Deleting offers
* Updating offers
* Buying offers

In addition, a valid authorization is enforced for:

* Getting offers with the optional parameter `include_empty_offer=true`
* Adding offers
* Restocking offers
* Deleting tokens for the consumer or merchant

We decided not to enforce the limit when adding or restocking offers as this is separate from updating the price. It should be possible at any time in order to prevent a merchant being unable to participate with _any_ product.

#### Decision to use string-based merchant and cosnumer IDs

When implementing our authentication system, we decided to switch from integer-based merchant IDs to string based merchant IDs. Without the authentication, a simple number was enough to identify a merchant and this was used to perform any operation which includes the merchant (e.g. updating prices). This merchant ID was also used whenever the merchant bought a new product from the producer and logged together with the bought product and its price. Even though other attributes of the acquisition (such as the amount or the products uid) were part of the signature created by the producer and validated by the marketplace, the merchant ID was not. In this scenario, it was completely insecure and any merchant could have bought products in the name of another merchant without paying anything (but with getting the product with a valid signature). The main problem when handling the authentication and the logging of the merchant ID in a distributed system is that a single source of trust is usually required. In our scenario, this role as an authentication server could be the marketplace, which is responsible for generating and handling the merchant tokens and merchant IDs. In order to minimise the network traffic, we searched for a solution which doesn't require the producer to validate the token and get the ID with contacting the marketplace. As a result, we came up with a merchant ID that can be calculated from the merchant token: In order to do so, the merchant token is hashed with SHA-256 and Base64 encrypted. Therefore, we changed the merchant ID to a string. This makes it computationally expensive to get from the ID to the token (e.g. as a competitor). In addition, the merchant ID is now included in the signature generated by the producer, so that the marketplace can additionally validate that the merchant, which is going to add the offer to the marketplace, bought the product at the producer. Thus, this concepts fulfils the requirement described above.

#### Future Work: De- / Active offers

As an extension to the marketplace, we thought that it might be useful to activate and disable certain offers on the marketplace. For example, the merchant could use this to pause offering products prior to a re-deployment. Please note, that this requirement is not connected to the previously described and might be implemented in any order. If the marketplace and producer should be merged to ease the buying behaviour of the merchant, this would be useful to assign a new product to the merchant without offering it to customers or the competition directly.

### Product Signature

Every time the amount of an offer is changed (either because it's a new offer or the offer is restocked), the merchant is required to pass a valid signature for the required operation. These signatures are generated by the producer, as described in the corresponding [ReadMe](https://github.com/hpi-epic/pricewars-producer/blob/master/README.md#product-signature). 

A valid signature, after decrypting, contains the following information, separated by a space:

`<product_uid> <amount> <merchant_id> <timestamp>`

Besides checking the for right product UID and the merchant ID (both must match exactly), the amount in the signature must be greater or equal to the amount described in the offer by the marketplace. Used signatures are stored in a database table together with the sum of the amount (if the signature allowed an amount greater than one) used by the merchant. Changes to offers are only allowed if all these checks pass.

#### Disallow decreasing the amount of offers

In our first prototypes, we allowed the merchant to decrease the amount of offers he posted to the marketplace by "restocking" a negative amount. Until we enforced the usage of signatures, this was enough for the merchant to implement a unrestricted pricing strategy. However, we encountered a problem with this process when adding the requirement for the signature. First, a merchant is unable to get a signature with a negative amount (the marketplace checks for equality of the amount given by the merchant and the amount included in the signature) and second, a signature that has been redeemed can't be used a second time to later increase the value again. Until now, we haven't implemented a solution for this scenario (it's documented as described here in our API definition) but we thought of a "simple" solution: Whenever the merchant requests to decrease the amount of an offer, the marketplace should lower the value in the database as requested (if the remaining result is equal to or higher than zero) and should generate a new signature with the same information (exactly as the producer would have done). Without logging any price to the revenue topic (it has been paid earlier when getting these products for the first time), this is returned to the merchant, which is than responsible for saving that signature and handling it carefully. We imagine, that this should be quite simple to implement and enough to solve the problem.

## Important components

### connectors
#### MerchantConnector
Responsible for notifying the merchant whenever a product has been sold. If the config setting `remove_merchant_on_notification_error` is set to `true` and the TCP handshake to the endpoint is not successful (the HTTP status code is not important), the merchant is deleted after 10 retries (`spray.can.host-connector.max-retries`).

#### ProducerConnector
Required for fetching the current producer key and for validating the signature. If validating the signature failed and the last fetch for a new producer key was more than 15 minutes ago, the key is updated and the validation is retried.

#### DatabaseStore
Abstracts all database queries and transactions from other parts. After processing a query, a log message is sent to kafka including important parts of the request and response. All log messages include a HTTP status code and a timestamp as meta information.

#### MarketplaceService
Defines all routes and adds CORS support. Each route is first checked for the limit and afterwards mainly handled by the corresponding method in the DatabaseStore. The routes for `products` are prepared but neither used nor filled yet.

#### ValidateLimit
Responsible for checking the Authorization Header and enforcing a request limit. For the short-term persistency, a Redis is used with custom keys (based on the Token and a timestamp) with a pre-set TimeToLive value (100 per default, can be changed dynamically by using the `/config` route). The amount of keys with the token as prefix is used to calculate whether an additional request is allowed or not.

## Logging

The marketplace logs many actions to Kafka. Some are important for merchants as historical record of the simulation and can be used for machine learning. The next sections will shortly show some example log messages, when they are created and what they contain.

### marketSituation

![](docs/ms_log.png)

Whenever a merchant adds or changes an offer, the situation on the marketplace changes. The marketplace takes a snapshots of the updated offers that are available for consumer with following data.

```
marketSituation: {
	timestamp
	trigger
	merchant_id
	product_id
	offers: { merchant_id -> Offer }
}

Offer: {
	offer_id
	uid
	product_id
	quality
	merchant_id
	amount
	price
	shipping_time_standard
	shipping_time_prime
	prime
}
```

### buyOffer

![](docs/bo_log.png)

Whenever a consumer buys an offer, this transaction is logged as _buyOffer_ message and the merchant is notified on his _/sold_ endpoint.

```
buyOffer: {
	amount
	consumer_id
	http_code
	left_in_stock
	merchant_id
	offer_id
	price
	product_id
	quality
	timestamp
	uid
}
```


