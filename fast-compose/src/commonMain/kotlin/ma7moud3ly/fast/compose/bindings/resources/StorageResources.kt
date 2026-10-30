package ma7moud3ly.fast.compose.bindings.resources

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** A file in a [ResourceStorage]: its [path], e.g. `drawable/logo.png`, and a [version] that changes when the file does. */
public data class StoredFile(val path: String, val version: Long)

/** Files of a `res/` or `composeResources/` folder in any file system, e.g. `java.io`, FileKit or Okio. */
public interface ResourceStorage {

    /** Paths relative to the folder, e.g. `drawable/logo.png`, with their versions, such as last-modified times. */
    public suspend fun list(): List<StoredFile>

    /** Bytes of the file at [path], or null when it cannot be read. */
    public suspend fun read(path: String): ByteArray?
}

/**
 * Resources read from a [ResourceStorage] when previews need them. Only paths are listed up front, and only from
 * folders previews use (`drawable`, `mipmap`, `values`, `font`, `files`). A file is read in the background the first
 * time a preview needs it and kept in a cache of up to [cacheBytes]; previews show a placeholder until it arrives.
 *
 * Call [refresh] after files change, e.g. from a file watcher: changed or deleted files are dropped from the cache and
 * read again when needed. [scope] runs the reads and should use the UI thread.
 */
public class StorageResources(
    private val storage: ResourceStorage,
    private val scope: CoroutineScope,
    private val cacheBytes: Long = 32L * 1024 * 1024,
) : ResourceProvider {

    /** Listed files by path with their versions, or null before the first listing. */
    private var files by mutableStateOf<Map<String, Long>?>(null)

    /** Read files by `path@version`. */
    private val cache = mutableStateMapOf<String, ByteArray>()

    /** Files being read, by `path@version`. Not state, since reads start during composition; [finishedReads] signals the end. */
    private val loading = mutableSetOf<String>()

    /** Changes whenever a read finishes, so previews that saw a file loading look again. */
    private var finishedReads by mutableIntStateOf(0)

    /** Cache keys, least recently used first. */
    private val usage = ArrayDeque<String>()
    private var cachedBytes = 0L
    private val parsedStrings = HashMap<String, Map<String, String>>()

    init {
        scope.launch { refresh() }
    }

    /** Lists the files again; files that changed or were deleted are read again when previews need them. */
    public suspend fun refresh() {
        val listed = storage.list()
            .filter { ResourceSelection.isPreviewFolder(it.path.substringBefore('/', missingDelimiterValue = "")) }
            .associate { it.path to it.version }
        files = listed
        val current = listed.map { (path, version) -> key(path, version) }.toSet()
        cache.keys.filter { it !in current }.forEach(::evict)
    }

    override fun bytes(type: String, name: String, environment: ResourceEnvironment): ByteArray? {
        val listed = files ?: return null
        return ResourceSelection.bestFile(listed.keys, type, name, environment)?.let(::load)
    }

    override fun string(name: String, environment: ResourceEnvironment): String? {
        val listed = files ?: return null
        for (path in ResourceSelection.stringFiles(listed.keys, environment)) {
            val bytes = load(path) ?: continue
            val strings = parsedStrings.getOrPut(key(path, listed.getValue(path))) { StringsXml.parse(bytes.decodeToString()) }
            strings[name]?.let { return it }
        }
        return null
    }

    override fun isLoading(type: String, name: String, environment: ResourceEnvironment): Boolean {
        val listed = files ?: return true
        finishedReads // read so previews check again when a read finishes
        val paths = if (type == "string") {
            ResourceSelection.stringFiles(listed.keys, environment)
        } else {
            listOfNotNull(ResourceSelection.bestFile(listed.keys, type, name, environment))
        }
        return paths.any { key(it, listed.getValue(it)) in loading }
    }

    /** Cached bytes of [path], or null while they are read in the background. */
    private fun load(path: String): ByteArray? {
        val version = files?.get(path) ?: return null
        val key = key(path, version)
        cache[key]?.let { bytes ->
            usage.remove(key)
            usage.addLast(key)
            return bytes
        }
        if (loading.add(key)) {
            scope.launch {
                val bytes = try {
                    storage.read(path)
                } finally {
                    loading.remove(key)
                    finishedReads++
                }
                if (bytes != null && files?.get(path) == version) store(key, bytes)
            }
        }
        return null
    }

    private fun store(key: String, bytes: ByteArray) {
        cache[key] = bytes
        usage.addLast(key)
        cachedBytes += bytes.size
        while (cachedBytes > cacheBytes && usage.size > 1) evict(usage.first())
    }

    private fun evict(key: String) {
        cache.remove(key)?.let { cachedBytes -= it.size }
        usage.remove(key)
        parsedStrings.remove(key)
    }

    private fun key(path: String, version: Long) = "$path@$version"
}
