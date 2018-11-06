package omar.disk.cleanup


import groovy.sql.Sql
import groovyx.net.http.HTTPBuilder
import static groovyx.net.http.Method.POST


class DiskCleanupService {

    def dataSource
    def grailsApplication


    def cleanup() {
        def dryRun = grailsApplication.config.dryRun

        def diskVolume = grailsApplication.config.diskVolume
        def maxDiskLimit = grailsApplication.config.maxDiskLimit
        def minDiskLimit = grailsApplication.config.minDiskLimit


        def deleteStaleFiles = grailsApplication.config.deleteStaleFiles
        if ( deleteStaleFiles ) {
            println "Deleting Stale Files..."
            removeStaleFiles()
            println "Done Deleting Stale Files..."
        }

        def deleteStaleEntries = grailsApplication.config.deleteStaleEntries
        if ( deleteStaleEntries ) {
            println "Deleting Stale Entries..."
            removeStaleEntries()
            println "Done Deleting Stale Entries..."
        }


        def totalDiskSpace = new File( diskVolume ).getTotalSpace()
        println "Total Disk Space: ${ convertBytesToHumanReadable( totalDiskSpace ) }"
        def freeDiskSpace = new File( diskVolume ).getUsableSpace()
        println "Free Disk Space: ${ convertBytesToHumanReadable( freeDiskSpace ) }"
        def usedDiskSpace = totalDiskSpace - freeDiskSpace
        println "Used Disk Space: ${ convertBytesToHumanReadable( usedDiskSpace ) }"

        println "Current disk usage: ${ (usedDiskSpace / totalDiskSpace * 100 as Double).trunc( 2 ) } %"

        if ( usedDiskSpace > totalDiskSpace * maxDiskLimit ) {
            println "The maximum disk limit has been exceeded!"
            def numberOfBytesToDelete = ( totalDiskSpace - freeDiskSpace ) - minDiskLimit * totalDiskSpace
            println "I will try and delete approx. ${ convertBytesToHumanReadable( numberOfBytesToDelete ) } of data..."

            def numberOfBytesCounted = 0
            def filesToDelete = []

            def sql = Sql.newInstance( dataSource )
            def sqlCommand = "SELECT filename FROM raster_entry ORDER BY ingest_date ASC;"
            sql.eachRow( sqlCommand ) {
                def filename = it.filename

                filesToDelete.push( filename )

                def file = new File( filename )
                if ( file.exists() ) {
                    numberOfBytesCounted += file.size()
                }

                if ( numberOfBytesToDelete < numberOfBytesCounted ) {
                    deleteFiles( filesToDelete )

                    sql.close()
                    System.exit( 0 )
                }
            }

            sql.close()
            println "I would have to delete everything and it still wouldn't be enough!"
        }
    }

    def convertBytesToHumanReadable( bytes ) {
        def unit = 1024
        if ( bytes < unit ) { return bytes + " B" }
        def exp = (Math.log( bytes ) / Math.log( unit )) as Integer
        def size = "KMGTPE".charAt( exp - 1 )


        return "${ (bytes / Math.pow( unit, exp )).trunc( 2 ) } ${ size }B"
    }

    def deleteFiles( filenames ) {
        def removeRasterUrl = "${ grailsApplication.config.stagerUrl }/dataManager/removeRaster"

        filenames.eachWithIndex {
            value, index ->
            println "Deleting raster entry ${ index + 1 } of ${ filenames.size() }: ${ value }..."
            if ( !grailsApplication.config.dryRun ) {
                def http = new HTTPBuilder( "${ removeRasterUrl }?deleteFiles=true&filename=${ value }" )
                http.request( POST ) { req ->
                    response.failure = { resp, reader -> println "Failure: ${ reader }" }
                    response.success = { resp, reader -> println "Success: ${ reader }" }
                }
            }
        }
    }

    def removeStaleEntries() {
        def sql = Sql.newInstance( dataSource )
        def sqlCommand = "SELECT filename FROM raster_entry;"
        sql.eachRow( sqlCommand ) {
            def filename = it.filename

            def file = new File( filename )
            if ( !file.exists() ) {
                deleteFiles([ filename ])
            }
        }
        sql.close()
    }

    def removeStaleFiles() {
        def sql = Sql.newInstance( dataSource )
        def sqlCommand = "SELECT filename FROM raster_entry ORDER BY ingest_date ASC;"
        def filenames = []
        sql.eachRow( sqlCommand ) {
            filenames.push( it.filename );
        }
        def newestFileDate = new File( filenames.last() ).lastModified()

        // set the stale date to be a day behind, just for good measure
        def newestStaleFileDate = new Date( newestFileDate ) - 1
        def staleFileDate = newestStaleFileDate.getTime()

        def rasterEntryFiles = []
        sql.eachRow( "SELECT name FROM raster_entry_files;" ) {
            rasterEntryFiles.push( it.name )
        }
        sql.close()

        def diskVolume = grailsApplication.config.diskVolume
        def files = []
        new File( diskVolume ).eachFileRecurse {
            def file = it

            if ( !file.directory ) {
                def lastModified = file.lastModified()
                if ( lastModified < staleFileDate ) {
                    if ( rasterEntryFiles.indexOf( file ) < 0 )  {
                        if ( filenames.indexOf( file ) < 0 ) {
                            files.push( it )
                        }
                    }
                }
            }
        }
        files.each {
            println "Deleting stale file ${ it }..."
            if ( !grailsApplication.config.dryRun ) {
                it.delete()
            }
        }

        // clean up empty directories
        def directories = []
        new File( diskVolume ).eachDirRecurse {
            def directory = it

            if ( directory.list().size() == 0 ) {
                directories.push( directory )
            }
        }
        directories.each {
            println "Deleting empty directory ${ it }..."
            if ( !grailsApplication.config.dryRun ) {
                it.delete()
            }
        }
    }
}
