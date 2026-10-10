package com.batuhanduran.burada

import android.content.pm.ApplicationInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests the installed, merged manifest rather than the source XML.
 * Existing BackupPrivacyConfigurationTest covers the source backup rules.
 */
@RunWith(AndroidJUnit4::class)
class CanonicalBackupPolicyInstrumentedTest {
    @Test
    fun installedCanonicalApplicationDisablesAndroidBackup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.batuhanduran.burada", context.packageName)

        @Suppress("DEPRECATION")
        val appInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
        assertEquals(
            "Installed canonical APK must not allow Android Auto Backup",
            0,
            appInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP
        )
    }
}
