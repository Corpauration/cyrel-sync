import io.prometheus.client.*
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
        val coursesDuration = Histogram.build()
            .exponentialBuckets(1.0, 1.5, 25)
            .name("scheduler_courses_duration").help("Duration of courses task").register()
        val studentsDuration = Histogram.build()
            .exponentialBuckets(1.0, 1.5, 25)
            .name("scheduler_students_duration").help("Duration of students task").register()
        val coursesError = Counter.build()
            .name("scheduler_courses_errors").help("Total errors of courses task.").register()
        val studentsError = Counter.build()
            .name("scheduler_students_errors").help("Total errors of students task.").register()
        var coursesGroupsDuration = Summary.build()
            .maxAgeSeconds(5 * 60)
            .ageBuckets(10)
            .name("scheduler_courses_groups_duration")
            .help("Duration of a group fetch from courses task")
            .register()
        val roomsNextFireTime =
            Gauge.build().name("scheduler_rooms_next_fire").help("When rooms task will be fired").register()
        val roomsDuration = Histogram.build()
            .exponentialBuckets(1.0, 1.5, 25)
            .name("scheduler_rooms_duration").help("Duration of rooms task").register()
        val roomsError = Counter.build()
            .name("scheduler_rooms_errors").help("Total errors of rooms task.").register()


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
            }
        }

        fun updateStudentsNextFireTime(scheduler: Scheduler) {
            scheduler.getTriggerKeys(GroupMatcher.anyGroup()).filter { it.name == "update-students-trigger" }.forEach {
                studentsNextFireTime.set(
                    scheduler.getTrigger(it).getFireTimeAfter(Date(System.currentTimeMillis() + 1000)).time.toDouble()
                )
            }
        }

        fun updateRoomsNextFireTime(scheduler: Scheduler) {
            scheduler.getTriggerKeys(GroupMatcher.anyGroup()).filter { it.name == "update-rooms-trigger" }.forEach {
                roomsNextFireTime.set(
                    scheduler.getTrigger(it).getFireTimeAfter(Date(System.currentTimeMillis() + 1000)).time.toDouble()
                )
            }
        }
    }
}