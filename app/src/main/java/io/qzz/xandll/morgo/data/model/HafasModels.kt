package io.qzz.xandll.morgo.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * HAFAS mgate.exe Request (ÖBB Scotty)
 */
@Serializable
data class HafasRequest(
    val svcReqL: List<HafasServiceRequest>,
    val client: HafasClient,
    val ext: String? = null,
    val ver: String = "1.41",
    val lang: String = "de",
    val auth: HafasAuth
)

@Serializable
data class HafasServiceRequest(
    val req: kotlinx.serialization.json.JsonElement,
    val meth: String,
    val id: String
)

@Serializable
data class HafasClient(
    val id: String = "OEBB",
    val v: String = "6030600",
    val type: String = "IPH",
    val name: String = "oebbIPH"
)

@Serializable
data class HafasAuth(
    val type: String = "AID",
    val aid: String = "OWDL4fE4ixNiPBBm"
)

/**
 * Location Match Request (Stationssuche)
 */
@Serializable
data class HafasLocMatchRequest(
    val input: HafasLocInput
)

@Serializable
data class HafasLocInput(
    val field: String = "S",
    val loc: HafasLocInputDetail,
    val maxLoc: Int = 5
)

@Serializable
data class HafasLocInputDetail(
    val name: String,
    val type: String = "S" // S = Station
)

/**
 * Trip Search Request (Verbindungssuche)
 */
@Serializable
data class HafasTripSearchRequest(
    val depLocL: List<HafasLocation>,
    val arrLocL: List<HafasLocation>,
    val outDate: String, // Format: YYYYMMDD
    val outTime: String, // Format: HHmmss
    val getPolyline: Boolean = false,
    val maxChg: Int? = null,
    val minChgTime: Int? = null,
    val numF: Int = 1 // Anzahl der Verbindungen
)

/**
 * HAFAS Response
 */
@Serializable
data class HafasResponse(
    val svcResL: List<HafasServiceResponse>? = null,
    val err: String? = null
)

@Serializable
data class HafasServiceResponse(
    val res: HafasResponseData? = null,
    val err: String? = null,
    val id: String? = null,
    val meth: String? = null
)

@Serializable
data class HafasResponseData(
    val match: HafasLocMatchResult? = null,
    val outConL: List<HafasConnection>? = null,
    val common: HafasCommon? = null
)

/**
 * Location Match Result
 */
@Serializable
data class HafasLocMatchResult(
    val locL: List<HafasLocation>? = null
)

@Serializable
data class HafasLocation(
    val lid: String? = null, // Location ID
    val type: String? = null, // S = Station
    val name: String? = null,
    val extId: String? = null, // Externe ID (z.B. EVA-Nummer)
    val crd: HafasCoordinates? = null
)

@Serializable
data class HafasCoordinates(
    val x: Long? = null, // Longitude * 1000000
    val y: Long? = null  // Latitude * 1000000
)

/**
 * Connection (Verbindung)
 */
@Serializable
data class HafasConnection(
    val cid: String? = null,
    val date: String? = null,
    val dur: String? = null, // Dauer in HHmmss
    val chg: Int? = null, // Anzahl Umstiege
    val dep: HafasArrDep? = null,
    val arr: HafasArrDep? = null,
    val secL: List<HafasJourneySection>? = null
)

@Serializable
data class HafasArrDep(
    val locX: Int? = null, // Index in common.locL
    val dPlatfS: String? = null,
    val dTimeS: String? = null, // Geplante Zeit HHmmss
    val dTimeR: String? = null, // Echtzeit HHmmss
    val dProgType: String? = null, // PROGNOSED, CALCULATED, etc.
    val dCncl: Boolean? = null, // Cancelled
    val aPlatfS: String? = null,
    val aTimeS: String? = null,
    val aTimeR: String? = null,
    val aProgType: String? = null,
    val aCncl: Boolean? = null
)

@Serializable
data class HafasJourneySection(
    val type: String? = null, // JNY = Journey, WALK, etc.
    val dep: HafasArrDep? = null,
    val arr: HafasArrDep? = null,
    val jny: HafasJourney? = null
)

@Serializable
data class HafasJourney(
    val jid: String? = null,
    val prodX: Int? = null, // Index in common.prodL
    val dirTxt: String? = null, // Richtung
    val status: String? = null,
    val isRchbl: Boolean? = null // Erreichbar
)

@Serializable
data class HafasCommon(
    val locL: List<HafasLocation>? = null,
    val prodL: List<HafasProduct>? = null
)

@Serializable
data class HafasProduct(
    val name: String? = null,
    val number: String? = null,
    val icoX: Int? = null,
    val cls: Int? = null
)
