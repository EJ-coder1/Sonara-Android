package com.sonara.player.data

import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Keeps the three places that describe a launcher icon in sync:
 * the AppIcon list, the <activity-alias> entries in the manifest and the icon files.
 * (Unit tests run from the app/ folder, so the paths below are relative to it.)
 */
class AppIconTest {
    private val androidNs = "http://schemas.android.com/apk/res/android"
    private val manifest = File("src/main/AndroidManifest.xml")
    private val iconDir = File("src/main/res/mipmap-anydpi-v26")

    private fun parse(file: File) =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(file)

    private fun Element.android(name: String): String = getAttributeNS(androidNs, name)

    private fun Element.children(tag: String): List<Element> {
        val nodes = getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun aliases(): List<Element> = parse(manifest).documentElement.children("activity-alias")

    @Test
    fun everyIconHasItsOwnNameAndAlias() {
        assertEquals(AppIcon.entries.size, AppIcon.entries.map { it.label }.toSet().size)
        assertEquals(AppIcon.entries.size, AppIcon.entries.map { it.alias }.toSet().size)
    }

    @Test
    fun manifestHasOneAliasPerIconAndOnlyTheDefaultIsEnabled() {
        val byName = aliases().associateBy { it.android("name") }
        assertEquals(AppIcon.entries.size, byName.size, "number of <activity-alias> entries")
        for (icon in AppIcon.entries) {
            val alias = assertNotNull(byName[".${icon.alias}"], "manifest alias for ${icon.name}")
            assertEquals(".MainActivity", alias.android("targetActivity"), icon.name)
            assertEquals("true", alias.android("exported"), icon.name)
            assertEquals((icon == AppIcon.DEFAULT).toString(), alias.android("enabled"), "enabled flag of ${icon.name}")
            assertEquals("@mipmap/${icon.fileName}", alias.android("icon"), "icon of ${icon.name}")
        }
    }

    @Test
    fun everyAliasIsALauncherEntry() {
        for (alias in aliases()) {
            val filter = alias.children("intent-filter").firstOrNull()
            assertNotNull(filter, "intent-filter of ${alias.android("name")}")
            val actions = filter.children("action").map { it.android("name") }
            val categories = filter.children("category").map { it.android("name") }
            assertTrue("android.intent.action.MAIN" in actions, "MAIN action of ${alias.android("name")}")
            assertTrue("android.intent.category.LAUNCHER" in categories, "LAUNCHER category of ${alias.android("name")}")
        }
    }

    @Test
    fun mainActivityHasNoLauncherEntryOfItsOwn() {
        val activity = parse(manifest).documentElement.children("activity")
            .first { it.android("name") == ".MainActivity" }
        assertEquals(0, activity.children("intent-filter").size, "MainActivity would show a second icon")
    }

    @Test
    fun everyIconFileExistsAndIsAnAdaptiveIcon() {
        for (icon in AppIcon.entries) {
            val file = File(iconDir, icon.fileName + ".xml")
            assertTrue(file.exists(), "missing icon file ${file.path}")
            val root = parse(file).documentElement
            assertEquals("adaptive-icon", root.tagName, file.name)
            assertTrue(root.children("background").isNotEmpty(), "${file.name} has no background")
            assertTrue(root.children("foreground").isNotEmpty(), "${file.name} has no foreground")
        }
    }
}
