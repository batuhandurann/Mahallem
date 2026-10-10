package com.batuhanduran.burada

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Public-facing Yakıno copy changes must not rename the canonical installed Firebase app. */
@RunWith(AndroidJUnit4::class)
class YakinoBrandResourcesTest {
    @Test
    fun visibleAppLabelAndPrivateNotificationUseYakino() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("Yakıno", app.getString(R.string.app_name))
        assertEquals(
            "Yakıno'da yeni bir mesajınız var.",
            app.getString(R.string.chat_new_message_notification)
        )
    }

    @Test
    fun publicRebrandingDoesNotChangeCanonicalAndroidApplicationId() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.batuhanduran.burada", app.packageName)
    }
}
