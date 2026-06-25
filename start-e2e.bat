@echo off
REM 启动 Java 后端（连远端 dev 中间件），detached
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
start "luban-java-backend" /B "%JAVA_HOME%\bin\java.exe" -jar target\luban-backend-0.0.1-SNAPSHOT.jar > java-backend.log 2>&1
