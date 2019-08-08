#!/usr/bin/env bash
set -e
docker-compose -f docker-compose-cicd.yml build
docker-compose -f docker-compose-cicd.yml up -d mysql postgresql
docker-compose -f docker-compose-cicd.yml up entando-k8s-dbjob-test
mkdir -p ./docker-cicd-result
docker cp $(docker ps -aq --filter ancestor=entando-k8s-dbjob-test:latest):/usr/src/entando-k8s-dbjob/target ./docker-cicd-result/
docker-compose -f docker-compose-cicd.yml down