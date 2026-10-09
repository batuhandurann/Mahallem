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

    @Test
    fun android12BackupAndTransferExcludeLocalData() {
        val doc = xml("src/main/res/xml/data_extraction_rules.xml")
        val privateDomains = setOf("root", "file", "database", "sharedpref", "external")
        for (sectionName in listOf("cloud-backup", "device-transfer")) {
            val sections = doc.getElementsByTagName(sectionName)
            assertTrue("Missing $sectionName rules", sections.length == 1)
            val children = sections.item(0).childNodes
            val excluded = (0 until children.length).map { children.item(it) }
                .filter { it.nodeName == "exclude" }
                .mapNotNull { child ->
                    val domain = child.attributes?.getNamedItem("domain")?.nodeValue
                    val path = child.attributes?.getNamedItem("path")?.nodeValue
                    if (path == ".") domain else null
                }.toSet()
            assertTrue("$sectionName must exclude all local data domains: $excluded",
                excluded.containsAll(privateDomains))
            assertTrue("$sectionName must not include private data",
                (0 until children.length).none { children.item(it).nodeName == "include" })
        }
    }

    @Test
    fun manifestReferencesBothBackupRuleFiles() {
        val attrs = xml("src/main/AndroidManifest.xml")
            .getElementsByTagName("application").item(0).attributes
        assertTrue("Android 12+ extraction rules must be wired",
            attrs.getNamedItem("android:dataExtractionRules")?.nodeValue ==
                "@xml/data_extraction_rules")
        assertTrue("Legacy backup rules must be wired",
            attrs.getNamedItem("android:fullBackupContent")?.nodeValue ==
                "@xml/backup_rules")
    }
}
