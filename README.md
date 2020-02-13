[![Build Status](https://img.shields.io/endpoint?url=https%3A%2F%2Fstatusbadge-jx.apps.serv.run%2Fentando-k8s%2Fentando-k8s-dbjob)](https://github.com/entando-k8s/devops-results/tree/logs/jenkins-x/logs/entando-k8s/entando-k8s-dbjob/master)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=entando-k8s_entando-k8s-dbjob&metric=alert_status)](https://sonarcloud.io/dashboard?id=entando-k8s_entando-k8s-dbjob)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=entando-k8s_entando-k8s-dbjob&metric=coverage)](https://entando-k8s.github.io/devops-results/entando-k8s-dbjob/master/jacoco/index.html)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=entando-k8s_entando-k8s-dbjob&metric=vulnerabilities)](https://entando-k8s.github.io/devops-results/entando-k8s-dbjob/master/dependency-check-report.html)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=entando-k8s_entando-k8s-dbjob&metric=code_smells)](https://sonarcloud.io/dashboard?id=entando-k8s_entando-k8s-dbjob)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=entando-k8s_entando-k8s-dbjob&metric=security_rating)](https://sonarcloud.io/dashboard?id=entando-k8s_entando-k8s-dbjob)
[![Technical Debt](https://sonarcloud.io/api/project_badges/measure?project=entando-k8s_entando-k8s-dbjob&metric=sqale_index)](https://sonarcloud.io/dashboard?id=entando-k8s_entando-k8s-dbjob)

# entando-k8s-dbjob
Entando K8S Database Job



This is a very simplistic little Java program that can create a schema/user pair in different database management systems.
The exepected input:
1. DATABASESERVER_HOST is the ip address or hostname of the database server
2. DATABASE_SERVER_PORT
3. DATABASE_VENDOR: one of 'postgresql', 'oracle'
4. DATABASE_NAME: the name of the database set aside for Entando
5. DATABASE_ADMIN_USER is a user on the specified database that has the necessary privileges to add schemas and other users, and set their priviliges
  (Please create a user. Don't use 'postgres' or 'sys' or any of the built in users. These lead to complications that are not supported)
6. DATABASE_ADMIN_PASSWORD: the password of the above mentioned user
7. DATABASE_USER: user/schema pair to be created
8. DATABASE_PASSWORD: expected password for above mentioned user

The expected output is:
1. A schema with the same name as the user provided, as specified in the DATABASE_USER environment variable
2. The user should only have access to one schema which is the schema that has the same name as the user(On Postgres please note that it is the customer's responsibility to remove access to the public schema if needed. It is highly unlike that any of the resulting users will access this, but better safe than sorry)
3. The user can log in with a password specified in the DATABASE_PASSWORD environment variable
4. The user has the necessary privileges to create database objects such as tables and sequences

