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
        // this does a test and set
        if(!atomicIsRunning())
        {
            diskCleanupService.cleanup()

            setRunning(false)
        }
    }
}
