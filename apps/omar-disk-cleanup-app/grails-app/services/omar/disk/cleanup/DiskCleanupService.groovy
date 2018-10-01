package omar.disk.cleanup


import groovyx.net.http.HTTPBuilder
import static groovyx.net.http.Method.POST
import omar.raster.RasterEntry


class DiskCleanupService {

    def grailsApplication


    def cleanup() {
        def diskVolume = grailsApplication.config.diskVolume
        def maxDiskLimit = grailsApplication.config.maxDiskLimit
        def minDiskLimit = grailsApplication.config.minDiskLimit


        def deleteStaleFiles = grailsApplication.config.deleteStaleFiles
        if ( deleteStaleFiles ) {
            removeStaleFiles()
        }

        def deleteStaleEntries = grailsApplication.config.deleteStaleEntries
        if ( deleteStaleEntries && Boolean.parseBoolean( deleteStaleEntries ) ) {
            removeStaleEntries()
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

            RasterEntry.each {
                def filename = it.filename

                filesToDelete.push( filename )

                def file = new File( filename )
                if ( file.exists() ) {
                    numberOfBytesCounted += file.size()
                }

                if ( numberOfBytesToDelete < numberOfBytesCounted ) {
                    deleteFiles( filesToDelete )

                    System.exit( 0 )
                }
            }

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
            println "Deleting raster entry ${ index } of ${ filenames.size() }: ${ value }..."
            def http = new HTTPBuilder( "${ removeRasterUrl }?deleteFiles=true&filename=${ value }" )
            http.request( POST ) { req ->
                response.failure = { resp, reader -> println "Failure: ${ reader }" }
                response.success = { resp, reader -> println "Success: ${ reader }" }
            }
        }
    }

    def removeStaleEntries() {
        RasterEntry.each {
            def filename = it.filename

            def file = new File( filename )
            if ( !file.exists() ) {
                deleteFiles([ filename ])
            }
        }
    }

    def removeStaleFiles() {
        println RasterEntry.each {
            println it.properties
        }
        //def filenames = RasterEntry.list( sort: "ingest_date", order: "asc" ).collect({ it.filename })
        /*def newestFileDate = new File( filenames.last() ).lastModified()

        // set the stale date to be a day behind, just for good measure
        def newestStaleFileDate = new Date( newestFileDate ) - 1
        def staleFileDate = newestStaleFileDate.getTime()

        def rasterEntryFiles = RasterEntryFile.list().collect({ it.name })

        def diskVolume = grailsApplication.config.diskVolume
        new File( diskVolume ).eachFileRecurse {
            def file = it

            if ( file.exists() && !file.directory ) {
                def lastModified = file.lastModified()
                if ( lastModified < staleFileDate ) {
                    if ( rasterEntryFiles.indexOf( file ) < 0 )  {
                        if ( filenames.indexOf( file ) < 0 ) {
                            println "Deleting stale file ${ file }..."
                            file.delete()
                        }
                    }
                }
            }
        }

        // clean up empty directories
        new File( diskVolume ).eachDirRecurse {
            def directory = it

            if (  file.list().size() == 0 ) {
                println "Deleting empty directory ${ directory }..."
                directory.delete()
            }
        }*/
    }
}
