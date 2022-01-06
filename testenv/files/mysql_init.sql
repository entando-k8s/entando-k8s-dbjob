CREATE DATABASE IF NOT EXISTS sampledb;
CREATE USER IF NOT EXISTS existing IDENTIFIED BY 'test123';
CREATE USER IF NOT EXISTS myschema IDENTIFIED BY 'test123';
GRANT ALL ON sampledb.* TO 'existing'@'%';
GRANT ALL ON sampledb.* TO 'myschema'@'%';
