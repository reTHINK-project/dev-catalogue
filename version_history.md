# version history
## 1.0.0
* initial public release

## 1.0.1
* fixed docker images

## 1.0.2
* fixed pom.xml
* fixed Catalogue Database crashing on launch
* added mandatory attribute "language" to example catalogue objects
* fixed NullPointerException when trying to get a list of hyperties/protostubs/etc. before any Database has registered on the server.

## 1.0.3
* fixed a bug in the broker that can cause a stall on some machines
* fixed error response when trying to get resources and no database has connected to the broker yet

## 1.0.4
* updates to docker images
* added HelloWorld example
* changed mapping of sourcePackage to Catalogue Object to use cguid
* updated Usage Guide
* added -domain option to Catalogue Database
* improved registration procedure (faster + less traffic)

## 1.0.5
* sourcePackages are now always a separate catalogue object
* added "scheme" attribute to dataSchema
* updated hyperty examples to reflect change in model

## 1.1.0
* Updated leshan version to 0.1.11-M10 (currently using snapshot)
* updated model (accessControlPolicy & scheme not mandatory anymore)
* added ability to restart all connected databases, or a specific one, using either '/.well-known/restart' or '/.well-known/restart/<endpoint>'
* renamed folder for idp proxy object from "idpproxy" to "idp-proxy" (how you specify it in URL)
* improved concurrency stability
* Broker & Database respond faster if the response is large
* added -v option to broker & database to increase log level to DEBUG
* colored logging
* Catalogue Broker
  * included rethink-catalogue-test into Catalogue Broker, it is the default website for the Broker now
  * updated test website to automatically get list of instances if type is selected
  * minor changes to broker responses, tries to eliminate stringified jsons in json
  * fixed bug in broker that resulted in a map getting duplicate entries and growing indefinitely on every client update
  * broker may now respond with appropriate error code if an object was not found, or another kind of error occurred
  * removed coap northbound interface from Broker
  * added alias for -sslport option for Broker, called -httpsport or -hs
  * added ability to get list of connected clients using /.well-known/client
  * added ability to do a more fine grained request, e.g. https://localhost/.well-known/sourcepackage/9819113587321/sourceCode/attr2/list (might come in handy with sourceCode as json)
  * added options for setting http and coap host name
* Catalogue Database
  * set default database lifetime to 60s
  * added -lifetime option for Database, setting the interval of sending keep-alives
  * added -endpoint option to set database endpoint name
  * added options for setting coap(s) host name and port