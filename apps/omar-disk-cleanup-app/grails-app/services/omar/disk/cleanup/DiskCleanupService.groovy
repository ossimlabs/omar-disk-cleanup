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
    
    private logJson(HashMap map)
    {
        log.info new groovy.json.JsonBuilder(map).toString()
    }
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
            File file = it as File;
            result += file.length()
        }
        result
    }
    private Sql newSqlInstance(HashMap dataSource)
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
    private def getFilesToDelete(File mainFile)
    {
        def filesToDelete = [mainFile]

        String fileNameOnly   = mainFile.getName()
        String fileWithoutExt = FilenameUtils.removeExtension(fileNameOnly)
        def files = new FileNameByRegexFinder().getFileNames(mainFile.parent,"${fileWithoutExt}*",fileNameOnly)
        files.each{filesToDelete <<  it }

        filesToDelete
    }
    private String localToIndexed(def volume, String file)
    {
        file.replace(volume.localRepository, volume.indexedRepository)
    }
    private String indexedToLocal(def volume, String file)
    {
        file.replace(volume.indexedRepository, volume.localRepository)
    }
    private def cleanupVolume(DiskCleanupConfig.Volume volume)
    {
        HashMap message = [:]
        String path         = volume.localRepository
        Double maxDiskLimit = volume.maxDiskLimit
        Double minDiskLimit = volume.minDiskLimit

        Long totalDiskSpace = new File( path ).getTotalSpace()
        Long freeDiskSpace  = new File( path ).getUsableSpace()
        Long usedDiskSpace  = totalDiskSpace - freeDiskSpace
        Boolean deleteStaleFiles = diskCleanupConfig.deleteStaleFiles
        message.totalDiskSpace = totalDiskSpace
        message.freeDiskSpace  = freeDiskSpace
        message.usedDiskSpace  = usedDiskSpace
        if ( deleteStaleFiles ) {
            log.info "Deleting Stale Files..."
            removeStaleFiles()
            log.info "Done Deleting Stale Files..."
        }

        Boolean deleteStaleEntries = diskCleanupConfig.deleteStaleEntries
        if ( deleteStaleEntries ) {
            log.info "Deleting Stale Entries..."
            removeStaleEntries()
            log.info "Done Deleting Stale Entries..."
        }
        log.info "Checking volume: ${volume.localRepository}"
        log.info "Total Disk Space: ${ convertBytesToHumanReadable( totalDiskSpace ) }"
        log.info "Free Disk Space: ${ convertBytesToHumanReadable( freeDiskSpace ) }"
        log.info "Used Disk Space: ${ convertBytesToHumanReadable( usedDiskSpace ) }"
        if ( usedDiskSpace > totalDiskSpace * maxDiskLimit ) {
            log.info "The maximum disk limit has been exceeded!"
            Long numberOfBytesToDelete = ( totalDiskSpace - freeDiskSpace ) - minDiskLimit * totalDiskSpace
            log.info "I will try and delete approx. ${ convertBytesToHumanReadable( numberOfBytesToDelete ) } of data..."
            message.numberOfBytesToDelete = numberOfBytesToDelete
            Long numberOfBytesCounted = 0

            Sql sql = newSqlInstance( volume.dbInfo.dataSource )

            Boolean done = false
            String sqlCommand
            
            sqlCommand = "SELECT ${volume.dbInfo.filenameColumn} FROM ${volume.dbInfo.tableName} ORDER BY ${volume.dbInfo.sortColumn} ASC;".toString()
            while(!done)
            {
                def filesToDelete = []
                def row = sql?.firstRow( sqlCommand )
                if(row){
                    String indexedFilename = row."${volume.dbInfo.filenameColumn}"
                    String filename = indexedToLocal(volume, indexedFilename)
                    File file = new File( filename )
                    if ( file.exists() ) 
                    {
                        numberOfBytesCounted += file.size()
                    }
                    filesToDelete = getFilesToDelete(file)
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
                            message.status = 400
                            message.statusMessage = "We were unsuccessfull in deleting all necessary files"
                        }
                    }
                    else
                    {
                        done = true
                    }

                     if ( numberOfBytesToDelete < numberOfBytesCounted ) {
                        done = true
                        message.status = 200
                        message.statusMessage = "Enough data was removed to free up disk space"
                     }
                }
                else
                {
                    done = true
                    message.status = 400
                    message.statusMessage = "All data was removed and can't free up anymore disk space"
                    //log.info "All data was removed!"
                }
            }

            sql?.close()
        }
        logJson(message)
    }
   @Synchronized
    def cleanup() {
        if(diskCleanupConfig.dryRun)
        {
            log.info "Performing Dry Run......${new Date()}"
        }
        diskCleanupConfig.volumes?.each{volume ->
            if(volume.localRepository?.exists())
            {
                cleanupVolume(volume)
            }
            else
            {
               logJson([status: 404, statusMessage: "Repository '${volume.localRepository}' does not exist and will be skipped"])
            }
        }
    }

    def convertBytesToHumanReadable( bytes ) {
        def unit = 1024
        if ( bytes < unit ) { return bytes + " B" }
        def exp = (Math.log( bytes ) / Math.log( unit )) as Integer
        def size = "KMGTPE".charAt( exp - 1 )


        return "${ (bytes / Math.pow( unit, exp )).trunc( 2 ) } ${ size }B"
    }
    private deleteFilesFromDisk(def filenames)
    {
        filenames?.each{
            File file = it as File
            if(file.exists())
            {
               file.delete() 
            }
        }
    }
    Boolean deleteFiles( DiskCleanupConfig.Volume volume, def filenames ) {
        Boolean result = false
        if(filenames)
        {
            String mainFile = filenames[0]
            String indexedMainFile = localToIndexed(volume, mainFile)
            if(volume.stagerUrl)
            {
                def removeRasterUrl = "${ volume.stagerUrl }/dataManager/removeRaster"
                if ( !diskCleanupConfig.dryRun ) {
                    def http = new HTTPBuilder( "${ removeRasterUrl }?deleteFiles=true&filename=${ indexedMainFile }" )
                    try{
                        http.request( POST ) { req ->
                            response.failure = { resp, reader -> 
                                logJson([status: 400, statusMessage: "${reader}"])
                            }
                            response.success = { resp, reader -> 
                                logJson([status: 200, statusMessage: "${reader}"])
                                deleteFilesFromDisk(filenames)
                                result = true
                            }
                        }
                    }
                    catch(java.net.ConnectException e)
                    {
                        logJson([status:400, statusMessage: "Unable to connect to url: ${removeRasterUrl}"])

                        result = false
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
                Sql sql = newSqlInstance(volume.dbInfo?.dataSource)
                String sqlCommand = "DELETE FROM ${volume.dbInfo.tableName} where ${volume.dbInfo.filenameColumn} = '${indexedMainFile}';"

                try{
                    if(!diskCleanupConfig.dryRun)
                    {
                        Boolean allDeleted = true
                        sql?.execute(sqlCommand)
                        deleteFilesFromDisk(filenames)
                        result = allDeleted;
                    }
                    else
                    {
                        sqlCommand = "SELECT ${volume.dbInfo.filenameColumn} FROM ${volume.dbInfo.tableName} where ${volume.dbInfo.filenameColumn} = '${indexedMainFile}';"                        
                        def row = sql.firstRow(sqlCommand)
                        if(row)
                        {
                            log.info "Dry Run: Removing file ${indexedMainFile} from table ${volume.dbInfo.tableName}"
                            log.info "Dry Run: Removing files from disk ${filenames}"
                        }
                        else
                        {
                            log.info "Dry run: file ${indexedMainFile} does not exist in the table"
                        }

                    }
                }
                catch(e)
                {
                    logJson([status:400, statusMessage: e.toString()])
                }
                sql?.close()
            }
        }

        result
    }

    def removeStaleEntries(DiskCleanupConfig.Volume volume) {
       String repository = volume.localRepository
       log.info "removeStaleEntries is not enabled at this time"
/*        if(volume.dbInfo?.dataSource)
        {
            def mainFiles = []
            def sql = newSqlInstance( volume.dbInfo?.dataSource )
            def sqlCommand = "SELECT ${volume.dbInfo.filenameColumn} FROM ${volume.dbInfo.tableName};"
            sql?.eachRow( sqlCommand ) {
                def filename = it.filename

                def file = new File( filename )
                File localFile = indexedToLocal(volume, file)
                if ( !file.exists() ) 
                {
                    def files = getFilesToDelete(new File(file))

                    if(volume.stagerUrl)
                    {
                        //deleteFiles(volume, files)
                    }
                    else
                    {
                        // this is for sanity check.  If the main file
                        // is not present then we make sure
                        // no support files are present
                        //
                        //deleteFilesFromDisk(files)
                    }
                }
            }
            sql?.close()
        }
        */
    }

    def removeStaleFiles(DiskCleanupConfig.Volume volume) {
       log.info "removeStaleFiles is not enabled at this time"
/*        
        def sqlCommand = "SELECT ${volume.dbInfo.filenameColumn} FROM ${volume.dbInfo.tableName} ORDER BY ${volume.dbInfo.lruDateColumn} DESC;"
        def filenames = []
        sql?.eachRow( sqlCommand ) {
            filenames.push( it.filename );
        }
        def newestFileDate = new File( filenames.last() ).lastModified()

        // set the stale date to be a day behind, just for good measure
        def newestStaleFileDate = new Date( newestFileDate ) - 1
        def staleFileDate = newestStaleFileDate.getTime()

        def rasterEntryFiles = []
        sql?.eachRow( "SELECT name FROM raster_entry_file;" ) {
            rasterEntryFiles.push( it.name )
        }
        sql?.close()

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
