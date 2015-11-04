# dev-catalogue
Development tree for the catalogue

Installation:
Simply execute "mvn install" inside the source directory, to build the jars for all components.


For the broker and database to work properly, you have to to set an environment variable called "MODELS_FOLDER" that points to the "model" folder inside the source directory.
E.g.: "export MODELS_FOLDER=.../dev-catalogue/model" (not needed for compiling the jars)

The hyperties themselves are defined in the file "hyperties.json", located inside the resources folder of the reTHINK catalogue database. Protostubs are defined in the "protostubs.json" file, located in the same folder.

To run the catalogue broker, you simply have to run the generated jar, e.g.
>java -jar catalogue_broker/target/rethink-catalogue-broker-0.1-jar-with-dependencies.jar
    
To run the catalogue database, you have to provide IP and port of the southbound coap interface of the catalogue broker (which is on port 5683 by default), e.g.
>java -jar catalogue_database/target/rethink-catalogue-database-0.1-jar-with-dependencies.jar localhost 5683

The http server for the catalogue broker test website runs on port 8090. To start the webserver, simply run the corresponding jar, e.g.
>java -jar catalogue_test/target/rethink-catalogue-test-0.1-jar-with-dependencies.jar

## Attention -- Docker Images Ready

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


