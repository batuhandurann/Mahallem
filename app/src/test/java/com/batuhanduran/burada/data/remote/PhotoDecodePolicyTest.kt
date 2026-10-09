package com.batuhanduran.burada.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoDecodePolicyTest {
    @Test fun smallPhotosKeepOriginalResolution() {
        assertEquals(1, PhotoDecodePolicy.sampleSize(300, 500))
        assertEquals(1, PhotoDecodePolicy.sampleSize(2048, 2048))
    }

    @Test fun portraitLandscapeAndVeryWideImagesAreBounded() {
        assertEquals(2, PhotoDecodePolicy.sampleSize(4000, 3000))
        assertEquals(2, PhotoDecodePolicy.sampleSize(3000, 4000))
        assertEquals(8, PhotoDecodePolicy.sampleSize(16000, 1000))
    }

    @Test fun fractionalBoundariesNeverExceedMaxDisplayEdge() {
        assertEquals(2, PhotoDecodePolicy.sampleSize(4096, 1024))
        assertEquals(4, PhotoDecodePolicy.sampleSize(4097, 1024))
        for (dimension in listOf(2048, 2049, 4000, 4096, 4097, 16000)) {
            val sample = PhotoDecodePolicy.sampleSize(dimension, 1)
            assertTrue(sample > 0 && sample and (sample - 1) == 0)
            assertTrue((dimension.toLong() + sample - 1) / sample <= PhotoDecodePolicy.MAX_DISPLAY_EDGE)
        }
    }

    @Test fun invalidDimensionsFailWithoutAttemptingDecode() {
        for (dimensions in listOf(0 to 100, -10 to 100, 100 to 0, 100 to -1)) {
            try {
                PhotoDecodePolicy.sampleSize(dimensions.first, dimensions.second)
                throw AssertionError("Invalid dimension was accepted")
            } catch (_: IllegalArgumentException) {
                // Expected.
            }
        }
    }

    @Test fun callerCanUseSmallerSizeForAdditionalMemoryPressure() {
        assertEquals(4, PhotoDecodePolicy.sampleSize(3000, 3000, maxEdge = 1024))
    }
}
