package omar.disk.cleanup
import org.quartz.TriggerKey
import org.quartz.JobKey


class BootStrap {
    def grailsApplication
    def diskCleanupConfig
    def quartzScheduler
    def init = { servletContext ->
      org.quartz.TriggerKey triggerKey = new TriggerKey("DiskCleanupTrigger", "DiskCleanupGroup")
      def trigger = quartzScheduler?.getTrigger(triggerKey)
      def jobDetails = quartzScheduler?.getJobDetail(new JobKey("DiskCleanupTrigger", "DiskCleanupGroup"))
      if(trigger&&diskCleanupConfig)
      {
        trigger.repeatInterval = diskCleanupConfig.repeatInterval as Long
        Date nextFireTime=quartzScheduler?.rescheduleJob(triggerKey, trigger)
      }
    }
    def destroy = {
    }
}
