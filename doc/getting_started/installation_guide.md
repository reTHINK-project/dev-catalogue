### Installation guide

#### Working with source code

To work with the source code, please clone the dev-catalogue git repository.  Afterwards desend in the repository and run

```mvn install```

to build the jars for all components. Catalogue Data Objects are defined in the "catalogue_objects" folder. Please take a look at the examples provided.

After having generated the JARs for the catalogue broker and database, you may start them executing the jar, i.e.:

1.) For the Catalogue:
```
java -jar catalogue_broker/target/rethink-catalogue-broker-*-jar-with-dependencies.jar -http 8090 -coap "localhost:5683" -coaps 5684
```
2.) For the Broker:
```
java -jar catalogue_database/target/rethink-catalogue-database-*-jar-with-dependencies.jar -h mydomain.com -p 5683 -o catalogue_objects
```

Please refer to the [usage guide](./usage_guide.md) to learn about support arguments and configuration options.

#### Working with deployable Docker images


