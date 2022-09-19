import io.prometheus.client.Gauge
import io.prometheus.client.Info
import org.quartz.Scheduler
import org.quartz.Trigger

class PrometheusStats {
    val schedulerStatus = Info.build().name("scheduler_status").help("Status of the scheduler").register()
    val activeJobs = Gauge.build()
        .name("scheduler_active_jobs").help("Number of active jobs").register()
    val totalRanJobs = Gauge.build()
        .name("scheduler_ran_jobs").help("Number of total ran jobs").register()
    val coursesNextFireTime =
        Info.build().name("scheduler_courses_next_fire").help("When courses fetching will be fired").register()
    val studentsNextFireTime =
        Info.build().name("scheduler_students_next_fire").help("When students fetching will be fired").register()

    private fun getStatus(scheduler: Scheduler): String {
        return if (scheduler.isInStandbyMode) "STANDBY"
        else if (scheduler.isStarted) "RUNNING"
        else if (scheduler.isShutdown) "DOWN"
        else "UNKNOWN STATUS"
    }

    fun updateStatus(scheduler: Scheduler) {
        schedulerStatus.info("status", getStatus(scheduler))
    }

    fun updateActiveJobs(scheduler: Scheduler) {
        activeJobs.set(scheduler.currentlyExecutingJobs.size.toDouble())
    }

    fun updateTotalRanJobs(scheduler: Scheduler) {
        totalRanJobs.set(scheduler.metaData.numberOfJobsExecuted.toDouble())
    }

    fun updateCoursesNextFireTime(trigger: Trigger) {
        coursesNextFireTime.info("date", trigger.nextFireTime.toInstant().epochSecond.toString())
    }

    fun updateStudentsNextFireTime(trigger: Trigger) {
        studentsNextFireTime.info("date", trigger.nextFireTime.toInstant().epochSecond.toString())
    }
}