package com.batuhanduran.burada.ui.screens

import com.batuhanduran.burada.data.model.SectorType
import org.junit.Assert.assertEquals
import org.junit.Test

class RequestSectorLabelTest {
    @Test fun everySupportedSectorUsesItsOwnTurkishLabel() {
        SectorType.values().filter { it != SectorType.ALL }.forEach { sector ->
            assertEquals(sector.titleTr, requestSectorLabel(sector.name))
        }
    }

    @Test fun cleaningIsNeverEntertainment() {
        assertEquals("Temizlik & Bakım", requestSectorLabel("CLEANING"))
        assertEquals(RequestDetailsKind.GENERAL, requestDetailsKind("CLEANING"))
    }

    @Test fun movingIsNeverEntertainment() {
        assertEquals("Nakliye & Montaj", requestSectorLabel("MOVING_ASSEMBLY"))
        assertEquals(RequestDetailsKind.GENERAL, requestDetailsKind("MOVING_ASSEMBLY"))
    }

    @Test fun tutoringAndCareShowNoEventFields() {
        assertEquals(RequestDetailsKind.GENERAL, requestDetailsKind("TUTORING_CONSULTING"))
        assertEquals(RequestDetailsKind.GENERAL, requestDetailsKind("PERSONAL_CARE"))
    }

    @Test fun renovationAndEventKeepCorrectFields() {
        assertEquals(RequestDetailsKind.RENOVATION, requestDetailsKind("HOME_REPAIR"))
        assertEquals(RequestDetailsKind.EVENT, requestDetailsKind("EVENT_ENTERTAINMENT"))
    }

    @Test fun unknownSectorNeverMasqueradesAsEntertainment() {
        listOf("", "UNKNOWN", "ALL", " ").forEach {
            assertEquals("Diğer Hizmet", requestSectorLabel(it))
            assertEquals(RequestDetailsKind.GENERAL, requestDetailsKind(it))
        }
    }

    @Test fun savedCodesAreNormalizedRegardlessOfLocale() {
        assertEquals("Temizlik & Bakım", requestSectorLabel(" cleaning "))
        assertEquals(RequestDetailsKind.EVENT, requestDetailsKind(" event_entertainment "))
    }
}
