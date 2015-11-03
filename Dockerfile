FROM rethink/testbed-baseline

MAINTAINER marc.emmelmann@fokus.fraunhofer.de

RUN cd /opt/reTHINK && mkdir GitHubRepos

# we should rather do a git pull here and use proper tags
# to pull a stable version out of the repo.
RUN cd /opt/reTHINK/GitHubRepos && mkdir dev-catalogue
COPY . /opt/reTHINK/GitHubRepos/dev-catalogue


