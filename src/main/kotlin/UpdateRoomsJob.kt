import org.quartz.Job
import org.quartz.JobExecutionContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.*


class UpdateRoomsJob : Job {
    private val LOG: Logger = LoggerFactory.getLogger(UpdateRoomsJob::class.java)
    private lateinit var conn: Connection

    override fun execute(context: JobExecutionContext?) {
        val timer = PrometheusStats.roomsDuration.startTimer()
        try {
            val url = System.getenv("JDBC_URL")
            val props = Properties()
            props.setProperty("user", System.getenv("DB_USERNAME"))
            props.setProperty("password", System.getenv("DB_PASSWORD"))
            props.setProperty("ssl", "false")
            conn = DriverManager.getConnection(url, props)
            LOG.info("Updating rooms availabilities...")
            try {
                conn.autoCommit = false
                conn.prepareStatement("DELETE FROM rooms_availabilities WHERE TRUE").executeUpdate()
                conn.prepareStatement(
                    """
                    INSERT INTO rooms_availabilities (id, ref)
                    SELECT r.id, c.id FROM rooms AS r JOIN courses AS c ON c.rooms LIKE '%' || r.name || '%';
                """
                ).executeUpdate()
                conn.commit()
            } catch (e: SQLException) {
                LOG.error(e.toString())
                try {
                    conn.rollback()
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
                throw e
            }
            conn.autoCommit = true
            LOG.info("Done!")
        } catch (e: Exception) {
            timer.observeDuration()
            PrometheusStats.roomsError.inc()
            throw e
        }
        timer.observeDuration()
    }
}