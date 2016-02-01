# dev-catalogue
Development tree for the catalogue

Installation:
Simply execute "mvn install" inside the source directory, to build the jars for all components.

Catalogue Data Objects are defined in the "catalogue_objects" folder. Please take a look at the examples provided.

## Catalogue Broker

To run the catalogue broker, you simply have to run the generated jar.

**Please note:
The Catalogue Broker now uses https for everything. If not configured with the options mentioned below, it runs on port 80 for http, and 443 for https.
Usually you are only permitted to use those ports when running the jar with sudo.
If testing in a browser, please also make sure your browser accepts the provided self-signed certificates.**


Synopsis:

`java -jar rethink-catalogue-broker-*-jar-with-dependencies.jar [-http [hostname:]port] [-ssl port] [-coap [hostname:]port] [-coaps [hostname:]port]`

Example:

`java -jar catalogue_broker/target/rethink-catalogue-broker-*-jar-with-dependencies.jar -http 8090 -coap "localhost:5683" -coaps 5684`

You can configure the Catalogue Broker using the following options:

option                      | description
--------------------------- | ---------------------------
-http, -h                   | set http port
-ssl, -s                    | set https port
-coap, -c                   | set coap address (hostname + port, or just port)
-coaps, -cs                 | set coap address (hostname + port, or just port)
-keystorePath, -kp          | set keystore path
-truststorePath, -tp        | set truststore path
-keystorePassword, -kpw     | set keystore password
-keyManagerPassword, -kmpw  | set keystore manager password
-truststorePassword, -tpw   | set truststore password


## Catalogue Database

If you run the Catalogue Database similar to the provided example, but without the launch arguments,
it tries to connect to the Catalogue Broker on localhost:5683 by default,
trying to use the catalogue_objects folder in the same directory you started the jar from.

You can configure the Catalogue Database using the following options:

option       | description
------------ | ---------------------------
-host, -h    | specify Catalogue Broker hostname/IP
-port, -p    | specify Catalogue Broker coap port
-objpath, -o | path of folder containing catalogue objects (e.g. provided catalogue_objects folder)

**Please be aware that the hostname will be used to generate the sourcePackageURL, if a sourcePackage is provided.**

Synopsis:

`java -jar target/catalogue_database-*-jar-with-dependencies.jar [-host hostname] [-port coap_port] [-objpath object_path]`

Example:

`java -jar catalogue_database/target/rethink-catalogue-database-*-jar-with-dependencies.jar -h mydomain.com -p 5683 -o catalogue_objects`

### Using custom Catalogue Data Objects

To use custom Catalogue Data Objects, you have to comply with a certain folder structure. Please see the provided example objects contained in *catalogue_objects*.

1. the root folder for objects must contain the type as a subfolder, e.g. "protocolstub"
2. all elements of a catalogue object are contained in a single folder.
3. the type folders hold the catalogue object folders
4. the catalogue data object is primarily defined in *description.json*
5. the sourcePackage is defined in *sourcePackage.json*
6. if a sourcePackage is provided, then a sourcePackageURL will be generated.
7. providing a sourcePackage is optional. **If not provided, sourcePackageURL has to be defined in *description.json***
7. the source code can either be included in the sourcePackage, or contained in *sourceCode.js*
8. a sourcePackage can also be included in description.json
9. you cannot have a sourcePackage defined in description.json and have the sourceCode in a separate file
10. please avoid duplicate entries, e.g. sourceCode in *sourcePackage.json* **and** *sourceCode.js*


## Catalogue Test Website

To start the webserver for the catalogue broker test website, simply run the corresponding jar. The webserver uses port 8080 by default.

Synopsis:

`java -jar rethink-catalogue-test-*-jar-with-dependencies.jar [port]`

Example:

`java -jar catalogue_test/target/rethink-catalogue-test-*-jar-with-dependencies.jar 8090`


## Attention -- Docker Images Ready

The following components are dockerized to allow local testing without the need to install components:

* Catalogue Broker
* Catalogue Database
* Catalogue Test Client GUI

To start the three components, run the following commands _in that order_ in separate terminal windows:

1. docker run -it --net=host rethink/catalogue-broker:v2.1
2. docker run -it --net=host rethink/catalogue-database:v2.1
3. docker run -it --net=host rethink/catalogue-test-client:v2.1


You may then open a web-browser and go to _http://localhost:8090_ to access the test client's GUI.