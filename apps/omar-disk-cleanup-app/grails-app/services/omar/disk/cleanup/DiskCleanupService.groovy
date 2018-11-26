package omar.disk.cleanup

import org.apache.commons.io.FilenameUtils
import groovy.sql.Sql
import groovyx.net.http.HTTPBuilder
import static groovyx.net.http.Method.POST
import omar.disk.cleanup.DiskCleanupConfig
import groovy.transform.Synchronized

class DiskCleanupService {

    def dataSource
    def grailsApplication
    DiskCleanupConfig diskCleanupConfig

    public static long fileSize(File file) {
        long length = 0;
        if(file.isFile())
        {
            length += file.length();
        }
        else
        {
           file.listFiles()?.each{
               length += fileSize(it)
           } 
        }
        length
    }    
    private Long byteCount(def files)
    {
        Long result = 0
        files.each{
            File file = new File(it);
            result += file.length()
        }
        result
    }
    private Sql newInstance(HashMap dataSource)
    {
        Sql result
        try{
            result = Sql.newInstance( dataSource )
        }
        catch(e)
        {
            println e
            result = null
        }

        result
    }
    def private cleanupVolume(DiskCleanupConfig.Volume volume)
    {
        String path = volume.repository
        Double maxDiskLimit = volume.maxDiskLimitPercent
        Double minDiskLimit = volume.minDiskLimitPercent

        Long totalDiskSpace = new File( path ).getTotalSpace()
        Long freeDiskSpace = new File( path ).getUsableSpace()
        Long usedDiskSpace = totalDiskSpace - freeDiskSpace
        log.info "Checking volume: ${volume.repository}"
        log.info "Total Disk Space: ${ convertBytesToHumanReadable( totalDiskSpace ) }"
        log.info "Free Disk Space: ${ convertBytesToHumanReadable( freeDiskSpace ) }"
        log.info "Used Disk Space: ${ convertBytesToHumanReadable( usedDiskSpace ) }"
        if ( usedDiskSpace > totalDiskSpace * maxDiskLimit ) {
            log.info "The maximum disk limit has been exceeded!"
            def numberOfBytesToDelete = ( totalDiskSpace - freeDiskSpace ) - minDiskLimit * totalDiskSpace
            log.info "I will try and delete approx. ${ convertBytesToHumanReadable( numberOfBytesToDelete ) } of data..."

            def numberOfBytesCounted = 0

            def sql = newInstance( volume.dbInfo.dataSource )

            Boolean done = false
            String sqlCommand = "SELECT ${volume.dbInfo.filenameColumn} FROM ${volume.dbInfo.tableName} ORDER BY ${volume.dbInfo.lruDateColumn} ASC;".toString()
            while(!done)
            {
                def filesToDelete = []
                def row = sql?.firstRow( sqlCommand )
                if(row){
                    String filename = row."${volume.dbInfo.filenameColumn}"
                    File file = new File( filename )
                    if ( file.exists() ) {

                        numberOfBytesCounted += file.size()
                    }
                    String fileNameOnly   = file.getName()
                    String fileWithoutExt = FilenameUtils.removeExtension(fileNameOnly)
                    def files = new FileNameByRegexFinder().getFileNames(file.parent,"${fileWithoutExt}*",fileNameOnly)
                    println "FILES = ${files}"
                    filesToDelete << filename
                    files.each{filesToDelete <<  it }
                    Long bytes = byteCount(filesToDelete)
                    if(bytes)
                    {
                        if(deleteFiles(volume, filesToDelete))
                        {
                            numberOfBytesCounted+=bytes;
                        }
                        else
                        {
                            // if we had a problem then just exit out and we will
                            // try during the next run of the job
                            //
                            done = true
                        }
                    }
                    else
                    {
                        done = true
                    }

                     if ( numberOfBytesToDelete < numberOfBytesCounted ) {
                        done = true
                     }
                }
                else
                {
                    done = true
                    log.info "All data was removed!"
                }
            }

            sql?.close()
        }
    }
   @Synchronized
    def cleanup() {
        if(diskCleanupConfig.dryRun)
        {
            log.info "Performing Dry Run......"
        }
        diskCleanupConfig.volumes?.each{volume ->
            cleanupVolume(volume)
        }

/*
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
        */
    }

    def convertBytesToHumanReadable( bytes ) {
        def unit = 1024
        if ( bytes < unit ) { return bytes + " B" }
        def exp = (Math.log( bytes ) / Math.log( unit )) as Integer
        def size = "KMGTPE".charAt( exp - 1 )


        return "${ (bytes / Math.pow( unit, exp )).trunc( 2 ) } ${ size }B"
    }

    Boolean deleteFiles( DiskCleanupConfig.Volume volume, filenames ) {
        Boolean result = false
        if(filenames)
        {
            if(volume.stagerUrl)
            {
                String mainFile = filenames[0]
                def removeRasterUrl = "${ volume.stagerUrl }/dataManager/removeRaster"
                if ( !diskCleanupConfig.dryRun ) {
                    def http = new HTTPBuilder( "${ removeRasterUrl }?deleteFiles=true&filename=${ mainFile }" )
                    http.request( POST ) { req ->
                        response.failure = { resp, reader -> 
                            log.error "Failure: ${ reader }" 
                        }
                        response.success = { resp, reader -> 
                            log.info "Success: ${ reader }" 
                            filenames.each{
                                File file = new File(it)
                                if(file.exists())
                                {
                                    file.delete() 
                                }
                            }
                            result = true
                        }
                    }
                }
                else
                {
                    log.info "Dry Run Deleting files: ${filenames}"
                    result = true
                }
            }
            else
            {
                // non native delete
                // Note cascade deletes must be enabled or there will be problems
                //
                log.info "Non native delete not supported yet"
            }
        }

        result
    }

    def removeStaleEntries(DiskCleanupConfig.Volume volume) {
       log.info "removeStaleEntries is not enabled at this time"

/*
        if(volume.dbInfo?.dataSource)
        {
            def sql = Sql.newInstance( volume.dbInfo?.dataSource )
            def sqlCommand = "SELECT ${volume.dbInfo.filenameColumn} FROM ${volume.dbInfo.tableName};"
            sql.eachRow( sqlCommand ) {
                def filename = it.filename

                def file = new File( filename )
                if ( !file.exists() ) {
                    deleteFiles([ filename ])
                }
            }
            sql.close()
        }
*/
    }

    def removeStaleFiles(DiskCleanupConfig.Volume volume) {
       String repository = volume.repository
       log.info "removeStaleFiles is not enabled at this time"
/*        
        def sql = Sql.newInstance( volume.dbInfo?.dataSource )
        def sqlCommand = "SELECT ${volume.dbInfo.filenameColumn} FROM ${volume.dbInfo.tableName} ORDER BY ${volume.dbInfo.lruDateColumn} ASC;"
        def filenames = []
        sql.eachRow( sqlCommand ) {
            filenames.push( it.filename );
        }
        def newestFileDate = new File( filenames.last() ).lastModified()

        // set the stale date to be a day behind, just for good measure
        def newestStaleFileDate = new Date( newestFileDate ) - 1
        def staleFileDate = newestStaleFileDate.getTime()

        def rasterEntryFiles = []
        sql.eachRow( "SELECT name FROM raster_entry_file;" ) {
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
 */
    }
}
