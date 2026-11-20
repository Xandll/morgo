package io.qzz.xandll.morgo.data.repository

import io.qzz.xandll.morgo.data.datasource.HafasClient
import io.qzz.xandll.morgo.data.datasource.HomeAssistantClient
import io.qzz.xandll.morgo.data.datasource.WebUntisClient
import io.qzz.xandll.morgo.data.model.*
import io.qzz.xandll.morgo.domain.model.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException

/**
 * Haupt-Repository für alle Datenquellen
 */
class MorgoRepository(
    private val webUntisClient: WebUntisClient,
    private val hafasClient: HafasClient,
    private val homeAssistantClient: HomeAssistantClient,
    private val lessonCache: LessonCache? = null
) {

    /**
     * WebUntis: Stundenplan abrufen
     */
    suspend fun getTimetable(
        settings: WebUntisSettings,
        startDate: LocalDate,
        endDate: LocalDate
    ): Result<List<Lesson>> {
        return withSession(settings) { auth ->
            webUntisClient.getTimetable(
                server = settings.server,
                schoolName = settings.schoolName,
                personType = auth.personType,
                personId = auth.personId,
                startDate = startDate,
                endDate = endDate
            ).map { periods ->
                periods.mapNotNull { period -> convertPeriodToLesson(period) }
            }
        }
    }

    suspend fun getHolidays(settings: WebUntisSettings): Result<List<SchoolHoliday>> {
        return withSession(settings) { _ ->
            webUntisClient.getHolidays(settings.server, settings.schoolName)
        }
    }

    fun saveLessonCache(date: LocalDate, lessons: List<Lesson>) {
        lessonCache?.save(date, lessons)
    }

    fun readLessonCache(date: LocalDate): List<Lesson>? = lessonCache?.read(date)

    private suspend fun <T> withSession(
        settings: WebUntisSettings,
        block: suspend (WebUntisAuthResult) -> Result<T>
    ): Result<T> {
        return try {
            var auth = authenticate(settings).getOrElse { return Result.failure(it) }
            var result = block(auth)
            if (SessionErrors.isSession(result.exceptionOrNull())) {
                webUntisClient.clearSession()
                auth = authenticate(settings).getOrElse { return Result.failure(it) }
                result = block(auth)
            }
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (SessionErrors.isSession(e)) {
                webUntisClient.clearSession()
                val auth = authenticate(settings).getOrElse { return Result.failure(it) }
                block(auth)
            } else {
                Result.failure(e)
            }
        } finally {
            runCatching { webUntisClient.logout(settings.server, settings.schoolName) }
        }
    }

    /**
     * Anmeldung prüfen, Stundenplan (heute bis nächster Schultag) und Timegrid laden.
     */
    suspend fun testWebUntisConnection(settings: WebUntisSettings): Result<WebUntisProbe> {
        return withSession(settings) { auth ->
            val today = LocalDate.now()
            val periods = webUntisClient.getTimetable(
                server = settings.server,
                schoolName = settings.schoolName,
                personType = auth.personType,
                personId = auth.personId,
                startDate = today,
                endDate = today.plusDays(7)
            ).getOrElse { return@withSession Result.failure(it) }
            val timegrid = webUntisClient.getTimegridUnits(
                server = settings.server,
                schoolName = settings.schoolName
            ).getOrElse { return@withSession Result.failure(it) }
            Result.success(
                WebUntisProbe(
                    displayName = auth.displayName,
                    lessons = periods.mapNotNull { convertPeriodToLesson(it) },
                    timegrid = timegrid
                )
            )
        }
    }

    private suspend fun authenticate(settings: WebUntisSettings): Result<WebUntisAuthResult> {
        return if (settings.useSecret && settings.secret.isNotBlank()) {
            webUntisClient.authenticateWithSecret(
                server = settings.server,
                schoolName = settings.schoolName,
                username = settings.username,
                secret = settings.secret
            )
        } else {
            webUntisClient.authenticate(
                server = settings.server,
                schoolName = settings.schoolName,
                username = settings.username,
                password = settings.password
            )
        }
    }

    /**
     * WebUntis: Zeitraster abrufen (Stundenanfangszeiten)
     */
    suspend fun getTimegridUnits(
        server: String,
        schoolName: String
    ): Result<List<io.qzz.xandll.morgo.data.model.WebUntisTimegrid>> {
        return try {
            webUntisClient.getTimegridUnits(
                server = server,
                schoolName = schoolName
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Timegrid mit Anmeldung laden. Die Session wird danach wieder geschlossen.
     */
    suspend fun loadTimegrid(settings: WebUntisSettings): Result<List<WebUntisTimegrid>> {
        return withSession(settings) {
            webUntisClient.getTimegridUnits(
                server = settings.server,
                schoolName = settings.schoolName
            )
        }
    }
    
    /**
     * WebUntis: Schulen suchen
     */
    suspend fun searchWebUntisSchools(query: String): Result<List<io.qzz.xandll.morgo.data.model.WebUntisSchool>> {
        return try {
            webUntisClient.searchSchools(query)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun convertPeriodToLesson(period: WebUntisPeriod): Lesson? {
        return try {
            val dateValue = period.date ?: return null
            val startValue = period.startTime ?: return null
            val endValue = period.endTime ?: return null
            val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
            val timeFormatter = DateTimeFormatter.ofPattern("HHmm")
            
            val date = LocalDate.parse(dateValue.toString(), dateFormatter)
            val startTime = LocalTime.parse(
                startValue.toString().padStart(4, '0'),
                timeFormatter
            )
            val endTime = LocalTime.parse(
                endValue.toString().padStart(4, '0'),
                timeFormatter
            )

            val subject = period.subjects?.firstOrNull()?.name?.takeIf { it.isNotBlank() } ?: "Unbekannt"
            val teacher = period.teachers?.firstOrNull()?.name.orEmpty()
            val room = period.rooms?.firstOrNull()?.name.orEmpty()

            val status = when (period.code) {
                "cancelled" -> LessonStatus.CANCELLED
                null -> LessonStatus.REGULAR
                else -> LessonStatus.SUBSTITUTED
            }

            Lesson(
                date = date,
                startTime = startTime,
                endTime = endTime,
                subject = subject,
                teacher = teacher,
                room = room,
                status = status
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun searchStations(query: String): Result<List<Station>> {
        return hafasClient.searchStations(query).map { locations ->
            locations.mapNotNull { location ->
                if (location.lid != null && location.name != null) {
                    Station(
                        id = location.lid,
                        name = location.name,
                        extId = location.extId
                    )
                } else {
                    null
                }
            }
        }
    }

    /**
     * HAFAS: Zugverbindungen suchen
     */
    suspend fun searchConnections(
        fromStation: Station,
        toStation: Station,
        date: LocalDate,
        time: LocalTime
    ): Result<ConnectionSearchResult> {
        return hafasClient.searchConnections(
            fromStationId = fromStation.id,
            toStationId = toStation.id,
            date = date,
            time = time
        ).map { response ->
            val connections = response.first
            val common = response.second
            val trainConnections = connections.mapNotNull { connection -> 
                convertHafasConnection(connection, common) 
            }
            ConnectionSearchResult(connections = trainConnections)
        }
    }

    private fun convertHafasConnection(
        connection: HafasConnection,
        common: HafasCommon?
    ): TrainConnection? {
        return try {
            val dep = connection.dep ?: return null
            val arr = connection.arr ?: return null

            val depTime = parseHafasTime(dep.dTimeS) ?: return null
            val depActualTime = parseHafasTime(dep.dTimeR)
            
            val arrTime = parseHafasTime(arr.aTimeS) ?: return null
            val arrActualTime = parseHafasTime(arr.aTimeR)

            // Stationsnamen aus common.locL auflösen
            val depStation = common?.locL?.getOrNull(dep.locX ?: -1)?.name ?: "Abfahrt"
            val arrStation = common?.locL?.getOrNull(arr.locX ?: -1)?.name ?: "Ankunft"

            val duration = connection.dur?.let { parseDuration(it) } ?: 0

            val status = when {
                dep.dCncl == true || arr.aCncl == true -> ConnectionStatus.CANCELLED
                depActualTime != null && depActualTime.isAfter(depTime) -> ConnectionStatus.DELAYED
                arrActualTime != null && arrActualTime.isAfter(arrTime) -> ConnectionStatus.DELAYED
                else -> ConnectionStatus.ON_TIME
            }

            // Produkte (Züge) aus secL extrahieren
            val products = connection.secL?.mapNotNull { section ->
                section.jny?.prodX?.let { prodX ->
                    common?.prodL?.getOrNull(prodX)?.let { product ->
                        TrainProduct(
                            name = product.name ?: "Zug",
                            category = product.name?.split(" ")?.firstOrNull() ?: "",
                            number = product.number
                        )
                    }
                }
            }?.distinctBy { it.name } ?: emptyList()

            TrainConnection(
                departure = ConnectionPoint(
                    station = depStation,
                    plannedTime = depTime,
                    actualTime = depActualTime,
                    platform = dep.dPlatfS
                ),
                arrival = ConnectionPoint(
                    station = arrStation,
                    plannedTime = arrTime,
                    actualTime = arrActualTime,
                    platform = arr.aPlatfS
                ),
                duration = duration,
                changes = connection.chg ?: 0,
                status = status,
                products = products
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseHafasTime(time: String?): LocalTime? {
        return try {
            if (time == null) return null
            val formatter = DateTimeFormatter.ofPattern("HHmmss")
            LocalTime.parse(time, formatter)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseDuration(duration: String): Int {
        return try {
            val formatter = DateTimeFormatter.ofPattern("HHmmss")
            val time = LocalTime.parse(duration, formatter)
            time.hour * 60 + time.minute
        } catch (e: Exception) {
            0
        }
    }

    /**
     * ÖBB: Fahrplan-API mit einer Stationssuche prüfen
     */
    suspend fun testHafasConnection(): Result<Int> {
        return hafasClient.searchStations("Wien")
            .map { stations -> stations.size }
    }

    /**
     * Home Assistant: API und Config prüfen
     */
    suspend fun testHomeAssistantConnection(
        url: String,
        token: String
    ): Result<io.qzz.xandll.morgo.data.model.HomeAssistantInfo> {
        return homeAssistantClient.checkConnection(url, token)
    }

    /**
     * Home Assistant: Entities abrufen
     */
    suspend fun getHomeAssistantEntities(
        url: String,
        token: String
    ): Result<List<HomeAssistantEntity>> {
        return homeAssistantClient.getStates(url, token).map { states ->
            states.map { state ->
                val type = when {
                    state.entityId.startsWith("light.") -> EntityType.LIGHT
                    state.entityId.startsWith("switch.") -> EntityType.SWITCH
                    state.entityId.startsWith("scene.") -> EntityType.SCENE
                    state.entityId.startsWith("script.") -> EntityType.SCRIPT
                    else -> EntityType.UNKNOWN
                }

                HomeAssistantEntity(
                    entityId = state.entityId,
                    name = state.friendlyName.ifBlank {
                        state.entityId.substringAfter(".").replace("_", " ")
                            .replaceFirstChar { it.uppercase() }
                    },
                    state = state.state,
                    type = type
                )
            }
        }
    }

    /**
     * Home Assistant: Licht einschalten
     */
    suspend fun turnOnLight(
        url: String,
        token: String,
        entityId: String,
        brightness: Int? = null
    ): Result<Unit> {
        return homeAssistantClient.turnOnLight(url, token, entityId, brightness)
            .map { Unit }
    }

    suspend fun toggleLight(url: String, token: String, entityId: String): Result<Unit> {
        return homeAssistantClient.toggleLight(url, token, entityId)
            .map { Unit }
    }

    /**
     * Home Assistant: Szene aktivieren
     */
    suspend fun activateScene(url: String, token: String, entityId: String): Result<Unit> {
        return homeAssistantClient.activateScene(url, token, entityId)
            .map { Unit }
    }
}

/**
 * Ergebnis von „Verbindung testen“: Name, Stunden und geladenes Zeitraster.
 */
data class WebUntisProbe(
    val displayName: String?,
    val lessons: List<Lesson>,
    val timegrid: List<io.qzz.xandll.morgo.data.model.WebUntisTimegrid>
)
