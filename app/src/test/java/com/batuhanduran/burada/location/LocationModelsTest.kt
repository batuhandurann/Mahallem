package com.batuhanduran.burada.location

import com.batuhanduran.burada.data.model.GeoCoordinate
import com.batuhanduran.burada.data.model.Geohash
import com.batuhanduran.burada.data.model.NeighborhoodRef
import com.batuhanduran.burada.data.model.PilotNeighborhoodCatalog
import com.batuhanduran.burada.data.model.PrivateLocation
import com.batuhanduran.burada.data.model.PublicLocation
import org.junit.Assert.*
import org.junit.Test

class LocationModelsTest {
    @Test fun rejectsOutOfRangeAndNonFiniteCoordinates() {
        listOf(91.0 to 0.0, -91.0 to 0.0, 0.0 to 181.0, 0.0 to -181.0,
            Double.NaN to 0.0, 0.0 to Double.POSITIVE_INFINITY).forEach { (lat, lon) ->
            assertThrows(IllegalArgumentException::class.java) { GeoCoordinate(lat, lon) }
        }
    }

    @Test fun distanceHandlesIdentityAntimeridianAndAntipodes() {
        val origin = GeoCoordinate(0.0, 0.0)
        assertEquals(0.0, origin.distanceMetersTo(origin), 0.0)
        assertEquals(111_195.0, origin.distanceMetersTo(GeoCoordinate(0.0, 1.0)), 1.0)
        assertEquals(22_239.0, GeoCoordinate(0.0, 179.9).distanceMetersTo(GeoCoordinate(0.0, -179.9)), 1.0)
        assertEquals(20_015_114.0, origin.distanceMetersTo(GeoCoordinate(0.0, 180.0)), 1.0)
        assertEquals(origin.distanceMetersTo(GeoCoordinate(52.0, 13.0)), GeoCoordinate(52.0, 13.0).distanceMetersTo(origin), 0.0001)
    }

    @Test fun geohashMatchesKnownReferenceAndRejectsInvalidPrecision() {
        assertEquals("ezs42", Geohash.encode(GeoCoordinate(42.6, -5.6), 5))
        assertEquals("s0000", Geohash.encode(GeoCoordinate(0.0, 0.0)))
        assertThrows(IllegalArgumentException::class.java) { Geohash.encode(GeoCoordinate(0.0, 0.0), 0) }
        assertThrows(IllegalArgumentException::class.java) { Geohash.encode(GeoCoordinate(0.0, 0.0), 13) }
    }

    @Test fun publicProjectionDropsAddressAndLimitsCoordinatePrecision() {
        val neighborhood = PilotNeighborhoodCatalog.neighborhoods.first()
        val public = PrivateLocation(GeoCoordinate(38.38, 27.18), "Private apartment 17").toPublicLocation(neighborhood)
        assertEquals(5, public.coarseGeohash!!.length)
        assertFalse(public.toString().contains("Private apartment 17"))
        assertFalse(public.toString().contains("38.38"))
        assertNull(PrivateLocation(null, "No GPS consent").toPublicLocation(neighborhood).coarseGeohash)
        assertThrows(IllegalArgumentException::class.java) { PublicLocation(neighborhood, "ezs42g") }
        assertThrows(IllegalArgumentException::class.java) { PublicLocation(neighborhood, "abcde") }
    }

    @Test fun catalogIdentitySurvivesRenamingAndAvoidsUnknownFallbacks() {
        val neighborhood = PilotNeighborhoodCatalog.neighborhoods.first()
        assertTrue(neighborhood.hasSameIdentity(neighborhood.copy(neighborhoodName = "Yeni ad")))
        assertFalse(neighborhood.hasSameIdentity(neighborhood.copy(districtId = "tr_35_other")))
        assertEquals(5, PilotNeighborhoodCatalog.neighborhoods.map { it.neighborhoodId }.distinct().size)
        assertEquals(neighborhood, PilotNeighborhoodCatalog.findById(neighborhood.neighborhoodId))
        assertNull(PilotNeighborhoodCatalog.findById("unknown"))
        assertThrows(IllegalArgumentException::class.java) {
            NeighborhoodRef("tr_35", "tr_35_buca", "../bad", "İzmir", "Buca", "Efeler")
        }
    }
}
