package com.batuhanduran.burada

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Regression guard for the canonical app's local account-scoped data.
 * Paths are relative to the app Gradle module working directory.
 */
class BackupPrivacyConfigurationTest {
    private fun xml(path: String) =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path))

    @Test
    fun appDisablesAndroidBackup() {
        val app = xml("src/main/AndroidManifest.xml").getElementsByTagName("application").item(0)
        val attrs = app.attributes
        val allowBackup = attrs.getNamedItem("android:allowBackup")
        assertTrue("Android backup must be explicitly disabled", allowBackup?.nodeValue == "false")
    }

    @Test
    fun legacyBackupRulesExcludeAppPrivateRoot() {
        val doc = xml("src/main/res/xml/backup_rules.xml")
        val excludes = doc.getElementsByTagName("exclude")
        assertTrue("Legacy backup rules must exclude app-private root",
            (0 until excludes.length).any { index ->
                val node = excludes.item(index)
                node.attributes.getNamedItem("domain")?.nodeValue == "root" &&
                    node.attributes.getNamedItem("path")?.nodeValue == "."
            })
    }
}
