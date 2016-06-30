### Usage guide
#### Command line options
##### Catalogue Broker
Synopsis:

`java -jar rethink-catalogue-broker-*-jar-with-dependencies.jar [options]`

Example:

`java -jar catalogue_broker/target/rethink-catalogue-broker-*-jar-with-dependencies.jar -http 8090 -coap "localhost:5683" -coaps 5684 -default "runtime/myRuntime"`

You can configure the Catalogue Broker using the following options:

option                      | description
--------------------------- | ---------------------------
-host                       | set coap & http host name
-http, -h                   | set http port
-ssl, -s, -https, -hs       | set https port
-coap, -c                   | set coap address (host[:port])
-coaps, -cs                 | set coap address (host[:port])
-coaphost, -ch              | set coap host
-coapport, -cp              | set coap port
-coapshost, -ch             | set coaps host
-coapsport, -cp             | set coaps port
-keystorePath, -kp          | set keystore path
-truststorePath, -tp        | set truststore path
-keystorePassword, -kpw     | set keystore password
-keyManagerPassword, -kmpw  | set keystore manager password
-truststorePassword, -tpw   | set truststore password
-default                    | set default instance (type/instanceName)
-v, -vv                     | increase logging level

Note: By default, the Catalogue Broker uses https for everything. If not configured with the options mentioned below, it runs on port 80 for http, and 443 for https. Usually you are only permitted to use those ports when running the jar with sudo.
If testing in a browser, please also make sure your browser accepts the provided self-signed certificates.

##### Catalogue Database
Synopsis:

`java -jar target/catalogue_database-*-jar-with-dependencies.jar [options]`

Example:

`java -jar catalogue_database/target/rethink-catalogue-database-*-jar-with-dependencies.jar -h mydomain.com -p 5683 -o catalogue_objects`

You can configure the Catalogue Database using the following options:

option              | description
------------------- | ---------------------------
-host, -h           | specify Catalogue Broker hostname/IP
-domain, -d         | specify Catalogue Broker domain/IP used for sourcePackageURL generation
-port, -p           | specify Catalogue Broker coap port
-coapaddress, -ca   | set Database CoAP address (host[:port])
-coaphost, -ch      | set Database CoAP host name
-coapport, -cp      | set Database CoAP port
-coapsaddress, -csa | set Database CoAPs address (host[:port])
-coapshost, -ch     | set Database CoAPs host name
-coapsport, -cp     | set Database CoAPs port
-objpath, -o        | path of folder containing catalogue objects (e.g. provided catalogue_objects folder)
-usehttp            | change protocol of generated sourcePackageURLs to http (otherwise, https is used)
-lifetime, -t       | set the time between client updates (default = 60)
-endpoint, -e       | set Catalogue Database endpoint name
-v, -vv             | increase logging level

If you run the Catalogue Database without launch arguments,
it tries to connect to the Catalogue Broker on localhost:5683 by default,
trying to use the catalogue_objects folder in the same directory you started the jar from.

Please be aware that the domain will be used to generate the sourcePackageURL, if a sourcePackage is provided.
If no domain is specified, the hostname will be used instead.
If your Catalogue Broker uses ports other than 80/443 for http/https, you have to provide "host:port" as domain for proper sourcePackageURL generation.
