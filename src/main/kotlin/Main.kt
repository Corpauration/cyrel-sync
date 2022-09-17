import org.quartz.JobBuilder.newJob
import org.quartz.JobDetail
import org.quartz.Scheduler
import org.quartz.SchedulerException
import org.quartz.SimpleScheduleBuilder.simpleSchedule
import org.quartz.Trigger
import org.quartz.TriggerBuilder.newTrigger

import org.quartz.impl.StdSchedulerFactory
import java.util.*


fun main() {
    Class.forName("org.postgresql.Driver")
    try {
        // Grab the Scheduler instance from the Factory
        val scheduler: Scheduler = StdSchedulerFactory.getDefaultScheduler()

        // and start it off
        scheduler.start()

        processSigTerm(scheduler)

        val coursesJob: JobDetail = newJob(UpdateCoursesJob::class.java)
            .withIdentity("update-courses", "update-courses")
            .build()

        val date = Date()
        date.minutes += 5
        val coursesTrigger: Trigger = newTrigger()
            .withIdentity("update-courses-trigger", "update-courses")
            .startNow()
            .withSchedule(
                simpleSchedule()
                    .withIntervalInHours(2)
                    .repeatForever()
                    .withMisfireHandlingInstructionFireNow()
            )
            .build()

        scheduler.scheduleJob(coursesJob, coursesTrigger)

        val studentsJob: JobDetail = newJob(UpdateCytechStudentsJob::class.java)
            .withIdentity("update-students", "update-students")
            .build()

        val studentsTrigger: Trigger = newTrigger()
            .withIdentity("update-students-trigger", "update-students")
            .startNow()
            .withSchedule(
                simpleSchedule()
                    .withIntervalInHours(96)
                    .repeatForever()
                    .withMisfireHandlingInstructionFireNow()
            )
            .build()

        scheduler.scheduleJob(studentsJob, studentsTrigger)
//        scheduler.shutdown()
    } catch (se: SchedulerException) {
        se.printStackTrace()
    }
}

fun processSigTerm(scheduler: Scheduler) {
    val mainThread = Thread.currentThread()
    Runtime.getRuntime().addShutdownHook(object : Thread() {
        override fun run() {
            try {
                if (scheduler.isStarted) {
                    scheduler.shutdown(true)
                }
                mainThread.join()
            } catch (ex: InterruptedException) {
                ex.printStackTrace()
            }
        }
    })
}
