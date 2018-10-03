package omar.disk.cleanup

class DiskCleanupJob {

    def diskCleanupService

    static triggers = {
        simple repeatInterval: grailsApplication.config.repeatInterval ?: 600000 
    }


    def execute() {
        diskCleanupService.cleanup()
    }
}
