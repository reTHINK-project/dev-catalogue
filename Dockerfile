FROM rethink/testbed-baseline

MAINTAINER marc.emmelmann@fokus.fraunhofer.de

RUN cd /opt/reTHINK && mkdir catalogue

COPY . /opt/reTHINK/catalogue
