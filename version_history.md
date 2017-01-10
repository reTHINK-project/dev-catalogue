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
* Updated leshan version to 0.1.11-M10
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
  * fixed database initialization failing if there is no instance for a model

## 1.2.0
* changed way of defining default hyperty/protostub/etc.
  * either use "-default" for broker to set default instance for a certain type, e.g. -default "hyperty/myHyperty"
  * or use configuration file
* added ability to configure Catalogue Broker and Database with a configuration file
* changed sourcePackageURL now being modified by the Catalogue Broker
* updated leshan to version 0.1.11-M12
* added more logging options (see logLevel in configuration or use -v, -vv or -vvv launch option)
* Broker
  * implemented async request handling
  * added option "-default" to set default instances for a certain type, e.g. -default "hyperty/myHyperty"
  * added option "-sourcePackageURLProtocol" to specify the protocol used when modifying sourcePackageURLs
  * Broker will respond with an error, if the default instance has been requested, but was not defined
  * improved Broker Test Page
  * can be configured using brokerconf.json in root directory
  * requesting a list of instances for a certain type now returns a sorted list
  * added EventServlet that notifies about changes of connected Databases to the Broker
  * changed northbound interface to react to /.well-known/database instead of /.well-known/client
  * updated Test Page
* Database
  * no longer supports instances with the name "default" (a warning will be printed)
  * can be configured using dbconf.json in root directory
* californium
  * changed deduplicator from DEDUPLICATOR_MARK_AND_SWEEP to DEDUPLICATOR_CROP_ROTATION

## 1.2.1
* catch handling client responses twice
* removed need to rely on Californium.properties
* minor fix for handling sourcePackageURLProtocol option

## 1.2.2
* dropped ability to set sourcePackageURL protocol
* added ability to manually set sourcePackageURL hostname

## 1.3.0
* removed catalogue_objects folder (was deprecated/unusable)
* updated to leshan version 0.1.11-M13
* improved logging
* launch options that use the same names as the config files
* Current version now logged on Broker/Database start
* Californium.properties not stored or loaded anymore
* 'encoding' field in sourcePackage not mandatory anymore
* sourcePackages are now get the name of the descriptor it belongs to, with a "_sp" suffix
* sourcePackages get added a objectName field instead of cguid field
* Broker specific changes:
  * removed need for setting sourcePackageURLHost, it is now extracted from HTTP request
* Database specific changes:
  * optimized code
  * increased stability
  * better validation

## 1.3.1
* auto-detect changes in catalogue objects folder and reregister Database on change
* under-the-hood optimizations for Broker and Database
* logging improvements

## 1.3.2
* changed default coap host from localhost to 0.0.0.0

## 1.4.0
* fixed docker build error
* deleted default catalogue_objects folder
* updated libs
* Broker specific:
  * default keystore now included in jar
  * added md5 checksums to sourcePackages
  * added ability to filter response with constraints by using HTTP POST request
  * added ability to set multiple objects as "default"
  * HTTP servlet now responds with proper content type ("application/json")
  * sets up sourcePackages by sourceCodeClassname
  * test website does a simple md5 check when clicking on "get & execute code"
* Database parses files alphabetically
  * generate md5 checksums of sourceCode and put it in sourcePackage as new attribute
  * auto generate cguid
  * made CatalogueObjectInstances comparable
  