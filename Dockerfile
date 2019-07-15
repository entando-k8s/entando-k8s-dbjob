FROM entando/entando-integration-tests-base:5.2.0-SNAPSHOT
ARG MVN_COMMAND="mvn verify jacoco:report"
ENV MVN_COMMAND=$MVN_COMMAND
COPY . /usr/src/entando-k8s-dbjob
WORKDIR /usr/src/entando-k8s-dbjob
CMD $MVN_COMMAND