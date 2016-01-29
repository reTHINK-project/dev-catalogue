FROM rethink/catalogue-baseline
MAINTAINER marc.emmelmann@fokus.fraunhofer.de


# Nothing to do here.  The baseline has installed the test
# component
#
# TODO: mv the installation of the code from the baseline
# into this dockerfile.



# Attention:  run with
#
#		docker  run  -it  --net=host 
#
ENTRYPOINT ["java", "-jar", "/opt/reTHINK/catalogue/catalogue_test/target/rethink-catalogue-test-lastest-jar-with-dependencies.jar"]
# default port used is 8090
CMD ["8090"]
