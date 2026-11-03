package ma7moud3ly.fast.compose.project

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProjectFilesTest {

    /** Files by path; folders are implied by the paths. */
    private class MemoryFiles(private val files: Map<String, String>) : ProjectFiles {
        override fun list(path: String): List<FileEntry> =
            files.keys.filter { it.startsWith("$path/") }.map { it.removePrefix("$path/") }
                .groupBy { it.substringBefore('/') }
                .map { (name, rest) -> FileEntry(name, rest.any { '/' in it }, files["$path/$name"]?.length?.toLong() ?: 0L) }

        override fun isFolder(path: String): Boolean = files.keys.any { it.startsWith("$path/") }
        override fun read(path: String): String? = files[path]
        override fun version(path: String): Long = files[path]?.hashCode()?.toLong() ?: 0L
    }

    private val common = "app/src/commonMain/kotlin"

    private val files = MemoryFiles(
        mapOf(
            "$common/app/ui/Screen.kt" to "package app.ui\n\nfun screen() = card() + platform()",
            "$common/app/ui/Cards.kt" to "package app.ui\n\nfun card() = \"card\"\n\nprivate fun secret() = 1",
            "$common/app/ui/Platform.kt" to "package app.ui\n\nexpect fun platform(): String\n\nfun label() = \"label\"",
            "app/src/androidMain/kotlin/app/ui/Platform.android.kt" to "package app.ui\n\nactual fun platform() = \"android\"",
            "app/src/jvmMain/kotlin/app/ui/Platform.jvm.kt" to "package app.ui\n\nactual fun platform() = \"jvm\"",
            "app/src/commonMain/composeResources/values/strings.xml" to "<resources/>",
            "app/src/androidMain/res/values/strings.xml" to "<resources/>",
            "lib-sources/io/lib/Theme.kt" to "package io.lib\n\ndata class Theme(val dark: Boolean)\n\nfun themed() = 1",
        ),
    )

    private val sources = KotlinSources(files)

    @Test
    fun findsDeclarationsInOtherFiles() {
        val found = sources.find("app.ui", "card", listOf(common), from = "$common/app/ui/Screen.kt")
        assertEquals("$common/app/ui/Cards.kt", found?.path)
        // Private declarations and the previewed file itself are not found.
        assertNull(sources.find("app.ui", "secret", listOf(common)))
        assertNull(sources.find("app.ui", "screen", listOf(common), from = "$common/app/ui/Screen.kt"))
    }

    @Test
    fun expectsAreReplacedByTheJvmActual() {
        val actual = sources.find("app.ui", "platform", listOf(common))
        assertEquals("app/src/jvmMain/kotlin/app/ui/Platform.jvm.kt", actual?.path)
        // The file of the expect is served with it blanked out, lines kept.
        val label = sources.find("app.ui", "label", listOf(common))!!
        assertFalse("expect" in label.text, label.text)
        assertEquals(5, label.text.lines().size)
        assertEquals("$common/app/ui/Platform.kt", sources.findExpect("app.ui", "platform", listOf(common))?.path)
    }

    @Test
    fun folderRootsWithoutAProjectModel() = runTest {
        val from = "app/src/androidMain/kotlin/app/ui/Platform.android.kt"
        assertEquals("app/src/androidMain/kotlin", sources.sourceRootOf(from))
        assertEquals(listOf("app/src/androidMain/kotlin", common), sources.folderRoots(from))
        val provider = FolderSources(sources, from)
        assertEquals("$common/app/ui/Cards.kt", provider.find("app.ui", "card")?.path)
        assertTrue(provider.isProjectPackage("app.ui"))
        assertFalse(provider.isProjectPackage("io.lib"))
    }

    @Test
    fun librarySourcesServeClassesOnly() {
        assertEquals("lib-sources/io/lib/Theme.kt", sources.findLibrary("io.lib", "Theme", listOf("lib-sources"))?.path)
        assertNull(sources.findLibrary("io.lib", "themed", listOf("lib-sources")))
    }

    @Test
    fun gradleLayout() {
        assertEquals("app/src/androidMain", GradleLayout.sourceSetOf("app/src/androidMain/kotlin/app/ui/A.kt"))
        assertNull(GradleLayout.sourceSetOf("app/A.kt"))
        assertTrue(GradleLayout.isPreviewRoot("app/build/generated/buildConfig/kotlin"))
        assertFalse(GradleLayout.isPreviewRoot("app/build/generated/compose/resourceGenerator/kotlin"))
        assertFalse(GradleLayout.isPreviewRoot("app/build/tmp/kotlin"))
        assertEquals(
            listOf("app/src/androidMain/res", "app/src/commonMain/composeResources"),
            GradleLayout.resourceFolders(files, "app/src/androidMain/kotlin/app/ui/Platform.android.kt"),
        )
    }
}
