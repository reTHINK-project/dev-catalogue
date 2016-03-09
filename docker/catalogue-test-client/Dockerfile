FROM rethink/catalogue-baseline
MAINTAINER marc.emmelmann@fokus.fraunhofer.de

#
# Attention:  run with
#
#		docker  run  -it  --net=host 
#


# get the source code from the github repository.
# clone the source code into the "catalogue" directory
RUN cd /opt/reTHINK && git clone https://github.com/reTHINK-project/dev-catalogue.git catalogue

# build the files
RUN cd /opt/reTHINK/catalogue && mvn install


ENTRYPOINT ["java", "-jar", "/opt/reTHINK/catalogue/catalogue_test/target/rethink-catalogue-test-*-jar-with-dependencies.jar"]
# default port used is 8090
CMD ["8090"]
