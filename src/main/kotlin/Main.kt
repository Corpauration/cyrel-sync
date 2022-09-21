import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
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
            install(MicrometerMetrics) {
                registry = appMicrometerRegistry
            }
            routing {
                get("/") {
                    call.respondText("Hello, world!")
                }
                get("/metrics") {
                    PrometheusStats.updateStatus(scheduler)
                    PrometheusStats.updateActiveJobs(scheduler)
                    PrometheusStats.updateTotalRanJobs(scheduler)
                    PrometheusStats.updateCoursesNextFireTime(scheduler)
                    PrometheusStats.updateStudentsNextFireTime(scheduler)
                    call.respond(appMicrometerRegistry.scrape())
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
