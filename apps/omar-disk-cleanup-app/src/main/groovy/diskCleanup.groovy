import groovy.sql.Sql
import groovyx.net.http.HTTPBuilder
import static groovyx.net.http.Method.POST


// necessary environment variables
diskVolume = System.getenv( "O2_DISK_VOLUME" ).toString()
jdbcUrl = System.getenv( "JDBC_CONNECTION_STRING" ).toString()
maxDiskLimit = System.getenv( "O2_MAX_DISK_LIMIT" ) as Double
minDiskLimit = System.getenv( "O2_MIN_DISK_LIMIT" ) as Double
password = System.getenv( "POSTGRES_PASSWORD" ).toString()
removeRasterUrl = "${ System.getenv( "STAGER_URL" ).toString() }/dataManager/removeRaster"
username = System.getenv( "POSTGRES_USER" ).toString()


def deleteStaleFiles = System.getenv( "DELETE_STALE_FILES" )
if ( deleteStaleFiles && Boolean.parseBoolean( deleteStaleFiles ) ) {
    deleteStaleFiles()
}

def deleteStaleEntries = System.getenv( "DELETE_STALE_ENTRIES" )
if ( deleteStaleEntries && Boolean.parseBoolean( deleteStaleEntries ) ) {
    deleteStaleEntries()
}


def totalDiskSpace = new File( diskVolume ).getTotalSpace()
println "Total Disk Space: ${ convertBytesToHumanReadable( totalDiskSpace ) }"
def freeDiskSpace = new File( diskVolume ).getUsableSpace()
println "Free Disk Space: ${ convertBytesToHumanReadable( freeDiskSpace ) }"
def usedDiskSpace = totalDiskSpace - freeDiskSpace
println "Used Disk Space: ${ convertBytesToHumanReadable( usedDiskSpace ) }"

println "Current disk usage: ${ (usedDiskSpace / totalDiskSpace * 100 as Double).trunc( 2 ) } %"

if (usedDiskSpace > totalDiskSpace * maxDiskLimit) {
    println "The maximum disk limit has been exceeded!"
    def numberOfBytesToDelete = (totalDiskSpace - freeDiskSpace) - minDiskLimit * totalDiskSpace
    println "I will try and delete approx. ${ convertBytesToHumanReadable( numberOfBytesToDelete ) } of data..."

    def numberOfBytesCounted = 0
    def filesToDelete = []

    def sql = Sql.newInstance( jdbcUrl, username, password, "org.postgresql.Driver" )
    def sqlCommand = "SELECT filename FROM raster_entry ORDER BY ingest_date ASC;"
    sql.eachRow( sqlCommand ) {
        def filename = it.filename

        filesToDelete.push( filename )

        def file = new File( filename )
        if ( file.exists() ) {
            numberOfBytesCounted += file.size()
        }

        if ( numberOfBytesToDelete < numberOfBytesCounted ) {
            sql.close()
            deleteFiles( filesToDelete )

            System.exit( 0 )
        }
    }

    println "I would have to delete everything and it still wouldn't be enough!"
    sql.close()

    System.exit( 0 )
}


def convertBytesToHumanReadable( bytes ) {
  def unit = 1024
  if ( bytes < unit ) { return bytes + " B" }
  def exp = (Math.log( bytes ) / Math.log( unit )) as Integer
  def size = "KMGTPE".charAt( exp - 1 )


  return "${ (bytes / Math.pow( unit, exp )).trunc( 2 ) } ${ size }B"
}

def deleteFiles( filenames ) {
    filenames.eachWithIndex {
        value, index ->
        println "Deleting raster entry ${ index } of ${ filenames.size() }: ${ value }..."
        def http = new HTTPBuilder( "${ removeRasterUrl }?deleteFiles=true&filename=${ value }" )
        http.request( POST ) { req ->
            response.failure = { resp, reader -> println "Failure: ${ reader }" }
            response.success = { resp, reader -> println "Success: ${ reader }" }
        }
    }
}

def deleteStaleEntries() {
    def sql = Sql.newInstance( jdbcUrl, username, password, "org.postgresql.Driver" )
    def sqlCommand = "SELECT filename FROM raster_entry ORDER BY ingest_date ASC;"
    sql.eachRow( sqlCommand ) {
        def filename = it.filename

        def file = new File( filename )
        if ( !file.exists() ) {
            deleteFiles([ filename ])
        }
    }
    sql.close()
}

def deleteStaleFiles() {
    def sql = Sql.newInstance( jdbcUrl, username, password, "org.postgresql.Driver" )
    def row = sql.firstRow( "SELECT filename FROM raster_entry ORDER BY ingest_date ASC;" )
    def oldestFileDate = new File( row.filename ).lastModified()

    def rasterEntryFiles = []
    sql.eachRow( "SELECT name FROM raster_entry_file;" ) {
        rasterEntryFiles.push( it.name )
    }

    sql.close()


    new File( diskVolume ).eachFileRecurse {
        def file = it

        if ( file.exists() && !file.directory ) {
            def lastModified = file.lastModified()
            if ( lastModified < oldestFileDate ) {
                println "Deleting stale file ${ file }..."
                file.delete()
            }
            else if ( rasterEntryFiles.indexOf( file ) < 0 )  {
                println "Deleting unused file ${ file }..."
                file.delete()
            }
        }
    }

    new File( diskVolume ).eachDirRecurse {
        def directory = it

        if (  file.list().size() == 0 ) {
            println "Deleting empty directory ${ directory }..."
            directory.delete()
        }
    }
}
