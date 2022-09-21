import fr.corpauration.cycelcat.CyCelcat
import fr.corpauration.cycelcat.resources.Student
import kotlinx.coroutines.runBlocking
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.*


class UpdateCytechStudentsJob : Job {
    private val LOG: Logger = LoggerFactory.getLogger(UpdateCytechStudentsJob::class.java)
    private lateinit var conn: Connection

    override fun execute(context: JobExecutionContext?) {
        val timer = PrometheusStats.studentsDuration.startTimer()
        try {
            val url = System.getenv("JDBC_URL")
            val props = Properties()
            props.setProperty("user", System.getenv("DB_USERNAME"))
            props.setProperty("password", System.getenv("DB_PASSWORD"))
            props.setProperty("ssl", "false")
            conn = DriverManager.getConnection(url, props)
            LOG.info("Updating students...")
            val celcat = CyCelcat()
            runBlocking {
                celcat.login(System.getenv("CELCAT_USERNAME"), System.getenv("CELCAT_PASSWORD"))
                val students = celcat.readAllResourceListItems(Student::class, false, "__").results
                try {
                    conn.setAutoCommit(false)
                    deleteAll()
                    students.filter { it.dept == "D : CY TECH" }.forEach {
                        insertStudent(it)
                    }
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
                conn.setAutoCommit(true)
            }
            LOG.info("Done!")
        } catch (e: Exception) {
            timer.observeDuration()
            PrometheusStats.studentsError.inc()
            throw e
        }
        timer.observeDuration()
    }

    fun deleteAll() {
        val st = conn.prepareStatement("DELETE FROM cytech_students")
        st.executeUpdate()
    }

    fun insertStudent(student: Student) {
        val st = conn.prepareStatement(
            """
            INSERT INTO cytech_students
                ( id )
            VALUES ( ? )
        """
        )
        st.setInt(1, student.id)
        st.executeUpdate()
    }
}