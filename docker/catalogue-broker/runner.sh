#!/bin/sh -x

# Attn.: all paths as they are found on the docker image !!!
MODELS_FOLDER="/opt/reTHINK/catalogue/model"
export MODELS_FOLDER

java -jar /opt/reTHINK/catalogue/catalogue_broker/target/rethink-catalogue-broker-2.0-jar-with-dependencies.jar $*


