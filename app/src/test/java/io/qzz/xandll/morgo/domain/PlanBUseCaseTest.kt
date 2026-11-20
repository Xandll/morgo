package io.qzz.xandll.morgo.domain

import io.mockk.*
import io.qzz.xandll.morgo.data.model.WebUntisTimeUnit
import io.qzz.xandll.morgo.data.model.WebUntisTimegrid
import io.qzz.xandll.morgo.data.repository.MorgoRepository
import io.qzz.xandll.morgo.domain.model.*
import io.qzz.xandll.morgo.domain.usecase.PlanBReason
import io.qzz.xandll.morgo.domain.usecase.PlanBUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class PlanBUseCaseTest {

    private lateinit var repository: MorgoRepository
    private lateinit var useCase: PlanBUseCase
    private lateinit var settings: AppSettings

    @Before
    fun setup() {
        repository = mockk()
        useCase = PlanBUseCase(repository)
        
        settings = AppSettings(
            train = TrainSettings(
                startStation = Station("1", "Wien Hbf"),
                destinationStation = Station("2", "Baden"),
                walkingTimeMinutes = 10,
                preparationTimeMinutes = 30
            ),
            alarm = AlarmSettings(
                earliestWakeTime = LocalTime.of(5, 0),
                maxEarlierMinutes = 30
            )
        )
    }

    @Test
    fun `preferred train runs on time`() = runTest {
        val preferredDeparture = LocalTime.of(7, 30)
        val lessonStart = LocalTime.of(8, 30)
        val date = LocalDate.of(2024, 11, 20)

        val connections = listOf(
            createConnection(
                depTime = LocalTime.of(7, 30),
                arrTime = LocalTime.of(8, 0),
                status = ConnectionStatus.ON_TIME
            )
        )

        coEvery { 
            repository.searchConnections(any(), any(), any(), any()) 
        } returns Result.success(ConnectionSearchResult(connections))

        val result = useCase.evaluatePlanB(preferredDeparture, lessonStart, date, settings).getOrThrow()

        assertFalse(result.needsPlanB)
        assertEquals(PlanBReason.ON_TIME, result.reason)
        assertNull(result.alternativeConnection)
        assertNull(result.newWakeTime)
    }

    @Test
    fun `preferred train cancelled - finds alternative`() = runTest {
        val preferredDeparture = LocalTime.of(7, 30)
        val lessonStart = LocalTime.of(8, 30)
        val date = LocalDate.of(2024, 11, 20)

        val connections = listOf(
            createConnection(
                depTime = LocalTime.of(7, 30),
                arrTime = LocalTime.of(8, 0),
                status = ConnectionStatus.CANCELLED
            ),
            createConnection(
                depTime = LocalTime.of(7, 10),
                arrTime = LocalTime.of(7, 40),
                status = ConnectionStatus.ON_TIME
            )
        )

        coEvery { 
            repository.searchConnections(any(), any(), any(), any()) 
        } returns Result.success(ConnectionSearchResult(connections))

        val result = useCase.evaluatePlanB(preferredDeparture, lessonStart, date, settings).getOrThrow()

        assertTrue(result.needsPlanB)
        assertEquals(PlanBReason.CANCELLED, result.reason)
        assertNotNull(result.alternativeConnection)
        assertNotNull(result.newWakeTime)
        assertEquals(LocalTime.of(7, 10), result.alternativeConnection?.departure?.plannedTime)
        assertEquals(LocalTime.of(6, 40), result.newWakeTime)
    }

    @Test
    fun `delayed train arrives too late - finds alternative`() = runTest {
        val preferredDeparture = LocalTime.of(7, 30)
        val lessonStart = LocalTime.of(8, 30)
        val date = LocalDate.of(2024, 11, 20)

        val connections = listOf(
            createConnection(
                depTime = LocalTime.of(7, 30),
                arrTime = LocalTime.of(8, 0),
                arrActualTime = LocalTime.of(8, 25),
                status = ConnectionStatus.DELAYED
            ),
            createConnection(
                depTime = LocalTime.of(7, 10),
                arrTime = LocalTime.of(7, 40),
                status = ConnectionStatus.ON_TIME
            )
        )

        coEvery { 
            repository.searchConnections(any(), any(), any(), any()) 
        } returns Result.success(ConnectionSearchResult(connections))

        val result = useCase.evaluatePlanB(preferredDeparture, lessonStart, date, settings).getOrThrow()

        assertTrue(result.needsPlanB)
        assertEquals(PlanBReason.DELAYED_TOO_LATE, result.reason)
        assertNotNull(result.alternativeConnection)
    }

    @Test
    fun `no alternative available - returns late connection with message`() = runTest {
        val preferredDeparture = LocalTime.of(7, 30)
        val lessonStart = LocalTime.of(8, 30)
        val date = LocalDate.of(2024, 11, 20)

        val connections = listOf(
            createConnection(
                depTime = LocalTime.of(7, 30),
                arrTime = LocalTime.of(8, 0),
                status = ConnectionStatus.CANCELLED
            ),
            createConnection(
                depTime = LocalTime.of(7, 50),
                arrTime = LocalTime.of(8, 30),
                status = ConnectionStatus.ON_TIME
            )
        )

        coEvery { 
            repository.searchConnections(any(), any(), any(), any()) 
        } returns Result.success(ConnectionSearchResult(connections))

        val result = useCase.evaluatePlanB(preferredDeparture, lessonStart, date, settings).getOrThrow()

        assertTrue(result.needsPlanB)
        assertEquals(PlanBReason.NO_ALTERNATIVE, result.reason)
        assertNotNull(result.alternativeConnection)
        assertNull(result.newWakeTime)
        assertTrue(result.message.contains("zu spät"))
    }

    @Test
    fun `respects earliestWakeTime boundary`() = runTest {
        val preferredDeparture = LocalTime.of(7, 30)
        val lessonStart = LocalTime.of(8, 30)
        val date = LocalDate.of(2024, 11, 20)

        val connections = listOf(
            createConnection(
                depTime = LocalTime.of(7, 30),
                arrTime = LocalTime.of(8, 0),
                status = ConnectionStatus.CANCELLED
            ),
            createConnection(
                depTime = LocalTime.of(5, 10),
                arrTime = LocalTime.of(5, 40),
                status = ConnectionStatus.ON_TIME
            )
        )

        coEvery { 
            repository.searchConnections(any(), any(), any(), any()) 
        } returns Result.success(ConnectionSearchResult(connections))

        val result = useCase.evaluatePlanB(preferredDeparture, lessonStart, date, settings).getOrThrow()

        assertTrue(result.needsPlanB)
        assertNotNull(result.newWakeTime)
        assertTrue(result.newWakeTime!! >= settings.alarm.earliestWakeTime)
    }

    @Test
    fun `first lesson number selects the preferred train`() = runTest {
        val date = LocalDate.of(2024, 11, 20)
        val lessons = listOf(
            Lesson(
                date = date,
                startTime = LocalTime.of(8, 0),
                endTime = LocalTime.of(8, 45),
                subject = "Mathematik",
                teacher = "Müller",
                room = "1",
                status = LessonStatus.CANCELLED
            ),
            Lesson(
                date = date,
                startTime = LocalTime.of(8, 30),
                endTime = LocalTime.of(9, 15),
                subject = "Deutsch",
                teacher = "Schmidt",
                room = "2",
                status = LessonStatus.REGULAR
            )
        )
        val slots = LessonGrid.fromTimegrids(
            listOf(
                WebUntisTimegrid(
                    day = 4,
                    timeUnits = listOf(
                        WebUntisTimeUnit("1", 800, 845),
                        WebUntisTimeUnit("2", 830, 915)
                    )
                )
            )
        )
        val withRule = settings.copy(
            alarm = settings.alarm.copy(
                wakeTimeRules = listOf(
                    WakeTimeRule(
                        lessonNumber = 2,
                        wakeTime = LocalTime.of(6, 30),
                        preferredTrainDeparture = LocalTime.of(7, 30)
                    )
                )
            )
        )
        coEvery { repository.searchConnections(any(), any(), any(), any()) } returns Result.success(
            ConnectionSearchResult(
                listOf(
                    createConnection(LocalTime.of(7, 30), LocalTime.of(8, 0), status = ConnectionStatus.CANCELLED),
                    createConnection(LocalTime.of(7, 10), LocalTime.of(7, 40), status = ConnectionStatus.ON_TIME)
                )
            )
        )

        val result = useCase.evaluateForFirstLesson(date, lessons, slots, withRule).getOrThrow()

        assertTrue(result.needsPlanB)
        assertEquals(PlanBReason.CANCELLED, result.reason)
        assertEquals(LocalTime.of(7, 10), result.alternativeConnection?.departure?.plannedTime)
    }

    @Test
    fun `missing preferred train does not trigger plan b`() = runTest {
        val date = LocalDate.of(2024, 11, 20)
        val lessons = listOf(
            Lesson(
                date = date,
                startTime = LocalTime.of(8, 30),
                endTime = LocalTime.of(9, 15),
                subject = "Deutsch",
                teacher = "Schmidt",
                room = "2",
                status = LessonStatus.REGULAR
            )
        )
        val slots = LessonGrid.fromTimegrids(
            listOf(
                WebUntisTimegrid(
                    day = 4,
                    timeUnits = listOf(WebUntisTimeUnit("1", 830, 915))
                )
            )
        )
        val withWakeOnly = settings.copy(
            alarm = settings.alarm.copy(
                wakeTimeRules = listOf(WakeTimeRule(lessonNumber = 1, wakeTime = LocalTime.of(6, 30)))
            )
        )

        val result = useCase.evaluateForFirstLesson(date, lessons, slots, withWakeOnly).getOrThrow()

        assertFalse(result.needsPlanB)
        assertEquals("Kein bevorzugter Zug", result.message)
    }

    private fun createConnection(
        depTime: LocalTime,
        arrTime: LocalTime,
        arrActualTime: LocalTime? = null,
        status: ConnectionStatus
    ): TrainConnection {
        return TrainConnection(
            departure = ConnectionPoint(
                station = "Wien Hbf",
                plannedTime = depTime,
                actualTime = null,
                platform = "1"
            ),
            arrival = ConnectionPoint(
                station = "Baden",
                plannedTime = arrTime,
                actualTime = arrActualTime,
                platform = "2"
            ),
            duration = 30,
            changes = 0,
            status = status
        )
    }
}
