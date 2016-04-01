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