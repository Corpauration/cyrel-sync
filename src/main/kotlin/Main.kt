import org.quartz.JobBuilder.newJob
import org.quartz.JobDetail
import org.quartz.Scheduler
import org.quartz.SchedulerException
import org.quartz.SimpleScheduleBuilder.simpleSchedule
import org.quartz.Trigger
import org.quartz.TriggerBuilder.newTrigger

import org.quartz.impl.StdSchedulerFactory


fun main() {
    Class.forName("org.postgresql.Driver")
    try {
        // Grab the Scheduler instance from the Factory
        val scheduler: Scheduler = StdSchedulerFactory.getDefaultScheduler()

        // and start it off
        scheduler.start()
        // define the job and tie it to our HelloJob class

        // define the job and tie it to our HelloJob class
        val job: JobDetail = newJob(UpdateCoursesJob::class.java)
            .withIdentity("update-courses", "update-courses")
            .build()

        // Trigger the job to run now, and then repeat every 40 seconds

        // Trigger the job to run now, and then repeat every 40 seconds
        val trigger: Trigger = newTrigger()
            .withIdentity("update-courses-trigger", "update-courses")
            .startNow()
            .withSchedule(
                simpleSchedule()
                    .withIntervalInMinutes(3)
                    .repeatForever()
            )
            .build()

        // Tell quartz to schedule the job using our trigger

        // Tell quartz to schedule the job using our trigger
        scheduler.scheduleJob(job, trigger)
//        scheduler.shutdown()
    } catch (se: SchedulerException) {
        se.printStackTrace()
    }
}