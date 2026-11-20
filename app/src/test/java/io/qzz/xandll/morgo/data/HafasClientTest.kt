package io.qzz.xandll.morgo.data

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import io.qzz.xandll.morgo.data.datasource.HafasClient
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class HafasClientTest {

    @Test
    fun `searchStations returns ÖBB stations with correct parsing`() = runTest {
        val mockEngine = MockEngine { request ->
            respond(
                content = ByteReadChannel("""
                    {
                        "svcResL": [{
                            "res": {
                                "match": {
                                    "locL": [
                                        {
                                            "lid": "A=1@O=Wien Hbf@X=16377094@Y=48184583@U=81@L=1291501@",
                                            "type": "S",
                                            "name": "Wien Hbf",
                                            "extId": "1291501",
                                            "crd": {
                                                "x": 16377094,
                                                "y": 48184583
                                            }
                                        },
                                        {
                                            "lid": "A=1@O=Wien Meidling@X=16333468@Y=48175245@U=81@L=1290401@",
                                            "type": "S",
                                            "name": "Wien Meidling",
                                            "extId": "1290401"
                                        }
                                    ]
                                }
                            },
                            "meth": "LocMatch",
                            "id": "1"
                        }],
                        "ver": "1.48",
                        "lang": "deu",
                        "id": "abc123"
                    }
                """.trimIndent()),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }

        val client = HafasClient(httpClient)
        val result = client.searchStations("Wien")

        assertTrue(result.isSuccess)
        val stations = result.getOrNull()
        assertNotNull(stations)
        assertEquals(2, stations?.size)
        assertEquals("Wien Hbf", stations?.get(0)?.name)
        assertEquals("1291501", stations?.get(0)?.extId)
        assertEquals("Wien Meidling", stations?.get(1)?.name)
    }

    @Test
    fun `searchConnections returns ÖBB connections with full parsing including locL and prodL`() = runTest {
        val mockEngine = MockEngine { request ->
            respond(
                content = ByteReadChannel("""
                    {
                        "svcResL": [{
                            "res": {
                                "common": {
                                    "locL": [
                                        {
                                            "lid": "A=1@O=Wien Hbf@X=16377094@Y=48184583@",
                                            "type": "S",
                                            "name": "Wien Hbf",
                                            "extId": "1291501"
                                        },
                                        {
                                            "lid": "A=1@O=Linz Hbf@X=14286575@Y=48289851@",
                                            "type": "S",
                                            "name": "Linz Hbf",
                                            "extId": "1290401"
                                        }
                                    ],
                                    "prodL": [
                                        {
                                            "name": "REX 5",
                                            "number": "5",
                                            "icoX": 5,
                                            "cls": 32
                                        },
                                        {
                                            "name": "IC 517",
                                            "number": "517",
                                            "icoX": 2,
                                            "cls": 4
                                        }
                                    ]
                                },
                                "outConL": [
                                    {
                                        "cid": "C-0",
                                        "date": "20261121",
                                        "dur": "012500",
                                        "chg": 0,
                                        "dep": {
                                            "locX": 0,
                                            "dPlatfS": "7",
                                            "dTimeS": "070000",
                                            "dTimeR": "070000",
                                            "dProgType": "PROGNOSED"
                                        },
                                        "arr": {
                                            "locX": 1,
                                            "aPlatfS": "3",
                                            "aTimeS": "082500",
                                            "aTimeR": "082500",
                                            "aProgType": "PROGNOSED"
                                        },
                                        "secL": [
                                            {
                                                "type": "JNY",
                                                "dep": {
                                                    "locX": 0,
                                                    "dTimeS": "070000"
                                                },
                                                "arr": {
                                                    "locX": 1,
                                                    "aTimeS": "082500"
                                                },
                                                "jny": {
                                                    "jid": "1|1234|0|81|21112026",
                                                    "prodX": 0,
                                                    "dirTxt": "Linz Hbf",
                                                    "status": "P",
                                                    "isRchbl": true
                                                }
                                            }
                                        ]
                                    }
                                ]
                            },
                            "meth": "TripSearch",
                            "id": "1"
                        }],
                        "ver": "1.48"
                    }
                """.trimIndent()),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }

        val client = HafasClient(httpClient)
        val result = client.searchConnections(
            fromStationId = "A=1@O=Wien Hbf@X=16377094@Y=48184583@",
            toStationId = "A=1@O=Linz Hbf@X=14286575@Y=48289851@",
            date = LocalDate.of(2026, 11, 21),
            time = LocalTime.of(7, 0)
        )

        assertTrue(result.isSuccess)
        val (connections, common) = result.getOrNull()!!
        
        assertEquals(1, connections.size)
        val connection = connections[0]
        
        // Prüfe Connection-Daten
        assertEquals("C-0", connection.cid)
        assertEquals("20261121", connection.date)
        assertEquals("012500", connection.dur)
        assertEquals(0, connection.chg)
        
        // Prüfe Departure
        assertEquals(0, connection.dep?.locX)
        assertEquals("7", connection.dep?.dPlatfS)
        assertEquals("070000", connection.dep?.dTimeS)
        
        // Prüfe Arrival
        assertEquals(1, connection.arr?.locX)
        assertEquals("3", connection.arr?.aPlatfS)
        assertEquals("082500", connection.arr?.aTimeS)
        
        // Prüfe Common-Data
        assertNotNull(common)
        assertEquals(2, common?.locL?.size)
        assertEquals("Wien Hbf", common?.locL?.get(0)?.name)
        assertEquals("Linz Hbf", common?.locL?.get(1)?.name)
        
        assertEquals(2, common?.prodL?.size)
        assertEquals("REX 5", common?.prodL?.get(0)?.name)
        assertEquals("5", common?.prodL?.get(0)?.number)
        
        // Prüfe Journey-Section mit Product
        assertEquals(1, connection.secL?.size)
        val section = connection.secL?.get(0)
        assertEquals("JNY", section?.type)
        assertEquals(0, section?.jny?.prodX)
    }

    @Test
    fun `searchConnections handles delayed trains correctly`() = runTest {
        val mockEngine = MockEngine { request ->
            respond(
                content = ByteReadChannel("""
                    {
                        "svcResL": [{
                            "res": {
                                "common": {
                                    "locL": [
                                        {"lid": "L1", "name": "Wien Hbf", "type": "S"},
                                        {"lid": "L2", "name": "Schulstadt Hbf", "type": "S"}
                                    ],
                                    "prodL": [
                                        {"name": "RJ 63", "number": "63", "cls": 1}
                                    ]
                                },
                                "outConL": [
                                    {
                                        "cid": "C-1",
                                        "date": "20261121",
                                        "dur": "030000",
                                        "chg": 0,
                                        "dep": {
                                            "locX": 0,
                                            "dPlatfS": "1",
                                            "dTimeS": "080000",
                                            "dTimeR": "080800",
                                            "dProgType": "PROGNOSED",
                                            "dCncl": false
                                        },
                                        "arr": {
                                            "locX": 1,
                                            "aPlatfS": "2",
                                            "aTimeS": "110000",
                                            "aTimeR": "111500",
                                            "aProgType": "PROGNOSED",
                                            "aCncl": false
                                        },
                                        "secL": [
                                            {
                                                "type": "JNY",
                                                "jny": {
                                                    "prodX": 0,
                                                    "dirTxt": "Schulstadt",
                                                    "isRchbl": true
                                                }
                                            }
                                        ]
                                    }
                                ]
                            },
                            "meth": "TripSearch",
                            "id": "1"
                        }],
                        "ver": "1.48"
                    }
                """.trimIndent()),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }

        val client = HafasClient(httpClient)
        val result = client.searchConnections(
            fromStationId = "L1",
            toStationId = "L2",
            date = LocalDate.of(2026, 11, 21),
            time = LocalTime.of(8, 0)
        )

        assertTrue(result.isSuccess)
        val (connections, common) = result.getOrNull()!!
        
        assertEquals(1, connections.size)
        val connection = connections[0]
        
        // Verspätung bei Abfahrt: 8 Minuten
        assertEquals("080000", connection.dep?.dTimeS)
        assertEquals("080800", connection.dep?.dTimeR)
        
        // Verspätung bei Ankunft: 15 Minuten
        assertEquals("110000", connection.arr?.aTimeS)
        assertEquals("111500", connection.arr?.aTimeR)
        
        // Nicht ausgefallen
        assertFalse(connection.dep?.dCncl ?: false)
        assertFalse(connection.arr?.aCncl ?: false)
    }

    @Test
    fun `searchConnections handles cancelled trains correctly`() = runTest {
        val mockEngine = MockEngine { request ->
            respond(
                content = ByteReadChannel("""
                    {
                        "svcResL": [{
                            "res": {
                                "common": {
                                    "locL": [
                                        {"lid": "L1", "name": "Graz Hbf", "type": "S"},
                                        {"lid": "L2", "name": "Wien Hbf", "type": "S"}
                                    ],
                                    "prodL": [
                                        {"name": "S3", "number": "3", "cls": 128}
                                    ]
                                },
                                "outConL": [
                                    {
                                        "cid": "C-CANCELLED",
                                        "date": "20261121",
                                        "dur": "024500",
                                        "chg": 0,
                                        "dep": {
                                            "locX": 0,
                                            "dPlatfS": "6",
                                            "dTimeS": "090000",
                                            "dCncl": true
                                        },
                                        "arr": {
                                            "locX": 1,
                                            "aPlatfS": "11",
                                            "aTimeS": "114500",
                                            "aCncl": true
                                        },
                                        "secL": [
                                            {
                                                "type": "JNY",
                                                "jny": {
                                                    "prodX": 0,
                                                    "dirTxt": "Wien",
                                                    "status": "C",
                                                    "isRchbl": false
                                                }
                                            }
                                        ]
                                    }
                                ]
                            },
                            "meth": "TripSearch",
                            "id": "1"
                        }],
                        "ver": "1.48"
                    }
                """.trimIndent()),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }

        val client = HafasClient(httpClient)
        val result = client.searchConnections(
            fromStationId = "L1",
            toStationId = "L2",
            date = LocalDate.of(2026, 11, 21),
            time = LocalTime.of(9, 0)
        )

        assertTrue(result.isSuccess)
        val (connections, common) = result.getOrNull()!!
        
        assertEquals(1, connections.size)
        val connection = connections[0]
        
        // Ausgefallen
        assertTrue(connection.dep?.dCncl ?: false)
        assertTrue(connection.arr?.aCncl ?: false)
        
        // Keine Echtzeit-Daten bei Ausfall
        assertNull(connection.dep?.dTimeR)
        assertNull(connection.arr?.aTimeR)
        
        // Nicht erreichbar
        val journey = connection.secL?.get(0)?.jny
        assertFalse(journey?.isRchbl ?: true)
        assertEquals("C", journey?.status)
    }

    @Test
    fun `searchConnections with connection changes and multiple products`() = runTest {
        val mockEngine = MockEngine { request ->
            respond(
                content = ByteReadChannel("""
                    {
                        "svcResL": [{
                            "res": {
                                "common": {
                                    "locL": [
                                        {"lid": "L1", "name": "Klagenfurt Hbf", "type": "S"},
                                        {"lid": "L2", "name": "Villach Hbf", "type": "S"},
                                        {"lid": "L3", "name": "Schulstadt Hbf", "type": "S"}
                                    ],
                                    "prodL": [
                                        {"name": "REX 1690", "number": "1690", "cls": 32},
                                        {"name": "RJ 165", "number": "165", "cls": 1}
                                    ]
                                },
                                "outConL": [
                                    {
                                        "cid": "C-CHANGE",
                                        "date": "20261121",
                                        "dur": "040000",
                                        "chg": 1,
                                        "dep": {
                                            "locX": 0,
                                            "dPlatfS": "4",
                                            "dTimeS": "100000",
                                            "dTimeR": "100000"
                                        },
                                        "arr": {
                                            "locX": 2,
                                            "aPlatfS": "1",
                                            "aTimeS": "140000",
                                            "aTimeR": "140000"
                                        },
                                        "secL": [
                                            {
                                                "type": "JNY",
                                                "dep": {
                                                    "locX": 0,
                                                    "dTimeS": "100000"
                                                },
                                                "arr": {
                                                    "locX": 1,
                                                    "aTimeS": "104500"
                                                },
                                                "jny": {
                                                    "prodX": 0,
                                                    "dirTxt": "Villach",
                                                    "isRchbl": true
                                                }
                                            },
                                            {
                                                "type": "JNY",
                                                "dep": {
                                                    "locX": 1,
                                                    "dTimeS": "110000"
                                                },
                                                "arr": {
                                                    "locX": 2,
                                                    "aTimeS": "140000"
                                                },
                                                "jny": {
                                                    "prodX": 1,
                                                    "dirTxt": "Schulstadt",
                                                    "isRchbl": true
                                                }
                                            }
                                        ]
                                    }
                                ]
                            },
                            "meth": "TripSearch",
                            "id": "1"
                        }],
                        "ver": "1.48"
                    }
                """.trimIndent()),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }

        val client = HafasClient(httpClient)
        val result = client.searchConnections(
            fromStationId = "L1",
            toStationId = "L3",
            date = LocalDate.of(2026, 11, 21),
            time = LocalTime.of(10, 0)
        )

        assertTrue(result.isSuccess)
        val (connections, common) = result.getOrNull()!!
        
        assertEquals(1, connections.size)
        val connection = connections[0]
        
        // 1 Umstieg
        assertEquals(1, connection.chg)
        
        // 2 Journey-Sections
        assertEquals(2, connection.secL?.size)
        
        // Erste Section: REX 1690
        val firstSection = connection.secL?.get(0)
        assertEquals(0, firstSection?.jny?.prodX)
        assertEquals("Villach", firstSection?.jny?.dirTxt)
        
        // Zweite Section: RJ 165
        val secondSection = connection.secL?.get(1)
        assertEquals(1, secondSection?.jny?.prodX)
        assertEquals("Schulstadt", secondSection?.jny?.dirTxt)
        
        // Common-Daten
        assertEquals(3, common?.locL?.size)
        assertEquals(2, common?.prodL?.size)
        assertEquals("REX 1690", common?.prodL?.get(0)?.name)
        assertEquals("RJ 165", common?.prodL?.get(1)?.name)
    }

    @Test
    fun `locMatch uses oebb iphone client and parses err OK`() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("https://fahrplan.oebb.at/bin/mgate.exe", request.url.toString())
            assertEquals(ContentType.Application.Json, request.body.contentType?.withoutParameters())
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals("1.41", body["ver"]!!.jsonPrimitive.content)
            assertEquals("de", body["lang"]!!.jsonPrimitive.content)
            assertEquals("AID", body["auth"]!!.jsonObject["type"]!!.jsonPrimitive.content)
            assertEquals("OWDL4fE4ixNiPBBm", body["auth"]!!.jsonObject["aid"]!!.jsonPrimitive.content)
            val clientInfo = body["client"]!!.jsonObject
            assertEquals("OEBB", clientInfo["id"]!!.jsonPrimitive.content)
            assertEquals("6030600", clientInfo["v"]!!.jsonPrimitive.content)
            assertEquals("IPH", clientInfo["type"]!!.jsonPrimitive.content)
            assertEquals("oebbIPH", clientInfo["name"]!!.jsonPrimitive.content)
            val svc = body["svcReqL"]!!.jsonArray.first().jsonObject
            assertEquals("LocMatch", svc["meth"]!!.jsonPrimitive.content)
            assertFalse(svc.containsKey("id"))
            val input = svc["req"]!!.jsonObject["input"]!!.jsonObject
            assertEquals("S", input["field"]!!.jsonPrimitive.content)
            assertEquals("5", input["maxLoc"]!!.jsonPrimitive.content)
            assertEquals("Heimstadt?", input["loc"]!!.jsonObject["name"]!!.jsonPrimitive.content)
            assertEquals("S", input["loc"]!!.jsonObject["type"]!!.jsonPrimitive.content)
            respond(
                content = ByteReadChannel(realisticLocMatch()),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val result = HafasClient(plainClient(mockEngine)).searchStations("Heimstadt")
        assertTrue(result.exceptionOrNull()?.message ?: "fehlgeschlagen", result.isSuccess)
        val stations = result.getOrThrow()
        assertEquals(2, stations.size)
        assertEquals("Heimstadt Bahnhof", stations[0].name)
        assertEquals("8100081", stations[0].extId)
        assertEquals("A=1@O=Heimstadt Bahnhof@X=13101333@Y=47683194@U=81@L=8100081@", stations[0].lid)
        assertEquals("Heimstadt Burgfried", stations[1].name)
    }

    @Test
    fun `locMatch keeps an existing question mark and rejects err other than OK`() = runTest {
        val names = mutableListOf<String>()
        val mockEngine = MockEngine { request ->
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            val input = body["svcReqL"]!!.jsonArray.first().jsonObject["req"]!!.jsonObject["input"]!!.jsonObject
            names += input["loc"]!!.jsonObject["name"]!!.jsonPrimitive.content
            respond(
                content = ByteReadChannel("""{"err":"SOT","svcResL":[]}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val result = HafasClient(plainClient(mockEngine)).searchStations("Heimstadt?")
        assertEquals(listOf("Heimstadt?"), names)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("SOT"))
    }

    @Test
    fun `tripSearch uses the same auth client and version`() = runTest {
        val mockEngine = MockEngine { request ->
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals("1.41", body["ver"]!!.jsonPrimitive.content)
            assertEquals("de", body["lang"]!!.jsonPrimitive.content)
            assertEquals("OWDL4fE4ixNiPBBm", body["auth"]!!.jsonObject["aid"]!!.jsonPrimitive.content)
            val clientInfo = body["client"]!!.jsonObject
            assertEquals("6030600", clientInfo["v"]!!.jsonPrimitive.content)
            assertEquals("IPH", clientInfo["type"]!!.jsonPrimitive.content)
            assertEquals("oebbIPH", clientInfo["name"]!!.jsonPrimitive.content)
            val svc = body["svcReqL"]!!.jsonArray.first().jsonObject
            assertEquals("TripSearch", svc["meth"]!!.jsonPrimitive.content)
            val req = svc["req"]!!.jsonObject
            assertEquals("L1", req["depLocL"]!!.jsonArray.first().jsonObject["lid"]!!.jsonPrimitive.content)
            assertEquals("L2", req["arrLocL"]!!.jsonArray.first().jsonObject["lid"]!!.jsonPrimitive.content)
            respond(
                content = ByteReadChannel("""{"err":"OK","svcResL":[{"meth":"TripSearch","err":"OK","res":{"outConL":[],"common":{}}}]}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val result = HafasClient(plainClient(mockEngine)).searchConnections(
            fromStationId = "L1",
            toStationId = "L2",
            date = LocalDate.of(2026, 11, 21),
            time = LocalTime.of(7, 0)
        )
        assertTrue(result.exceptionOrNull()?.message ?: "fehlgeschlagen", result.isSuccess)
        assertTrue(result.getOrThrow().first.isEmpty())
    }

    private fun plainClient(engine: MockEngine) = HttpClient(engine)

    private fun realisticLocMatch(): String = """
        {
          "ver": "1.41",
          "lang": "de",
          "err": "OK",
          "svcResL": [
            {
              "meth": "LocMatch",
              "err": "OK",
              "res": {
                "match": {
                  "locL": [
                    {
                      "lid": "A=1@O=Heimstadt Bahnhof@X=13101333@Y=47683194@U=81@L=8100081@",
                      "type": "S",
                      "name": "Heimstadt Bahnhof",
                      "extId": "8100081",
                      "state": "F",
                      "pCls": 100,
                      "crd": {"x": 13101333, "y": 47683194},
                      "productClasses": [1, 2, 4, 8]
                    },
                    {
                      "lid": "A=1@O=Heimstadt Burgfried@X=13090000@Y=47670000@U=81@L=8100082@",
                      "type": "S",
                      "name": "Heimstadt Burgfried",
                      "extId": "8100082"
                    }
                  ]
                },
                "extraIgnored": true
              }
            }
          ]
        }
    """.trimIndent()
}
