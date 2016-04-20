### Usage guide

#### Command line options

##### Catalogue Broker

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


Note: By default, the Catalogue Broker uses https for everything. If not configured with the options mentioned below, it runs on port 80 for http, and 443 for https. Usually you are only permitted to use those ports when running the jar with sudo.
If testing in a browser, please also make sure your browser accepts the provided self-signed certificates.


##### Catalogue Database

Synopsis:

`java -jar target/catalogue_database-*-jar-with-dependencies.jar [-host hostname] [-port coap_port] [-objpath object_path] [-usehttp]`

Example:

`java -jar catalogue_database/target/rethink-catalogue-database-*-jar-with-dependencies.jar -h mydomain.com -p 5683 -o catalogue_objects`


You can configure the Catalogue Database using the following options:

option        | description
------------- | ---------------------------
-host, -h     | specify Catalogue Broker hostname/IP
-domain, -d   | specify Catalogue Broker domain/IP used for sourcePackageURL generation
-port, -p     | specify Catalogue Broker coap port
-objpath, -o  | path of folder containing catalogue objects (e.g. provided catalogue_objects folder)
-usehttp      | change protocol of generated sourcePackageURLs to http (otherwise, https is used)
-lifetime, -t | set the time between client updates (default = 60)

If you run the Catalogue Database without launch arguments,
it tries to connect to the Catalogue Broker on localhost:5683 by default,
trying to use the catalogue_objects folder in the same directory you started the jar from.

Please be aware that the domain will be used to generate the sourcePackageURL, if a sourcePackage is provided.
If no domain is specified, the hostname will be used instead.
If your Catalogue Broker uses ports other than 80/443 for http/https, you have to provide "host:port" as domain for proper sourcePackageURL generation.


##### Catalogue Test WebPage


Synopsis:

`java -jar rethink-catalogue-test-*-jar-with-dependencies.jar [port]`

Example:

`java -jar catalogue_test/target/rethink-catalogue-test-*-jar-with-dependencies.jar 8090`

Note: by default, the catalogue test webpage is accessible via port 8080.



#### Using custom Catalogue Data Objects

To use custom Catalogue Data Objects, you have to comply with a certain folder structure. Please see the provided example objects contained in _catalogue_objects_.

1. the root folder for objects must contain the type as a subfolder, e.g. "protocolstub"
2. all elements of a catalogue object are contained in a single folder.
3. the type folders hold the catalogue object folders
4. the catalogue data object is primarily defined in _description.json_
5. the sourcePackage is defined in _sourcePackage.json_
6. if a sourcePackage is provided, then a sourcePackageURL will be generated.
7. providing a sourcePackage is optional. Please pay attention that in the case where a source package is not provided, a sourcePackageURL has to be defined in _description.json_.
7. the source code can either be included in the sourcePackage, or contained in _sourceCode.*_
8. a sourcePackage can also be included in description.json
9. you cannot have a sourcePackage defined in description.json and have the sourceCode in a separate file
10. please avoid duplicate entries, e.g. sourceCode in _sourcePackage.json_ and _sourceCode.*_

