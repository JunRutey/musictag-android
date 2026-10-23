/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor

import com.xjcheng.musictageditor.compat.CompatContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Guards the compatibility contract against accidental edits.
 *
 * This runs on the JVM during `testDebugUnitTest` (no device, no aapt2), so a PR that
 * drops a MIME type, a path pattern, a component or an authority fails before it can
 * reach a caller. The CI job additionally diffs the *merged* manifest of the built APK
 * against docs/MANIFEST_CONTRACT.json to catch library-injected components.
 */
class ManifestContractTest {

    private val androidNs = "http://schemas.android.com/apk/res/android"
    private val manifest: Element by lazy { parse("src/main/AndroidManifest.xml").documentElement }

    private fun parse(path: String): org.w3c.dom.Document {
        val f = File(path)
        assertTrue("missing $path (unit tests run with the module dir as CWD)", f.exists())
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isValidating = false
        }
        return factory.newDocumentBuilder().parse(f)
    }

    private fun attr(element: Element, localName: String): String? {
        // android:name in the manifest, plain `name` in res/xml/*.xml.
        val namespaced = element.getAttributeNS(androidNs, localName)
        if (namespaced.isNotEmpty()) return namespaced
        val plain = element.getAttribute(localName)
        return plain.takeIf { it.isNotEmpty() }
    }

    private fun children(element: Element, tag: String): List<Element> {
        val out = mutableListOf<Element>()
        val nodes = element.childNodes
        for (i in 0 until nodes.length) {
            val n = nodes.item(i)
            if (n.nodeType == Node.ELEMENT_NODE && (n as Element).tagName == tag) out.add(n)
        }
        return out
    }

    private fun components(tag: String): List<Element> =
        children(children(manifest, "application").single(), tag)

    private fun resolvedName(element: Element): String? {
        val raw = attr(element, "name") ?: return null
        // The applicationId lives in build.gradle.kts: AGP 8 rejects a `package`
        // attribute in the source manifest, so resolve relative names against the
        // documented contract value instead of the manifest root.
        return if (raw.startsWith(".")) CompatContract.PACKAGE + raw else raw
    }

    private fun filters(activity: Element): List<Element> = children(activity, "intent-filter")

    private fun actions(filter: Element): Set<String> =
        children(filter, "action").mapNotNull { attr(it, "name") }.toSet()

    private fun categories(filter: Element): Set<String> =
        children(filter, "category").mapNotNull { attr(it, "name") }.toSet()

    private fun dataAttrs(filter: Element): List<Element> = children(filter, "data")

    private fun mimeTypes(filter: Element): Set<String> =
        dataAttrs(filter).mapNotNull { attr(it, "mimeType") }.toSet()

    private fun schemes(filter: Element): Set<String> =
        dataAttrs(filter).mapNotNull { attr(it, "scheme") }.toSet()

    private fun pathPatterns(filter: Element): Set<String> =
        dataAttrs(filter).mapNotNull { attr(it, "pathPattern") }
            .map { androidUnescape(it) }
            .toSet()

    /**
     * AAPT2 compiles manifest attribute values as Android strings, so a single
     * backslash in the source is consumed: `.*\.ape` ships as `.*.ape`, while
     * `.*\\.ape` ships as the intended `.*\.ape`. Compare in compiled form.
     */
    private fun androidUnescape(value: String): String {
        val out = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c != '\\' || i == value.length - 1) {
                out.append(c)
                i++
                continue
            }
            when (val next = value[i + 1]) {
                '\\' -> out.append('\\')
                'n' -> out.append('\n')
                't' -> out.append('\t')
                'r' -> out.append('\r')
                else -> out.append(next)
            }
            i += 2
        }
        return out.toString()
    }

    // ------------------------------------------------------------------ tests

    @Test
    fun applicationIdAndNamespaceStayTheOriginalPackage() {
        // AGP 8 keeps the identity in the build script, not in the manifest, so this is
        // where an accidental rename would happen.
        val script = File("build.gradle.kts")
        assertTrue("missing app/build.gradle.kts", script.exists())
        val text = script.readText()
        val applicationId = Regex("""applicationId\s*=\s*"([^"]+)"""")
            .find(text)?.groupValues?.get(1)
        val namespace = Regex("""namespace\s*=\s*"([^"]+)"""")
            .find(text)?.groupValues?.get(1)
        assertEquals(CompatContract.PACKAGE, applicationId)
        assertEquals(CompatContract.PACKAGE, namespace)
    }

    @Test
    fun everyComponentClassStaysRegistered() {
        val declared = buildList {
            components("activity").forEach { resolvedName(it)?.let(::add) }
            components("service").forEach { resolvedName(it)?.let(::add) }
            components("provider").forEach { resolvedName(it)?.let(::add) }
        }.toSet()
        for (expected in CompatContract.COMPONENT_CLASSES) {
            assertTrue("component missing from manifest: $expected (declared: $declared)", expected in declared)
        }
    }

    @Test
    fun exportedFlagsMatchTheContract() {
        val exported = mutableMapOf<String, String?>()
        for (tag in listOf("activity", "service", "provider")) {
            for (c in components(tag)) resolvedName(c)?.let { exported[it] = attr(c, "exported") }
        }
        // Three entry points must stay reachable by third-party callers.
        assertEquals("true", exported["com.xjcheng.musictageditor.MainActivity"])
        assertEquals("true", exported["com.xjcheng.musictageditor.SongDetailActivity"])
        assertEquals("true", exported["com.xjcheng.musictageditor.activity.SearchActivity"])
        // Everything else stays private.
        for ((name, value) in exported) {
            if (name == "com.xjcheng.musictageditor.MainActivity" ||
                name == "com.xjcheng.musictageditor.SongDetailActivity" ||
                name == "com.xjcheng.musictageditor.activity.SearchActivity"
            ) continue
            assertEquals("$name must not be exported", "false", value)
        }
    }

    @Test
    fun songDetailHandlesTypedAudioViewIntents() {
        val activity = components("activity").first { resolvedName(it) == "com.xjcheng.musictageditor.SongDetailActivity" }
        val viewFilter = filters(activity).first {
            "android.intent.action.VIEW" in actions(it) && mimeTypes(it).size > 1
        }
        assertEquals(CompatContract.AUDIO_MIME_TYPES.toSet(), mimeTypes(viewFilter))
        assertEquals(setOf("file", "content"), schemes(viewFilter))
        assertTrue("android.intent.category.DEFAULT" in categories(viewFilter))
    }

    @Test
    fun songDetailHandlesExtensionBasedViewIntents() {
        val activity = components("activity").first { resolvedName(it) == "com.xjcheng.musictageditor.SongDetailActivity" }
        val starFilter = filters(activity).first { "*/*" in mimeTypes(it) && pathPatterns(it).isNotEmpty() }
        assertEquals(CompatContract.EXTENSION_PATH_PATTERNS.toSet(), pathPatterns(starFilter))
        assertTrue("*" in dataAttrs(starFilter).mapNotNull { attr(it, "host") }.toSet())
        assertTrue("android.intent.action.VIEW" in actions(starFilter))
    }

    @Test
    fun songDetailHandlesSendIntents() {
        val activity = components("activity").first { resolvedName(it) == "com.xjcheng.musictageditor.SongDetailActivity" }
        val send = filters(activity).first { "android.intent.action.SEND" in actions(it) }
        assertTrue("*/*" in mimeTypes(send))
        assertTrue("android.intent.category.DEFAULT" in categories(send))
    }

    @Test
    fun searchActivityStaysSearchable() {
        val activity = components("activity").first { resolvedName(it) == "com.xjcheng.musictageditor.activity.SearchActivity" }
        assertTrue(filters(activity).any { "android.intent.action.SEARCH" in actions(it) })
        val meta = children(activity, "meta-data").first { attr(it, "name") == "android.app.searchable" }
        assertNotNull(meta)
    }

    @Test
    fun providersKeepTheirAuthorities() {
        val providers = components("provider")
        val sugg = providers.first { resolvedName(it) == "com.xjcheng.musictageditor.SearchSuggProvider" }
        assertEquals(CompatContract.SUGGEST_AUTHORITY, attr(sugg, "authorities"))

        val fileProvider = providers.first { resolvedName(it) == "androidx.core.content.FileProvider" }
        assertEquals(CompatContract.FILE_PROVIDER_AUTHORITY, attr(fileProvider, "authorities"))
        assertEquals("true", attr(fileProvider, "grantUriPermissions"))
    }

    @Test
    fun searchableXmlKeepsTheContractValues() {
        val doc = parse("src/main/res/xml/searchable.xml").documentElement
        assertEquals(CompatContract.SUGGEST_AUTHORITY, attr(doc, "searchSuggestAuthority"))
        assertEquals(" ?", attr(doc, "searchSuggestSelection"))
        assertEquals("android.intent.action.VIEW", attr(doc, "searchSuggestIntentAction"))
        assertEquals(CompatContract.SUGGEST_INTENT_DATA, attr(doc, "searchSuggestIntentData"))
    }

    @Test
    fun providerPathsKeepsTheStorageRoot() {
        val doc = parse("src/main/res/xml/provider_paths.xml").documentElement
        val roots = children(doc, "root-path")
        assertEquals(1, roots.size)
        assertEquals("storage", attr(roots[0], "name"))
        assertEquals("storage", attr(roots[0], "path"))
    }
}
