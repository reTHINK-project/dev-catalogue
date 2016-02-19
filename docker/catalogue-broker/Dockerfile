FROM rethink/catalogue-baseline:v2.2
MAINTAINER marc.emmelmann@fokus.fraunhofer.de


# Nothing to do here.  The baseline has installed the test
# component
#
# TODO: mv the installation of the code from the baseline
# into this dockerfile.
#
# Attention:  run with
#
#		docker  run  -it  --net=host 
#

# ENTRYPOINT is the command that is always run.
ENTRYPOINT ["java", "-jar", "/opt/reTHINK/catalogue/catalogue_broker/target/rethink-catalogue-broker-2.2-jar-with-dependencies.jar", "-kp", "/opt/reTHINK/catalogue/ssl/keystore", "-tp", "/opt/reTHINK/catalogue/ssl/keystore"]

