#!/bin/sh

rm srcFiles.tar

cd ../..
tar cvf docker/catalogue-baseline/srcFiles.tar ./catalogue_broker ./catalogue_database ./catalogue_test ./pom.xml ./data ./model

echo "Attention: make sure to commit the new tar file"


