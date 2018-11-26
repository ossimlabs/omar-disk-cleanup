package omar.disk.cleanup

class DiskCleanupJob {

    def diskCleanupService
    DiskCleanupConfig diskCleanupConfig

    static triggers = {
        simple repeatInterval: 2000//grailsApplication.config.repeatInterval ?: 600000 
    }


    def execute() {
        diskCleanupService.cleanup()
    }
}
