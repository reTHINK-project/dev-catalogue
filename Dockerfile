FROM rethink/testbed-baseline

MAINTAINER marc.emmelmann@fokus.fraunhofer.de

RUN cd /opt/reTHINK && mkdir GitHubRepos

# we should rather do a git pull here and use proper tags
# to pull a stable version out of the repo.
RUN cd /opt/reTHINK/GitHubRepos && mkdir dev-catalogue
COPY . /opt/reTHINK/GitHubRepos/dev-catalogue


ENTRYPOINT ["echo", "This image (rethink/dev-catalogue) is not intended to be invoked directly.  If you derived your own image, you will need to specify a docker ENTRYPOINT."]
