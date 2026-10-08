package com.example.ui.screens

import com.example.data.model.SectorType
import org.junit.Assert.assertEquals
import org.junit.Test

class RequestSectorLabelTest {
    @Test fun everySupportedSectorUsesItsOwnTurkishLabel() {
        SectorType.values().filter { it != SectorType.ALL }.forEach { sector ->
            assertEquals(sector.titleTr, requestSectorLabel(sector.name))
        }
    }

    @Test fun cleaningDoesNotLookLikeEntertainment() {
        assertEquals("Temizlik & Bakım", requestSectorLabel("CLEANING"))
        assertEquals(RequestDetailsKind.GENERAL, requestDetailsKind("CLEANING"))
    }

    @Test fun movingDoesNotLookLikeEntertainment() {
        assertEquals("Nakliye & Montaj", requestSectorLabel("MOVING_ASSEMBLY"))
        assertEquals(RequestDetailsKind.GENERAL, requestDetailsKind("MOVING_ASSEMBLY"))
    }

    @Test fun tutoringAndPersonalCareAreGeneralRequests() {
        assertEquals(RequestDetailsKind.GENERAL, requestDetailsKind("TUTORING_CONSULTING"))
        assertEquals(RequestDetailsKind.GENERAL, requestDetailsKind("PERSONAL_CARE"))
    }

    @Test fun renovationAndEventRetainTheirOwnDetailTypes() {
        assertEquals(RequestDetailsKind.RENOVATION, requestDetailsKind("HOME_REPAIR"))
        assertEquals(RequestDetailsKind.EVENT, requestDetailsKind("EVENT_ENTERTAINMENT"))
    }

    @Test fun unknownAndAllDoNotPretendToBeEntertainment() {
        listOf("", "UNKNOWN", "ALL", "  ").forEach { code ->
            assertEquals("Diğer Hizmet", requestSectorLabel(code))
            assertEquals(RequestDetailsKind.GENERAL, requestDetailsKind(code))
        }
    }

    @Test fun storedSectorCodesAreNormalizedSafely() {
        assertEquals("Temizlik & Bakım", requestSectorLabel(" cleaning "))
        assertEquals(RequestDetailsKind.EVENT, requestDetailsKind(" event_entertainment "))
    }
}
