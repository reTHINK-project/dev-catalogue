FROM rethink/dev-catalogue
MAINTAINER marc.emmelmann@fokus.fraunhofer.de

RUN cd /opt/reTHINK && mkdir catalogue

#RUN apt-get update -y && apt-get install -y git
#RUN apt-get update -y && apt-get install -y maven git default-jre default-jdk openjdk-7-jre openjdk-7-jdk


# Note, we should directly pull from github, but this requires that the repo becomes publically visible
# Right now, we include a static snapshot
RUN cd /opt/reTHINK/catalogue && cp ../GitHubRepo/dev-catalogue/catalogue_broker . 
RUN cd /opt/reTHINK/catalogue && cp ../GitHubRepo/dev-catalogue/catalogue_database .
RUN cd /opt/reTHINK/catalogue && cp ../GitHubRepo/dev-catalogue/catalogue_test .
RUN cd /opt/reTHINK/catalogue && cp ../GitHubRepo/dev-catalogue/model .
RUN cd /opt/reTHINK/catalogue && cp ../GitHubRepo/dev-catalogue/data .
RUN cd /opt/reTHINK/catalogue && cp ../GitHubRepo/dev-catalogue/pom.xml .


CMD echo "Baseline installation for reTHINK Catalogue; All code is under /opt/reTHINK" && ls -l /opt/reTHINK && echo "Installed components in .../reTHINK/catalogue" && ls -l /opt/reTHINK/catalogue


