# OMAR Disk Cleanup

## Dockerfile
```
FROM omar-base
COPY omar-disk-cleanup-app-1.0.1-SNAPSHOT.jar /home/omar
CMD while true; do sleep 1m; java -jar /home/omar/omar-disk-cleanup-app-1.0.1-SNAPSHOT.jar; done;
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
