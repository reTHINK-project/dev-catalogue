### Installation guide

#### Working with source code

To work with the source code, please clone the dev-catalogue git repository. Afterwards descend into the repository and run

```mvn install```

to build the jars for all components. Catalogue Data Objects are defined in the "catalogue_objects" folder. Please take a look at the examples provided.

After having generated the JARs for the catalogue broker and database, you may start them by executing the jar, i.e.:

1.) For the Catalogue:
```
java -jar catalogue_broker/target/rethink-catalogue-broker-*-jar-with-dependencies.jar
```
2.) For the Broker:
```
java -jar catalogue_database/target/rethink-catalogue-database-*-jar-with-dependencies.jar
```

Please refer to the [usage guide](./usage_guide.md) to learn about supported arguments and configuration options.


#### Working with deployable Docker images

A set of docker images is provided, one for each catalogue components. The images contain the most recent version of the implementation as found in the master branch of the code repository. Images are automatically updated by Docker-Hub. To deploy the images, run ```docker pull``` for each image. You may also directly start the images (indirectly triggering a *pull* if the image is not locally available) via:
```
docker run -it --net=host rethink/catalogue-broker
docker run -it --net=host rethink/catalogue-database
```
Note:  in order to provide a configuration as included in the docker files that allows a user to run the catalogue without the need to
do any further configuration, all instances need to be accessible vie "localhost".  Hence, docker is run using the --net=host option.
In case you defer from that way of running the images, you might have to configure the ip-addresses and ports under which each component
can be reached from another.  Please refer to the commands' options description for details.

If you reverse proxify your containers, you probably won't need to expose the ports on your hosts (otherwise conflicts may happen), so the "--net=host" parameter won't be necessary. The usage of -d is also usefull to keep in background the process. The correct commands are then
```
docker run -it -d --name="catalogue-broker" rethink/catalogue-broker
docker run -it -d --name="catalogue-database" rethink/catalogue-database
```



Please refer to the [docker website](https://www.docker.com) on how to install and work with docker. Also, please refer to the [usage guide](./usage_guide.md) to learn about support arguments and configuration options.





