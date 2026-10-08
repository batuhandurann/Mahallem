package com.batuhanduran.burada.data.model

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** App-owned identifiers; labels may change without changing identity. */
data class NeighborhoodRef(
    val provinceId: String,
    val districtId: String,
    val neighborhoodId: String,
    val provinceName: String,
    val districtName: String,
    val neighborhoodName: String
) {
    init {
        require(listOf(provinceId, districtId, neighborhoodId).all { it.matches(ID_PATTERN) })
        require(listOf(provinceName, districtName, neighborhoodName).all { it.isNotBlank() && it.length <= 100 })
    }

    val districtLabel: String get() = "$districtName, $provinceName"
    val displayLabel: String get() = "$neighborhoodName, $districtName / $provinceName"

    fun hasSameIdentity(other: NeighborhoodRef): Boolean =
        provinceId == other.provinceId && districtId == other.districtId && neighborhoodId == other.neighborhoodId

    companion object { private val ID_PATTERN = Regex("[a-z0-9][a-z0-9_-]{1,79}") }
}

data class GeoCoordinate(val latitude: Double, val longitude: Double) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0) { "Latitude must be finite and within -90..90." }
        require(longitude.isFinite() && longitude in -180.0..180.0) { "Longitude must be finite and within -180..180." }
    }

    fun distanceMetersTo(other: GeoCoordinate): Double {
        val lat1 = Math.toRadians(latitude)
        val lat2 = Math.toRadians(other.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(other.longitude - longitude)
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 6_371_008.8 * 2 * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }
}

/** Public data deliberately contains neither address nor exact coordinates. */
data class PublicLocation(val neighborhood: NeighborhoodRef, val coarseGeohash: String? = null) {
    init {
        require(coarseGeohash == null || (coarseGeohash.length == 5 && coarseGeohash.all { it in Geohash.ALPHABET }))
    }
}

/** Store only in owner/authorized-participant protected documents. */
data class PrivateLocation(val coordinate: GeoCoordinate?, val address: String) {
    init { require(address.length <= 500) }
    fun toPublicLocation(neighborhood: NeighborhoodRef): PublicLocation =
        PublicLocation(neighborhood, coordinate?.let { Geohash.encode(it, 5) })
}

object Geohash {
    internal const val ALPHABET = "0123456789bcdefghjkmnpqrstuvwxyz"

    fun encode(coordinate: GeoCoordinate, precision: Int = 5): String {
        require(precision in 1..12)
        var latitudeLow = -90.0
        var latitudeHigh = 90.0
        var longitudeLow = -180.0
        var longitudeHigh = 180.0
        var longitudeBit = true
        var bits = 0
        var value = 0
        val output = StringBuilder(precision)
        while (output.length < precision) {
            value = value shl 1
            if (longitudeBit) {
                val midpoint = (longitudeLow + longitudeHigh) / 2
                if (coordinate.longitude >= midpoint) { value = value or 1; longitudeLow = midpoint }
                else longitudeHigh = midpoint
            } else {
                val midpoint = (latitudeLow + latitudeHigh) / 2
                if (coordinate.latitude >= midpoint) { value = value or 1; latitudeLow = midpoint }
                else latitudeHigh = midpoint
            }
            longitudeBit = !longitudeBit
            if (++bits == 5) { output.append(ALPHABET[value]); bits = 0; value = 0 }
        }
        return output.toString()
    }
}

/**
 * Limited pilot catalog, not Turkey-wide coverage and not official government IDs.
 * Names checked against https://www.buca.bel.tr/tr/muhtarliklar on 2026-10-07.
 * No invented centroids or device coordinates are supplied.
 */
object PilotNeighborhoodCatalog {
    val neighborhoods: List<NeighborhoodRef> = listOf(
        "adatepe" to "Adatepe",
        "efeler" to "Efeler",
        "inonu" to "İnönü",
        "sirinkapi" to "Şirinkapı",
        "yigitler" to "Yiğitler"
    ).map { (key, name) ->
        NeighborhoodRef("tr_35", "tr_35_buca", "pilot_tr_35_buca_$key", "İzmir", "Buca", name)
    }

    fun findById(neighborhoodId: String): NeighborhoodRef? = neighborhoods.firstOrNull { it.neighborhoodId == neighborhoodId }
}
