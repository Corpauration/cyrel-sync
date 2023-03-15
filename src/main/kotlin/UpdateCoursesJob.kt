import fr.corpauration.cycelcat.CyCelcat
import fr.corpauration.cycelcat.resources.Course
import fr.corpauration.cycelcat.resources.SideBarEventElement
import fr.corpauration.cycelcat.resources.Student
import kotlinx.coroutines.runBlocking
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.sql.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*


class UpdateCoursesJob : Job {
    private val LOG: Logger = LoggerFactory.getLogger(UpdateCoursesJob::class.java)
    private lateinit var conn: Connection

    override fun execute(context: JobExecutionContext?) {
        val timer = PrometheusStats.coursesDuration.startTimer()
        try {
            val url = System.getenv("JDBC_URL")
            val props = Properties()
            props.setProperty("user", System.getenv("DB_USERNAME"))
            props.setProperty("password", System.getenv("DB_PASSWORD"))
            props.setProperty("ssl", "false")
            conn = DriverManager.getConnection(url, props)
            LOG.info("Updating courses...")
            val celcat = CyCelcat()
            runBlocking {
                celcat.login(System.getenv("CELCAT_USERNAME"), System.getenv("CELCAT_PASSWORD"))
                getGroupReferents().map {
                    updateCourses(celcat, it.first, it.second)
                }
            }
            LOG.info("Done!")
            conn.close()
        } catch (e: Exception) {
            timer.observeDuration()
            PrometheusStats.coursesError.inc()
            throw e
        }
        timer.observeDuration()
    }

    fun getGroupReferents(): List<Pair<Int, Int>> {
        val list = ArrayList<Pair<Int, Int>>()
        val st: Statement = conn.createStatement()
        val rs = st.executeQuery("SELECT * FROM \"groups\" WHERE private = false AND referent IS NOT NULL ")
        while (rs.next()) {
            val groupId = rs.getInt("id")
            val groupReferent = rs.getString("referent")
            val st2 = conn.prepareStatement("SELECT * FROM students WHERE id = ?")
            st2.setObject(1, UUID.fromString(groupReferent))
            val rs2 = st2.executeQuery()
            rs2.next()
            val studentId = rs2.getInt("student_id")
            list.add(Pair(groupId, studentId))
            rs2.close()
            st2.close()
        }
        rs.close()
        st.close()
        return list
    }

    fun getDifferences(group: Int, courses: List<Course>) {
        val now = LocalDateTime.now().withNano(0)
        val sun = now.plusDays((DayOfWeek.SUNDAY.ordinal - now.dayOfWeek.ordinal).toLong())

        val old = ArrayList<Course>()
        val st = conn.prepareStatement(
            """select c.* from courses as c
        join courses_groups as gc on c.id = gc.id
        where gc.ref = ? and c.start >= ? and c."end" <= ?"""
        )
        st.setInt(1, group)
        st.setTimestamp(2, Timestamp.from(now.toInstant(ZoneOffset.UTC)))
        st.setTimestamp(3, Timestamp.from(sun.toInstant(ZoneOffset.UTC)))
        val rs = st.executeQuery()
        while (rs.next()) {
            old.add(
                Course(
                    rs.getString(1),
                    rs.getTimestamp(2).toString(),
                    rs.getTimestamp(3).toString(),
                    false,
                    "",
                    "",
                    "",
                    null,
                    null,
                    null,
                    null,
                    null,
                    0,
                    0,
                    null,
                    null,
                    null
                )
            )
        }
        rs.close()
        st.close()

        courses.filter {
            LocalDateTime.parse(it.start) in now..sun
        }.forEach { course ->
            val equal = old.firstOrNull { course.id == it.id }
            if (equal != null) {
                if (course.start.replace("T", " ").plus(".0") != equal.start || course.end?.replace("T", " ")
                        .plus(".0") != equal.end
                ) {
                    println("Course ${course.id} has changed: ${equal.start} -> ${course.start} && ${equal.end} -> ${course.end}")
                    addCourseAlert(course.id, group, now, CourseAlertEvent.MODIFIED)
                }
            } else {
                println("New course: ${course.id}")
                addCourseAlert(course.id, group, now, CourseAlertEvent.ADDED)
            }
        }

        var removedCount = 0

        old.filter { !courses.map { it.id }.contains(it.id) }.forEach {
            println("Removed course: ${it.id}")
            removedCount++
            addCourseAlert(it.id, group, now, CourseAlertEvent.DELETED)
        }

        if (removedCount != 0 && courses.filter {
                LocalDateTime.parse(it.start) in now..sun
            }.isEmpty()) {
            println("All courses of the current week ($removedCount) were removed for group $group")
            println("Aborting, this is not normal")
            throw Exception("All courses of the week were deleted for group $group")
        }
    }

    suspend fun updateCourses(celcat: CyCelcat, group: Int, referent: Int) {
        val timer = PrometheusStats.coursesGroupsDuration.startTimer()
        LOG.info("Updating courses for group $group with referent id $referent")
        try {
            val now = LocalDate.now()
            val courses = celcat.getCalendarData(
                if (now.month.value in 9..12) now.withMonth(9).withDayOfMonth(1) else now.withMonth(9).withDayOfMonth(1)
                    .minusYears(1),
                if (now.month.value in 9..12) LocalDate.now().withMonth(9).withDayOfMonth(1)
                    .plusYears(1) else now.withMonth(9).withDayOfMonth(1),
                Student::class,
                "month",
                referent
            )

            if (courses.isEmpty()) {
                LOG.error("Courses is empty, canceling update now for group $group with referent id $referent")
                timer.observeDuration()
                PrometheusStats.coursesError.inc()
                return
            }

            conn.setAutoCommit(false)

            courses.map { updateEvent(celcat, it) }

            getDifferences(group, courses)

            val st1 = conn.prepareStatement("DELETE FROM courses_groups WHERE ref = ?")
            st1.setInt(1, group)
            st1.executeUpdate()

            courses.map {
                val st2 = conn.prepareStatement("INSERT INTO courses_groups (id, ref) VALUES ( ?, ? )")
                st2.setString(1, it.id)
                st2.setInt(2, group)
                st2.executeUpdate()
            }

            conn.commit()
        } catch (e: SQLException) {
            LOG.error(e.toString())
            try {
                conn.rollback()
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
            timer.observeDuration()
            PrometheusStats.coursesError.inc()
        }
        conn.setAutoCommit(true)
        timer.observeDuration()
    }

    suspend fun updateEvent(celcat: CyCelcat, course: Course) {
        val event = celcat.getSideBarEvent(course.id)
        val id: String = course.id
        val start: LocalDateTime = LocalDateTime.parse(course.start)
        val end: LocalDateTime? = if (course.end == null) null else LocalDateTime.parse(course.end)
        var category: CourseCategory = CourseCategory.DEFAULT
        var subject: String? = null
        var teachers = ""
        var rooms = ""
//        var groups: List<GroupEntity>

        var svElem = SideBarEventElement(
            "",
            entityType = 0,
            containsHyperlinks = false,
            isNotes = false,
            isStudentSpecific = false,
            content = null,
            assignmentContext = null,
            federationId = null
        )
        for (element in event.elements) {
            if (element.label != null) svElem = element
            when (element.label) {
                "Catégorie" -> category = when (element.content) {
                    "CM" -> CourseCategory.CM
                    "TD" -> CourseCategory.TD
                    "Accueil" -> CourseCategory.ACCUEIL
                    "Examens" -> CourseCategory.EXAMENS
                    "Indisponibilité" -> CourseCategory.INDISPONIBILITE
                    "Réunions" -> CourseCategory.REUNIONS
                    "Manifestation" -> CourseCategory.MANIFESTATION
                    "Projet encadré/Projet tutoré" -> CourseCategory.PROJET_ENCADRE_TUTORE
                    else -> CourseCategory.DEFAULT
                }

                "Matière" -> subject = element.content
                "Salle" -> rooms = element.content.toString()
                "Salles" -> rooms = element.content.toString()
                "Enseignant" -> teachers = element.content.toString()
                "Enseignants" -> teachers = element.content.toString()
                null -> when (svElem.label) {
                    "Salles" -> rooms += ",${element.content}"
                    "Enseignants" -> teachers += ",${element.content}"
                }
            }
        }

        val st = conn.prepareStatement(
            """
            INSERT INTO courses
                ( id
                , start
                , "end"
                , category
                , subject
                , rooms
                , teachers
                )
            VALUES ( ?, ?, ?, ?, ?, ?, ? )
            ON CONFLICT (id) DO UPDATE
            SET ( start
                , "end"
                , category
                , subject
                , rooms
                , teachers
                ) = ( EXCLUDED.start
                    , EXCLUDED.end
                    , EXCLUDED.category
                    , EXCLUDED.subject
                    , EXCLUDED.rooms
                    , EXCLUDED.teachers
                    )
        """
        )
        st.setString(1, id)
        st.setTimestamp(2, Timestamp.from(start.toInstant(ZoneOffset.UTC)))
        st.setTimestamp(3, if (end != null) Timestamp.from(end.toInstant(ZoneOffset.UTC)) else null)
        st.setInt(4, category.ordinal)
        st.setString(5, subject)
        st.setString(6, rooms)
        st.setString(7, teachers)
        st.executeUpdate()
    }

    fun addCourseAlert(courseId: String, groupId: Int, time: LocalDateTime, event: CourseAlertEvent) {
        val st = conn.prepareStatement(
            """
            INSERT INTO courses_alerts (id, "group", time, event)
            VALUES ( ?, ?, ?, ? );
        """
        )
        st.setString(1, courseId)
        st.setInt(2, groupId)
        st.setTimestamp(3, Timestamp.from(time.toInstant(ZoneOffset.UTC)))
        st.setInt(4, event.ordinal)
        st.executeUpdate()
    }
}