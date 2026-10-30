package ma7moud3ly.fast.compose.bindings.resources

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StorageResourcesTest {

    /** Files in memory; counts reads. */
    private class TestStorage : ResourceStorage {
        val files = mutableMapOf<String, Pair<Long, String>>()
        val reads = mutableListOf<String>()

        override suspend fun list() = files.map { (path, file) -> StoredFile(path, file.first) }

        override suspend fun read(path: String): ByteArray? {
            reads += path
            return files[path]?.second?.encodeToByteArray()
        }
    }

    private val environment = ResourceEnvironment()

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun readsOnlyWhatPreviewsNeed() = runTest {
        val storage = TestStorage()
        storage.files["drawable/logo.png"] = 1L to "logo"
        storage.files["drawable/other.png"] = 1L to "other"
        storage.files["raw/video.mp4"] = 1L to "video"
        val resources = StorageResources(storage, this)
        advanceUntilIdle()

        assertNull(resources.bytes("drawable", "logo", environment))
        assertTrue(resources.isLoading("drawable", "logo", environment))
        advanceUntilIdle()
        assertEquals("logo", resources.bytes("drawable", "logo", environment)?.decodeToString())
        assertFalse(resources.isLoading("drawable", "logo", environment))
        resources.bytes("drawable", "logo", environment)
        assertEquals(listOf("drawable/logo.png"), storage.reads)
        assertNull(resources.bytes("raw", "video", environment))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun refreshReadsChangedFilesAgain() = runTest {
        val storage = TestStorage()
        storage.files["values/strings.xml"] = 1L to """<resources><string name="hi">Hi</string></resources>"""
        val resources = StorageResources(storage, this)
        advanceUntilIdle()
        resources.string("hi", environment)
        advanceUntilIdle()
        assertEquals("Hi", resources.string("hi", environment))

        storage.files["values/strings.xml"] = 2L to """<resources><string name="hi">Hello</string></resources>"""
        resources.refresh()
        assertNull(resources.string("hi", environment))
        advanceUntilIdle()
        assertEquals("Hello", resources.string("hi", environment))

        storage.files.remove("values/strings.xml")
        resources.refresh()
        assertNull(resources.string("hi", environment))
        assertFalse(resources.isLoading("string", "hi", environment))
    }
}
