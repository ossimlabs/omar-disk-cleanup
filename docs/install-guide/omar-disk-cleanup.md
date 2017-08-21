# OMAR Disk Cleanup

## Dockerfile
```
FROM omar-base
ENV JDBC_CONNECTION_STRING jdbc:postgresql://host:5432/omar_db
ENV O2_DISK_VOLUME /data
ENV O2_MAX_DISK_LIMIT 0.9
ENV O2_MIN_DISK_LIMIT 0.8
ENV POSTGRES_PASSWORD password
ENV POSTGRES_USER postgres
ENV STAGER_URL http://omar-stager-app:8080/omar-stager
WORKDIR /home/omar
COPY omar-disk-cleanup-app-1.1.0-SNAPSHOT.jar .
RUN echo 'while true; do sleep 10m; java -jar /home/omar/omar-disk-cleanup-app-1.1.0-SNAPSHOT.jar; done;' >> omar-disk-cleanup-app-1.1.0-SNAPSHOT.sh
CMD sh omar-disk-cleanup-app-1.1.0-SNAPSHOT.sh
```
Ref: [omar-base](../../../omar-ossim-base/docs/install-guide/omar-base/)

## JAR
[http://artifacts.radiantbluecloud.com/artifactory/webapp/#/artifacts/browse/tree/General/omar-local/io/ossim/omar/apps/omar-disk-cleanup-app](http://artifacts.radiantbluecloud.com/artifactory/webapp/#/artifacts/browse/tree/General/omar-local/io/ossim/omar/apps/omar-disk-cleanup-app)


## Configuration
The following environment variables need to be set for the `omar` user.
```
JDBC_CONNECTION_STRING = jdbc:postgresql://<host>:<port>/<database>
O2_DISK_VOLUME = /data
O2_MAX_DISK_LIMIT = 0.9
O2_MIN_DISK_LIMIT = 0.8
POSTGRES_PASSWORD = <password>
POSTGRES_USER = <user>
STAGER_URL = <protocol>://<host>/omar-stager
```
