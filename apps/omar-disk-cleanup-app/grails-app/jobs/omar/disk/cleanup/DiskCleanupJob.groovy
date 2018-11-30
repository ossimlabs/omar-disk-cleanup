package omar.disk.cleanup
import groovy.transform.Synchronized

class DiskCleanupJob {
    static Boolean running = false;
    private final myLock = new Object()
    def diskCleanupService
    DiskCleanupConfig diskCleanupConfig

    static triggers = {
        simple repeatInterval: 60000, name: 'DiskCleanupTrigger', group: 'DiskCleanupGroup' 
    }
    
    @Synchronized("myLock")
    Boolean atomicIsRunning()
    {
        Boolean result = running;
    
        if(!running)
        {
            running = true
        }
    
        result
    }
    @Synchronized("myLock")
    void setRunning(Boolean value)
    {
        running = value
    }

    def execute() {
        // this does a test and set.  We only want to run again
        // if the previous run has finished
        if(!atomicIsRunning())
        {
            try{
                diskCleanupService.cleanup()
            }
            catch(e)
            {
                log.error e.toString()
            }
            setRunning(false)
        }
    }
}
