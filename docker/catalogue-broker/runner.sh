#!/bin/sh -x

# Attn.: all paths as they are found on the docker image !!!

java -jar /opt/reTHINK/catalogue/catalogue_broker/target/rethink-catalogue-broker-2.0-jar-with-dependencies.jar -kp /opt/reTHINK/catalogue/ssl/keystore -tp /opt/reTHINK/catalogue/ssl/keystore $*

