# dev-catalogue
Development tree for the catalogue

Installation:
Simply execute "mvn install" inside the source directory, to build the jars for all components.

Catalogue Data Objects are defined in the "catalogue_objects" folder. Please take a look at the examples provided.

## Catalogue Broker

To run the catalogue broker, you simply have to run the generated jar.

Synopsis:

`java -jar rethink-catalogue-broker-*-jar-with-dependencies.jar [-http [hostname:]port] [-coap [hostname:]port] [-coaps [hostname:]port]`

Example:

`java -jar catalogue_broker/target/rethink-catalogue-broker-*-jar-with-dependencies.jar -http 8090 -coap "localhost:6683" -coaps 6684`


## Catalogue Database

To run the catalogue database, you have to provide the hostname of the catalogue broker, and the port of the southbound coap interface (which is on port 5683 by default).

Synopsis:

`java -jar target/catalogue_database-*-jar-with-dependencies.jar ServerHost ServerCoapPort [ObjectFolderPath]`

Example:

`java -jar catalogue_database/target/rethink-catalogue-database-*-jar-with-dependencies.jar mydomain.com 6683 catalogue_objects`

### Using custom Catalogue Data Objects

In order to include your own hyperties/protostubs in the database, you can specify a folder path as an argument when running the database. The hyperties/protostubs inside that folder will be parsed on start. Please view the provided examples inside the catalogue_objects folder, to see the formatting.

* All hyperties/protostubs/sourcePackages are in their respective folders
* A Hyperty descriptor is defined in a file with the ending ".hyperty", while protostubs have the ending ".stub". SourcePackages have the ending ".sourcePackage".
* If your javascript code is contained in a seperate file, it needs to have the same filename as the sourcePackage it belongs to (including file extension), in order to be included in the sourcePackage.
* For now, SourcePackages have a "objectName" field, to easily reference a sourcePackage in hyperties/protostubs via sourcePackageURL.
* SourcePackages can be requested from the server like this: "/.well-known/sourcepackage/<objectName>" 
* The SourcePackageURL field of hyperties/protostubs has to be the path to the sourcePackage's objectName, without protocol, domain or ".well-known" (e.g. sourcePackageURL = "sourcepackage/MySourcePackage")

Example:

"SecondHyperty.hyperty" is the descriptor for a hyperty, located in catalogue_objects/hyperty. It contains a sourcePackageURL, that points to "sourcepackage/SecondHypertySourcePackage".

The SourcePackage itself is defined by "SecondHyperty.sourcePackage", which is located in catalogue_objects/sourcepackage. It defines the name of the sourcePackage by using the "objectName" field, which in this case is "SecondHypertySourcePackage.

Because the SourcePackage defined by SecondHyperty.sourcePackage is (intentionally) missing the sourceCode field, the source code can reside in a seperate file.

## Catalogue Test Website

To start the webserver for the catalogue broker test website, simply run the corresponding jar.

Synopsis:

`java -jar rethink-catalogue-test-*-jar-with-dependencies.jar [port]`

Example:

`java -jar catalogue_test/target/rethink-catalogue-test-0.1-jar-with-dependencies.jar 9080` 


##  ~~Attention -- Docker Images Ready~~ Docker Images are probably broken currently.

The following components are dockerized to allow local testing without the need to install components:

* Catalogue Broker
* Catalogue Database
* Catalogue Test Client GUI

To start the three components, run the following commands _in that order_ in separate terminal windows:

1. docker run -it --net=host rethink/catalogue-broker
2. docker run -it --net=host rethink/catalogue-database
3. docker run -it --net=host rethink/catalogue-test-client


You may then open a web-browser and go to _http://localhost:8090_ to access the test client's GUI.  Note that right now, we only have two Hyperties in the Database, named _MyFirstHyperty_ and _MySecondHyperty_.

For some fun, load _MySecondHyperty_ and execute the Hyperties code :-)


