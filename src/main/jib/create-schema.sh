#!/usr/bin/env bash
export DATABASE_USER=${1}
export DATABASE_PASSWORD=${2}
java ${JAVA_OPTS} -Djava.security.egd=file:/dev/./urandom -cp /app/resources/:/app/classes/:/app/libs/* \
        org.entando.k8s.db.job.CreateSchemaCommand || exit -1