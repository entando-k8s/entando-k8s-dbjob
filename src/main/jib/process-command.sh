#!/usr/bin/env bash
if [ "${DATABASE_VENDOR}" = "oracle" ]; then
  pushd ${ENTANDO_COMMON_PATH}/oracle-driver-installer
  echo "Downloading Oracle drivers from repo ${ORACLE_MAVEN_REPO:-https://maven.oracle.com}"
  curl -v ${ORACLE_MAVEN_REPO:-https://maven.oracle.com} || true
  env MAVEN_OPTS="-Dmaven.repo.local=/opt/app-root/src/.m2/repository" ./install.sh || exit 5
  cp /jetty-runner/ojdbc*.jar /app/libs/ -f
  popd
fi
if [ "${DATABASE_SCHEMA_COMMAND}" = "CREATE_SCHEMA" ]; then
  $(dirname ${BASH_SOURCE[0]})/create-schema.sh $DATABASE_USER $DATABASE_PASSWORD || exit 4
else
  echo "Uknown DATABASE_SCHEMA_COMMAND: ${DATABASE_SCHEMA_COMMAND}"
  exit 6
fi
