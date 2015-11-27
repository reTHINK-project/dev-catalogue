# dev-catalogue
Development tree for the catalogue

Installation:
Simply execute "mvn install" inside the source directory, to build the jars for all components.

The hyperties themselves are defined in the file "hyperties.json", located inside the resources folder of the reTHINK catalogue database. Protostubs are defined in the "protostubs.json" file, located in the same folder.

To run the catalogue broker, you simply have to run the generated jar.

Synopsis:

`java -jar rethink-catalogue-broker-*-jar-with-dependencies.jar [-http [hostname:]port] [-coap [hostname:]port] [-coaps [hostname:]port]`

Example:

`java -jar catalogue_broker/target/rethink-catalogue-broker-*-jar-with-dependencies.jar -http 8090 -coap "localhost:6683" -coaps 6684`


To run the catalogue database, you have to provide IP and port of the southbound coap interface of the catalogue broker (which is on port 5683 by default).

Synopsis:

`java -jar rethink-catalogue-database-*-jar-with-dependencies.jar serverIP serverPort [clientIP] [clientPort]`

Example:

`java -jar catalogue_database/target/rethink-catalogue-database-0.1-jar-with-dependencies.jar localhost 6683`

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


