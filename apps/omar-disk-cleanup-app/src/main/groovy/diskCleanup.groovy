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

// needed for deep clean mode
oldestFileDate = null

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

    def sql = Sql.newInstance( jdbcUrl, username, password, "org.postgresql.Driver" )
    def sqlCommand = "SELECT filename, keep_forever FROM raster_entry ORDER BY ingest_date ASC;"
    sql.eachRow( sqlCommand ) {
        def filename = it.filename

        if ( !it.keep_forever ) {
            if ( !oldestFileDate ) {
                oldestFileDate = new File( filename ).lastModified()
            }

            println "Deleting all files associated with ${ filename }..."
            //def http = new HTTPBuilder( "${ removeRasterUrl }?deleteFiles=true&filename=${ filename }" )
            //http.request( POST ) { req ->
            //    response.failure = { resp, reader -> println "Failure: ${ reader }" }
            //    response.success = { resp, reader -> println "Success: ${ reader }" }
            //}

            def file = new File( filename )
            if ( file.exists() ) {
                println "Uh oh! I couldn't delete ${ filename }, but I'm going to keep going."
            }
        }
        else {
            println "Looks like we are keeping ${ filename } forever."
        }

        def usedDiskSpacePercentage = (totalDiskSpace - new File( diskVolume ).getUsableSpace()) / totalDiskSpace as Double
        println "Disk space being used: ${ (usedDiskSpacePercentage * 100).trunc( 2 ) } %"

        if (usedDiskSpacePercentage < minDiskLimit) {
            println "I successfully deleted enough data!"
            sql.close()
            System.exit( 0 )
        }
    }

    println "Well, I deleted everything I could but it doesn't look like it was enough!"
    sql.close()

    def deepCleanMode = System.getenv( "DEEP_CLEAN" )
    if ( deepCleanMode && Boolean.parseBoolean( deepCleanMode ) ) {
        deepClean()
    }

    System.exit( 1 )
}


def convertBytesToHumanReadable( bytes ) {
  def unit = 1024
  if ( bytes < unit ) { return bytes + " B" }
  def exp = (Math.log( bytes ) / Math.log( unit )) as Integer
  def size = "KMGTPE".charAt( exp - 1 )


  return "${ (bytes / Math.pow( unit, exp )).trunc( 2 ) } ${ size }B"
}

def deepClean() {
    new File( diskVolume ).eachFileRecurse {
        def file = it
        println file.absolutePath
        def lastModified = file.lastModified()
        println lastModified
        println oldestFileDate
        if ( lastModified < oldestFileDate ) {
            file.delete()
        }
    }
}
