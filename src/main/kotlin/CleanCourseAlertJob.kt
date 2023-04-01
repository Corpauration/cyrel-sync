import org.quartz.Job
import org.quartz.JobExecutionContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Timestamp
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*


class CleanCourseAlertJob : Job {
    private val LOG: Logger = LoggerFactory.getLogger(CleanCourseAlertJob::class.java)
    private lateinit var conn: Connection

    override fun execute(context: JobExecutionContext?) {
        val timer = PrometheusStats.cleanCourseAlertDuration.startTimer()
        try {
            val url = System.getenv("JDBC_URL")
            val props = Properties()
            props.setProperty("user", System.getenv("DB_USERNAME"))
            props.setProperty("password", System.getenv("DB_PASSWORD"))
            conn = DriverManager.getConnection(url, props)
            LOG.info("Cleaning course alerts...")

            val now = LocalDateTime.now().withNano(0)
            val last = now.plusDays((DayOfWeek.SUNDAY.ordinal - now.dayOfWeek.ordinal).toLong() - 7)
            val st = conn.prepareStatement(
                """
            DELETE FROM courses_alerts WHERE time < ?;
        """
            )
            st.setTimestamp(1, Timestamp.from(last.toInstant(ZoneOffset.UTC)))
            st.executeUpdate()

            LOG.info("Done!")
            conn.close()
        } catch (e: Exception) {
            timer.observeDuration()
            PrometheusStats.cleanCourseAlertError.inc()
            throw e
        }
        timer.observeDuration()
    }
}