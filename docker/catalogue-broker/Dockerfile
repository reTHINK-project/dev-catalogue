FROM rethink/catalogue-baseline:v2.0
MAINTAINER marc.emmelmann@fokus.fraunhofer.de


# Nothing to do here.  The baseline has installed the test
# component
#
# TODO: mv the installation of the code from the baseline
# into this dockerfile.


ADD ./runner.sh /opt/reTHINK/catalogue/catalogue_broker/runner.sh
RUN chmod +x /opt/reTHINK/catalogue/catalogue_broker/runner.sh


# Attention:  run with
#
#		docker  run  -it  --net=host 
#

# ENTRYPOINT is the command that is always run.
ENTRYPOINT ["/opt/reTHINK/catalogue/catalogue_broker/runner.sh"]
