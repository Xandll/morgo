package io.qzz.xandll.morgo.domain

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.qzz.xandll.morgo.data.model.WebUntisTimeUnit
import io.qzz.xandll.morgo.data.model.WebUntisTimegrid
import io.qzz.xandll.morgo.data.repository.MorgoRepository
import io.qzz.xandll.morgo.domain.model.*
import io.qzz.xandll.morgo.domain.usecase.CalculateAlarmTimeUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class CalculateAlarmTimeUseCaseTest {

    private lateinit var repository: MorgoRepository
    private lateinit var useCase: CalculateAlarmTimeUseCase

    @Before
    fun setup() {
        repository = mockk()
        useCase = CalculateAlarmTimeUseCase(repository)
        coEvery { repository.loadTimegrid(any()) } returns Result.success(emptyList())
        coEvery { repository.getHolidays(any()) } returns Result.success(emptyList())
        every { repository.saveLessonCache(any(), any()) } returns Unit
        every { repository.readLessonCache(any()) } returns null
    }

    @Test
    fun `calculates alarm time based on first lesson and train connection`() = runTest {
        val date = LocalDate.of(2026, 11, 21)
        val firstLesson = Lesson(
            date = date,
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(8, 50),
            subject = "Mathematik",
            teacher = "Müller",
            room = "101",
            status = LessonStatus.REGULAR
        )

        val trainConnection = TrainConnection(
            departure = ConnectionPoint(
                station = "Start",
                plannedTime = LocalTime.of(7, 15),
                actualTime = null,
                platform = "1"
            ),
            arrival = ConnectionPoint(
                station = "Ziel",
                plannedTime = LocalTime.of(7, 45),
                actualTime = null,
                platform = "2"
            ),
            duration = 30,
            changes = 0,
            status = ConnectionStatus.ON_TIME
        )

        val settings = AppSettings(
            webUntis = WebUntisSettings(
                server = "test.webuntis.com",
                schoolName = "school",
                username = "user",
                password = "pass"
            ),
            train = TrainSettings(
                startStation = Station("1", "Start"),
                destinationStation = Station("2", "Ziel"),
                walkingTimeMinutes = 10,
                preparationTimeMinutes = 30
            ),
            alarm = AlarmSettings(enabled = true)
        )

        coEvery {
            repository.getTimetable(any(), any(), any())
        } returns Result.success(listOf(firstLesson))

        coEvery {
            repository.searchConnections(any(), any(), any(), any())
        } returns Result.success(ConnectionSearchResult(listOf(trainConnection)))

        val result = useCase.execute(date, settings)

        assertTrue(result.isSuccess)
        val alarmPlan = result.getOrNull()
        assertNotNull(alarmPlan)
        assertEquals(LocalTime.of(6, 45), alarmPlan?.alarmTime)
        assertEquals(firstLesson, alarmPlan?.firstLesson)
        assertEquals(trainConnection, alarmPlan?.trainConnection)
    }

    @Test
    fun `does not ring when the weekday has no lessons`() = runTest {
        val date = LocalDate.of(2026, 11, 23)
        val settings = AppSettings(
            alarm = AlarmSettings(
                enabled = true,
                defaultWakeTime = LocalTime.of(7, 0)
            )
        )

        coEvery {
            repository.getTimetable(any(), any(), any())
        } returns Result.success(emptyList())

        val result = useCase.execute(date, settings)

        assertTrue(result.isSuccess)
        val alarmPlan = result.getOrNull()
        assertNotNull(alarmPlan)
        assertEquals(false, alarmPlan?.rings)
        assertEquals("Kein Unterricht", alarmPlan?.reason)
        assertNull(alarmPlan?.firstLesson)
    }

    @Test
    fun `weekend without lessons does not ring`() = runTest {
        val date = LocalDate.of(2026, 11, 21)
        val settings = AppSettings(alarm = AlarmSettings(enabled = true))
        coEvery { repository.getTimetable(any(), any(), any()) } returns Result.success(emptyList())
        val plan = useCase.execute(date, settings).getOrNull()
        assertEquals(false, plan?.rings)
        assertEquals("Wochenende", plan?.reason)
    }

    @Test
    fun `holiday suppresses the alarm`() = runTest {
        val date = LocalDate.of(2026, 11, 23)
        val lesson = Lesson(
            date = date,
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(8, 45),
            subject = "Mathe",
            teacher = "A",
            room = "1",
            status = LessonStatus.REGULAR
        )
        coEvery { repository.getHolidays(any()) } returns Result.success(
            listOf(SchoolHoliday("Weihnachtsferien", date, date))
        )
        coEvery { repository.getTimetable(any(), any(), any()) } returns Result.success(listOf(lesson))
        val plan = useCase.execute(date, AppSettings(alarm = AlarmSettings(enabled = true))).getOrNull()
        assertEquals(false, plan?.rings)
        assertEquals("Ferien: Weihnachtsferien", plan?.reason)
    }

    @Test
    fun `all cancelled lessons do not ring`() = runTest {
        val date = LocalDate.of(2026, 11, 23)
        val lesson = Lesson(
            date = date,
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(8, 45),
            subject = "Mathe",
            teacher = "A",
            room = "1",
            status = LessonStatus.CANCELLED
        )
        coEvery { repository.getTimetable(any(), any(), any()) } returns Result.success(listOf(lesson))
        val plan = useCase.execute(date, AppSettings(alarm = AlarmSettings(enabled = true))).getOrNull()
        assertEquals(false, plan?.rings)
        assertEquals("Alle Stunden entfallen", plan?.reason)
    }

    @Test
    fun `unreachable timetable without cache still rings`() = runTest {
        val date = LocalDate.of(2026, 11, 23)
        coEvery { repository.getTimetable(any(), any(), any()) } returns Result.failure(Exception("timeout"))
        val plan = useCase.execute(date, AppSettings(alarm = AlarmSettings(enabled = true, defaultWakeTime = LocalTime.of(7, 0)))).getOrNull()
        assertEquals(true, plan?.rings)
        assertEquals(true, plan?.uncertain)
        assertEquals(LocalTime.of(7, 0), plan?.alarmTime)
    }

    @Test
    fun `unreachable timetable uses cached lessons`() = runTest {
        val date = LocalDate.of(2026, 11, 23)
        val lesson = Lesson(
            date = date,
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(8, 45),
            subject = "Mathe",
            teacher = "A",
            room = "1",
            status = LessonStatus.REGULAR
        )
        coEvery { repository.getTimetable(any(), any(), any()) } returns Result.failure(Exception("offline"))
        every { repository.readLessonCache(date) } returns listOf(lesson)
        coEvery { repository.searchConnections(any(), any(), any(), any()) } returns Result.success(ConnectionSearchResult(emptyList()))
        val plan = useCase.execute(
            date,
            AppSettings(
                train = TrainSettings(walkingTimeMinutes = 10, preparationTimeMinutes = 30),
                alarm = AlarmSettings(enabled = true)
            )
        ).getOrNull()
        assertEquals(true, plan?.rings)
        assertEquals(true, plan?.usedCache)
        assertEquals(LocalTime.of(7, 20), plan?.alarmTime)
    }

    @Test
    fun `ignores cancelled lessons`() = runTest {
        val date = LocalDate.of(2026, 11, 21)
        val cancelledLesson = Lesson(
            date = date,
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(8, 50),
            subject = "Mathematik",
            teacher = "Müller",
            room = "101",
            status = LessonStatus.CANCELLED
        )

        val regularLesson = Lesson(
            date = date,
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(9, 50),
            subject = "Deutsch",
            teacher = "Schmidt",
            room = "102",
            status = LessonStatus.REGULAR
        )

        val settings = AppSettings(
            alarm = AlarmSettings(enabled = true)
        )

        coEvery {
            repository.getTimetable(any(), any(), any())
        } returns Result.success(listOf(cancelledLesson, regularLesson))

        coEvery {
            repository.searchConnections(any(), any(), any(), any())
        } returns Result.success(ConnectionSearchResult(emptyList()))

        val result = useCase.execute(date, settings)

        assertTrue(result.isSuccess)
        val alarmPlan = result.getOrNull()
        assertEquals(regularLesson, alarmPlan?.firstLesson)
    }

    @Test
    fun `uses the wake time stored for the lesson number`() = runTest {
        val date = LocalDate.of(2026, 11, 23)
        val lesson = lessonAt(date, 8, 0)
        val train = trainAt(7, 15, 7, 45)
        val settings = schoolSettings(
            wakeRules = listOf(
                WakeTimeRule(lessonNumber = 1, wakeTime = LocalTime.of(6, 20))
            )
        )
        stubDay(date, lesson, train, mondayAtEight())

        val plan = useCase.execute(date, settings).getOrNull()

        assertEquals(LocalTime.of(6, 20), plan?.alarmTime)
        assertEquals(train, plan?.trainConnection)
        assertEquals("Weckzeit für die 1. Stunde", plan?.reason)
    }

    @Test
    fun `empty wake time falls back to the train lead time`() = runTest {
        val date = LocalDate.of(2026, 11, 23)
        val lesson = lessonAt(date, 8, 0)
        val train = trainAt(7, 15, 7, 45)
        val settings = schoolSettings(
            wakeRules = listOf(
                WakeTimeRule(
                    lessonNumber = 1,
                    wakeTime = null,
                    preferredTrainDeparture = LocalTime.of(7, 15)
                )
            )
        )
        stubDay(date, lesson, train, mondayAtEight())

        val plan = useCase.execute(date, settings).getOrNull()

        assertEquals(LocalTime.of(6, 45), plan?.alarmTime)
    }

    @Test
    fun `legacy clock rule is migrated onto the weekday lesson number`() = runTest {
        val date = LocalDate.of(2026, 11, 25)
        val lesson = lessonAt(date, 8, 0)
        val train = trainAt(7, 15, 7, 45)
        val settings = schoolSettings(
            wakeRules = listOf(
                WakeTimeRule(
                    lessonNumber = 0,
                    wakeTime = LocalTime.of(6, 5),
                    legacyStartTime = LocalTime.of(8, 0)
                )
            )
        )
        stubDay(date, lesson, train, mondayAndWednesdayGrid())

        val plan = useCase.execute(date, settings).getOrNull()

        assertEquals(LocalTime.of(6, 5), plan?.alarmTime)
        assertEquals("Weckzeit für die 1. Stunde", plan?.reason)
    }

    @Test
    fun `returns null when alarm is disabled`() = runTest {
        val date = LocalDate.of(2026, 11, 21)
        val settings = AppSettings(
            alarm = AlarmSettings(enabled = false)
        )

        val result = useCase.execute(date, settings)

        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
    }

    private fun schoolSettings(wakeRules: List<WakeTimeRule>): AppSettings {
        return AppSettings(
            webUntis = WebUntisSettings(
                server = "test.webuntis.com",
                schoolName = "school",
                username = "user",
                password = "pass"
            ),
            train = TrainSettings(
                startStation = Station("1", "Start"),
                destinationStation = Station("2", "Ziel"),
                walkingTimeMinutes = 10,
                preparationTimeMinutes = 30
            ),
            alarm = AlarmSettings(enabled = true, wakeTimeRules = wakeRules)
        )
    }

    private fun stubDay(
        date: LocalDate,
        lesson: Lesson,
        train: TrainConnection,
        grid: List<WebUntisTimegrid>
    ) {
        coEvery { repository.getTimetable(any(), any(), any()) } returns Result.success(listOf(lesson))
        coEvery { repository.searchConnections(any(), any(), any(), any()) } returns Result.success(
            ConnectionSearchResult(listOf(train))
        )
        coEvery { repository.loadTimegrid(any()) } returns Result.success(grid)
    }

    private fun lessonAt(date: LocalDate, hour: Int, minute: Int): Lesson {
        return Lesson(
            date = date,
            startTime = LocalTime.of(hour, minute),
            endTime = LocalTime.of(hour, minute).plusMinutes(50),
            subject = "Mathematik",
            teacher = "Müller",
            room = "101",
            status = LessonStatus.REGULAR
        )
    }

    private fun trainAt(depHour: Int, depMinute: Int, arrHour: Int, arrMinute: Int): TrainConnection {
        return TrainConnection(
            departure = ConnectionPoint("Start", LocalTime.of(depHour, depMinute), null, "1"),
            arrival = ConnectionPoint("Ziel", LocalTime.of(arrHour, arrMinute), null, "2"),
            duration = 30,
            changes = 0,
            status = ConnectionStatus.ON_TIME
        )
    }

    private fun mondayAtEight(): List<WebUntisTimegrid> {
        return listOf(
            WebUntisTimegrid(
                day = 2,
                timeUnits = listOf(WebUntisTimeUnit("1", 800, 850))
            )
        )
    }

    private fun mondayAndWednesdayGrid(): List<WebUntisTimegrid> {
        return listOf(
            WebUntisTimegrid(
                day = 2,
                timeUnits = listOf(WebUntisTimeUnit("1", 745, 830))
            ),
            WebUntisTimegrid(
                day = 4,
                timeUnits = listOf(WebUntisTimeUnit("1", 800, 845))
            )
        )
    }
}
