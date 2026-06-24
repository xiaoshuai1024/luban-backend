@echo off
REM start java backend via mvn spring-boot:run (reads src resources incl. flyway migrations), detached
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"
set "APP_PORT=8080"
set "MYSQL_HOST=192.168.100.248"
set "MYSQL_PORT=13306"
set "MYSQL_DB=luban"
set "MYSQL_USER=root"
set "MYSQL_PASSWORD=yanhuo123"
set "REDIS_HOST=192.168.100.248"
set "REDIS_PORT=16379"
set "REDIS_PASSWORD="
start "luban-java-mvn" /B cmd /c "C:\tools\apache-maven-3.9.16\bin\mvn.cmd -q spring-boot:run > mvn-run.log 2>&1"
