package omar.disk.cleanup


import groovyx.net.http.HTTPBuilder
import static groovyx.net.http.Method.POST


class DiskCleanupJob {

    def diskCleanupService

    static triggers = {
        simple repeatInterval: 600000 // execute job every 10 minutes
    }


    def execute() {
        diskCleanupService.cleanup()
    }
}
