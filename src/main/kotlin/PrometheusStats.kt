import io.prometheus.client.Gauge
import io.prometheus.client.Info
import org.quartz.Scheduler
import org.quartz.impl.matchers.GroupMatcher
import java.util.*

class PrometheusStats {
    companion object {
        val schedulerStatus = Info.build().name("scheduler_status").help("Status of the scheduler").register()
        val activeJobs = Gauge.build()
            .name("scheduler_active_jobs").help("Number of active jobs").register()
        val totalRanJobs = Gauge.build()
            .name("scheduler_ran_jobs").help("Number of total ran jobs").register()
        val coursesNextFireTime =
            Gauge.build().name("scheduler_courses_next_fire").help("When courses fetching will be fired").register()
        val studentsNextFireTime =
            Gauge.build().name("scheduler_students_next_fire").help("When students fetching will be fired").register()

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

        fun updateCoursesNextFireTime(scheduler: Scheduler) {
            scheduler.getTriggerKeys(GroupMatcher.anyGroup()).filter { it.name == "update-courses-trigger" }.forEach {
                coursesNextFireTime.set(
                    scheduler.getTrigger(it).getFireTimeAfter(Date(System.currentTimeMillis() + 1000)).time.toDouble()
                )
                println(scheduler.getTrigger(it).getFireTimeAfter(Date(System.currentTimeMillis() + 1000)).time)
            }
        }

        fun updateStudentsNextFireTime(scheduler: Scheduler) {
            scheduler.getTriggerKeys(GroupMatcher.anyGroup()).filter { it.name == "update-students-trigger" }.forEach {
                studentsNextFireTime.set(
                    scheduler.getTrigger(it).getFireTimeAfter(Date(System.currentTimeMillis() + 1000)).time.toDouble()
                )
            }
        }
    }
}