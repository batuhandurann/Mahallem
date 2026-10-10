package com.batuhanduran.burada

import android.content.ComponentName
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the installed merged manifest rather than merely checking class names.
 * A legacy QA launcher must never replace the canonical entry point.
 */
@RunWith(AndroidJUnit4::class)
class CanonicalLauncherManifestTest {
    @Test
    fun installedLauncherResolvesToCanonicalMainActivity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.batuhanduran.burada", context.packageName)

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        assertNotNull("Installed application must expose a launcher activity", launchIntent)
        assertEquals(
            ComponentName(context.packageName, MainActivity::class.java.name),
            launchIntent!!.component
        )
    }

    @Test
    fun installedApplicationClassRemainsCanonical() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val applicationInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
        assertEquals(BuradaApplication::class.java.name, applicationInfo.className)
    }
}
