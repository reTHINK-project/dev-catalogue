FROM rethink/dev-catalogue:v2.2
MAINTAINER marc.emmelmann@fokus.fraunhofer.de

RUN cd /opt/reTHINK && mkdir catalogue

RUN apt-get update -y  &&  apt-get install -y maven git default-jre default-jdk openjdk-7-jre openjdk-7-jdk


# Note, we should directly pull from github, but this requires that the repo becomes publically visible
# Right now, we include a static snapshot
RUN cd /opt/reTHINK/catalogue && cp -r ../GitHubRepos/dev-catalogue/catalogue_broker . 
RUN cd /opt/reTHINK/catalogue && cp -r ../GitHubRepos/dev-catalogue/catalogue_database .
RUN cd /opt/reTHINK/catalogue && cp -r ../GitHubRepos/dev-catalogue/catalogue_objects .
RUN cd /opt/reTHINK/catalogue && cp -r ../GitHubRepos/dev-catalogue/catalogue_test .
RUN cd /opt/reTHINK/catalogue && cp -r ../GitHubRepos/dev-catalogue/model .
RUN cd /opt/reTHINK/catalogue && cp -r ../GitHubRepos/dev-catalogue/data .
RUN cd /opt/reTHINK/catalogue && cp -r ../GitHubRepos/dev-catalogue/pom.xml .
RUN cd /opt/reTHINK/catalogue && cp -r ../GitHubRepos/dev-catalogue/ssl .

RUN cd /opt/reTHINK/catalogue && mvn install


ENTRYPOINT ["echo", "This image (rethink/catalogue-baseline) is not intended to be invoked directly.  If you derived your own image, you will need to specify a docker ENTRYPOINT."]


