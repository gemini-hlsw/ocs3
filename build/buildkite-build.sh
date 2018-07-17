#!/bin/bash
set -xe

cd `dirname $0`/..

###
### BUILD
###

echo "--- :scala: Compiling main codebase"
/usr/local/bin/sbt                  \
  -jvm-opts build/buildkite-jvmopts \
  -Docs3.skipDependencyUpdates      \
  headerCheck                       \
  test:headerCheck                  \
  scalastyle                        \
  compile

###
### COMPILE TESTS
###

# Compile tests
echo "--- :scala: Compiling tests"
/usr/local/bin/sbt                                        \
  -jvm-opts build/buildkite-jvmopts                       \
  -Docs3.skipDependencyUpdates                            \
  -Docs3.databaseUrl=jdbc:postgresql://$HOST_AND_PORT/gem \
  test:compile

###
### RUN TESTS
###

# Start a new Postgres container for this build
# TODO: read postgres version from the build
echo "--- :postgres: Starting Postgres test instance"
CID=`docker run --detach --publish 5432 postgres:9.6.0`

# Add an exit handler to ensure we always clean up.
function cleanup {
  echo "--- :postgres: Cleaning up Postgres test instance"
  docker stop $CID
  docker rm --volumes --force $CID
  echo "--- :tada: Done"
}
trap cleanup EXIT

# Get our host and port ... like 0.0.0.0:32751
HOST_AND_PORT=`docker port $CID 5432/tcp`

# The postgres user already exists, so we can go ahead and create the database
echo "--- :postgres: Creating database"
until docker exec $CID psql -U postgres -c 'create database gem'
do
  sleep 1
done

# Set up the schema and run tests
echo "--- :scala: Running tests"
/usr/local/bin/sbt                                        \
  -jvm-opts build/buildkite-jvmopts                       \
  -Docs3.skipDependencyUpdates                            \
  -Docs3.databaseUrl=jdbc:postgresql://$HOST_AND_PORT/gem \
  sql/flywayMigrate                                       \
  test

###
### JAVASCRIPT PACKAGING
###

echo "--- :javascript: Linking Javascript"
/usr/local/bin/sbt                      \
  -jvm-opts build/buildkite-jvmopts     \
  -Docs3.skipDependencyUpdates          \
  ui/fastOptJS

echo "--- :webpack: Webpack"
/usr/local/bin/sbt                      \
  -jvm-opts build/buildkite-jvmopts     \
  -Docs3.skipDependencyUpdates          \
  seqexec_web_client/fastOptJS::webpack

###
### DOCKER IMAGE
###

# If this is a merge into `develop` then this is a shippable version and we will build a docker
# image for it. We can later deploy it to test or production.
# if [ "$BUILDKITE_PULL_REQUEST" = "false" ] && [ "$BUILDKITE_BRANCH" = "develop" ];

  echo "--- :docker: Creating a Docker image"
  /usr/local/bin/sbt                      \
    -jvm-opts build/buildkite-jvmopts     \
    -Docs3.skipDependencyUpdates          \
    main/docker:publish                   \
    main/docker:clean

# fi