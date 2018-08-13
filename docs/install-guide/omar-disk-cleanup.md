# OMAR Disk Cleanup
Runs similar to a daemon service that checks the indicated disk size and will delete data from OMAR to a minimum threshhold if the amount of data on disk exceeds a maximum threshhold.  The required ENV variables that must be defined:

* **`DELETE_STALE_ENTRIES`** a boolean to remove raster entries whose file is not on the volume. Example true.
* **`DELETE_STALE_FILES`** a boolean to delete files on the volume older than the oldest ingested raster entry.  Example true.
* **`JDBC_CONNECTION_STRING`** is the full URL to the database.  Example: jdbc:postgresql://host:port/database
* **`O2_DISK_VOLUME`** is defined to the desired disk volume cache location.  Example /data/s3.
* **`O2_MAX_DISK_LIMIT`** Expressed as a percentage between 0 and 1.  Example: .96
* **`O2_MIN_DISK_LIMIT`** Expressed as a percentage between 0 and 1.  Example: .92
* **`POSTGRES_PASSWORD`** Password
* **`POSTGRES_USER`** Username
* **`STAGER_URL`** Location of the stager URL.  For OpenShift pod deployments this should be **http://omar-stager-app:8080/omar-stager** if the context path is set to omar-stager.
