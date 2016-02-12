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

# ENTRYPOINT is the command that is always run.
ENTRYPOINT ["java", "-jar", "/opt/reTHINK/catalogue/catalogue_database/target/rethink-catalogue-database-2.1-jar-with-dependencies.jar", "-o", "/opt/reTHINK/catalogue/catalogue_objects"]
