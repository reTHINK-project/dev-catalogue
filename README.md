# dev-catalogue
Development tree for the catalogue

Installation:
Simply execute "mvn install" inside the source directory, to build the jars for all components.

The hyperties themselves are defined in the file "hyperties.json", located inside the resources folder of the reTHINK catalogue database. Protostubs are defined in the "protostubs.json" file, located in the same folder.

## Quick Start Using ready-to-use Docker Images

The following components are dockerized to allow local testing without the need to install components:

* Catalogue Broker
* Catalogue Database
* Catalogue Test Client GUI

To start the three components, run the following commands _in that order_ in separate terminal windows:

1. docker run -it --net=host rethink/catalogue-broker:v1.0
2. docker run -it --net=host rethink/catalogue-database:v1.0
3. docker run -it --net=host rethink/catalogue-test-client:v1.0

Note: if you do not want the (stable) v1.0 Verison but the latest commit from the repository, just leave the ":v1.0" tag away.

You may then open a web-browser and go to _http://localhost:8090_ to access the test client's GUI.  Note that right now, we only have two Hyperties in the Database, named _MyFirstHyperty_ and _MySecondHyperty_.

For some fun, load _MySecondHyperty_ and execute the Hyperties code :-)




## Details on the Catalogue components and how to use them from the source code

### Catalogue Broker

To run the catalogue broker, you simply have to run the generated jar.

Synopsis:

`java -jar rethink-catalogue-broker-*-jar-with-dependencies.jar [-http [hostname:]port] [-coap [hostname:]port] [-coaps [hostname:]port]`

Example:

`java -jar catalogue_broker/target/rethink-catalogue-broker-*-jar-with-dependencies.jar -http 8090 -coap "localhost:6683" -coaps 6684`


### Catalogue Database

To run the catalogue database, you have to provide IP and port of the southbound coap interface of the catalogue broker (which is on port 5683 by default).

Synopsis:

`java -jar rethink-catalogue-database-*-jar-with-dependencies.jar serverIP serverPort [ObjectsPath]`

Example:

`java -jar catalogue_database/target/rethink-catalogue-database-0.1-jar-with-dependencies.jar localhost 6683 catalogue_objects`

#### Using custom hyperties/protostubs

In order to include your own hyperties/protostubs in the database, you can specify a folder path as an argument when running the database. The hyperties/protostubs inside that folder will be parsed on start. Please view the provided examples inside the catalogue_objects folder, to see the formatting.

* A Hyperty descriptor is defined in a file with the ending ".hyperty", while protostubs have the ending ".stub".
* If your javascript code is contained in a seperate file, it needs to have the same filename as the hyperty descriptor it belongs to (excluding file extension), in order to be included in the sourcePackage.

Example:

"SecondHyperty.hyperty" is the descriptor for a hyperty. It contains a sourcePackage, but that is missing the sourceCode field.
The source code that belongs to it, is contained in a file called "SecondHyperty.js".
If the hyperty descriptor file contains a sourcePackage, the contents of "SecondHyperty.js" is read and added as sourceCode to the sourcePackage.


### Catalogue Test Website

To start the webserver for the catalogue broker test website, simply run the corresponding jar.

Synopsis:

`java -jar rethink-catalogue-test-*-jar-with-dependencies.jar [port]`

Example:

`java -jar catalogue_test/target/rethink-catalogue-test-0.1-jar-with-dependencies.jar 9080` 


### Docker Images

The following components are dockerized to allow local testing without the need to install components:

* Catalogue Broker
* Catalogue Database
* Catalogue Test Client GUI

To start the three components, run the following commands _in that order_ in separate terminal windows:

1. docker run -it --net=host rethink/catalogue-broker
2. docker run -it --net=host rethink/catalogue-database
3. docker run -it --net=host rethink/catalogue-test-client

**Attention:** The above docker commands will get the the *latest* docker image, i.e. imagages based on the latest commit on the dev-catalgue master branch.  The latest *stable* version is currently **v1.0**.  It can be pulled by adding ":v1.0" to the name of the docker image.


You may then open a web-browser and go to _http://localhost:8090_ to access the test client's GUI.  Note that right now, we only have two Hyperties in the Database, named _MyFirstHyperty_ and _MySecondHyperty_.

For some fun, load _MySecondHyperty_ and execute the Hyperties code :-)


