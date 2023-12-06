import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import org.quartz.*
import org.quartz.JobBuilder.newJob
import org.quartz.SimpleScheduleBuilder.simpleSchedule
import org.quartz.TriggerBuilder.newTrigger
import org.quartz.impl.StdSchedulerFactory
import org.quartz.impl.matchers.GroupMatcher
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
                    .withIntervalInHours(30 * 24)
                    .repeatForever()
                    .withMisfireHandlingInstructionFireNow()
            )
            .build()

        scheduler.scheduleJob(studentsJob, studentsTrigger)

        val roomsJob: JobDetail = newJob(UpdateRoomsJob::class.java)
            .withIdentity("update-rooms", "update-rooms")
            .build()

        val roomsTrigger: Trigger = newTrigger()
            .withIdentity("update-rooms-trigger", "update-rooms")
            .startNow()
            .withSchedule(
                simpleSchedule()
                    .withIntervalInHours(2)
                    .repeatForever()
                    .withMisfireHandlingInstructionFireNow()
            )
            .build()

        scheduler.scheduleJob(roomsJob, roomsTrigger)

        val cleanCourseAlertJob: JobDetail = newJob(CleanCourseAlertJob::class.java)
            .withIdentity("clean-course-alert", "clean-course-alert")
            .build()

        val cleanCourseAlertTrigger: Trigger = newTrigger()
            .withIdentity("clean-course-trigger", "clean-course-alert")
            .startNow()
            .withSchedule(
                CronScheduleBuilder.weeklyOnDayAndHourAndMinute(DateBuilder.MONDAY, 0, 0)
                    .withMisfireHandlingInstructionFireAndProceed()
            )
            .build()

        scheduler.scheduleJob(cleanCourseAlertJob, cleanCourseAlertTrigger)

        embeddedServer(Netty, port = 8080) {
            val appMicrometerRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
            appMicrometerRegistry.prometheusRegistry.register(PrometheusStats.schedulerStatus)
            appMicrometerRegistry.prometheusRegistry.register(PrometheusStats.activeJobs)
            appMicrometerRegistry.prometheusRegistry.register(PrometheusStats.totalRanJobs)
            appMicrometerRegistry.prometheusRegistry.register(PrometheusStats.coursesNextFireTime)
            appMicrometerRegistry.prometheusRegistry.register(PrometheusStats.studentsNextFireTime)
            appMicrometerRegistry.prometheusRegistry.register(PrometheusStats.coursesDuration)
            appMicrometerRegistry.prometheusRegistry.register(PrometheusStats.studentsDuration)
            appMicrometerRegistry.prometheusRegistry.register(PrometheusStats.coursesError)
            appMicrometerRegistry.prometheusRegistry.register(PrometheusStats.studentsError)
            appMicrometerRegistry.prometheusRegistry.register(PrometheusStats.coursesGroupsDuration)
            appMicrometerRegistry.prometheusRegistry.register(PrometheusStats.roomsNextFireTime)
            appMicrometerRegistry.prometheusRegistry.register(PrometheusStats.roomsDuration)
            appMicrometerRegistry.prometheusRegistry.register(PrometheusStats.roomsError)
            appMicrometerRegistry.prometheusRegistry.register(PrometheusStats.cleanCourseAlertNextFireTime)
            appMicrometerRegistry.prometheusRegistry.register(PrometheusStats.cleanCourseAlertDuration)
            appMicrometerRegistry.prometheusRegistry.register(PrometheusStats.cleanCourseAlertError)
            install(MicrometerMetrics) {
                registry = appMicrometerRegistry
            }
            authentication {
                basic(name = "prometheus") {
                    realm = "prometheus"
                    validate { credentials ->
                        if (credentials.name == System.getenv("CC_METRICS_PROMETHEUS_USER") && credentials.password == System.getenv(
                                "CC_METRICS_PROMETHEUS_PASSWORD"
                            )
                        ) {
                            UserIdPrincipal(credentials.name)
                        } else {
                            null
                        }
                    }
                }

                basic(name = "run") {
                    realm = "run"
                    validate { credentials ->
                        if (credentials.name == System.getenv("RUN_USER") && credentials.password == System.getenv("RUN_PASSWORD")) {
                            UserIdPrincipal(credentials.name)
                        } else {
                            null
                        }
                    }
                }
            }
            routing {
                get("/") {
                    call.respondText("Hello, world!")
                }
                authenticate("prometheus") {
                    get("/metrics") {
                        PrometheusStats.updateStatus(scheduler)
                        PrometheusStats.updateActiveJobs(scheduler)
                        PrometheusStats.updateTotalRanJobs(scheduler)
                        PrometheusStats.updateCoursesNextFireTime(scheduler)
                        PrometheusStats.updateStudentsNextFireTime(scheduler)
                        PrometheusStats.updateRoomsNextFireTime(scheduler)
                        PrometheusStats.updateCleanCourseAlertNextFireTime(scheduler)
                        call.respond(appMicrometerRegistry.scrape())
                    }
                }
                authenticate("run") {
                    get("/run/students") {
                        scheduler.getJobKeys(GroupMatcher.anyGroup()).filter { it.name == "update-students" }.forEach {
                            scheduler.triggerJob(it)
                        }
                        call.respond(HttpStatusCode.OK)
                    }
                    get("/run/courses") {
                        scheduler.getJobKeys(GroupMatcher.anyGroup()).filter { it.name == "update-courses" }.forEach {
                            scheduler.triggerJob(it);
                        }
                        call.respond(HttpStatusCode.OK)
                    }
                    get("/run/rooms") {
                        scheduler.getJobKeys(GroupMatcher.anyGroup()).filter { it.name == "update-rooms" }.forEach {
                            scheduler.triggerJob(it);
                        }
                        call.respond(HttpStatusCode.OK)
                    }
                    get("/run/clean/courses") {
                        scheduler.getJobKeys(GroupMatcher.anyGroup()).filter { it.name == "clean-course-alert" }
                            .forEach {
                                scheduler.triggerJob(it);
                            }
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }
        }.start(wait = false)

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
