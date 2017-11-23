# OMAR Disk Cleanup

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
