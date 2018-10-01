package omar.disk.cleanup

class DiskCleanupJob {

    def diskCleanupService

    static triggers = {
        simple repeatInterval: 600000 // execute job every 10 minutes
    }


    def execute() {
        diskCleanupService.cleanup()
    }
}
