#!/usr/bin/env bash
if [ "${DATABASE_VENDOR}" = "oracle" ] ; then
    pushd ${ENTANDO_COMMON_PATH}/oracle-driver-installer
    env MAVEN_OPTS="-Dmaven.repo.local=/opt/app-root/src/.m2/repository" ./install.sh || exit 5
    cp /jetty-runner/ojdbc*.jar /app/libs/ -f
    popd
fi
if [ "${DATABASE_SCHEMA_COMMAND}" = "PREPARE_ENTANDO_SCHEMAS" ] ; then
    $(dirname ${BASH_SOURCE[0]})/create-schema.sh  $PORTDB_USERNAME $PORTDB_PASSWORD || exit 1
    $(dirname ${BASH_SOURCE[0]})/create-schema.sh  $SERVDB_USERNAME $SERVDB_PASSWORD || exit 2
    export PORTDB_URL="jdbc:${DATABASE_VENDOR}://${DATABASE_SERVER_HOST}:${DATABASE_SERVER_PORT}/${DATABASE_NAME}"
    export SERVDB_URL=${PORTDB_URL}
    ${ENTANDO_COMMON_PATH}/init-db-from-war.sh --war-file=/wildfly/standalone/deployments/entando-de-app.war || exit 3
else
    $(dirname ${BASH_SOURCE[0]})/create-schema.sh  $DATABASE_USER $DATABASE_PASSWORD || exit 4
fi