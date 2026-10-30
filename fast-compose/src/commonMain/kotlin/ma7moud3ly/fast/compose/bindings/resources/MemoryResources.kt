package ma7moud3ly.fast.compose.bindings.resources

import androidx.compose.runtime.mutableStateMapOf

/**
 * Resource files kept in memory, laid out like Android `res/` or Compose Multiplatform `composeResources/`:
 * `drawable/logo.png`, `drawable-night/logo.xml`, `drawable-xxhdpi/photo.webp`, `values/strings.xml`,
 * `values-ar/strings.xml`, … Files are Compose state, so previews update when files are added, replaced or removed.
 *
 * Lookups pick the variant that best fits the [ResourceEnvironment], see [ResourceSelection].
 */
public class MemoryResources : ResourceProvider {

    private val files = mutableStateMapOf<String, ByteArray>()

    /** Parsed XML files of `values` folders, by file content. */
    private val parsedStrings = HashMap<ByteArray, Map<String, String>>()

    /** Paths of all files, e.g. `drawable/logo.png`. */
    public val paths: Set<String> get() = files.keys.toSet()

    /** Adds or replaces the file at [path]; a leading `res/` or `composeResources/` folder is ignored. */
    public fun put(path: String, bytes: ByteArray) {
        val key = normalize(path)
        files[key]?.let { parsedStrings.remove(it) }
        files[key] = bytes
    }

    /** Adds or replaces a text file, such as `values/strings.xml`. */
    public fun put(path: String, text: String): Unit = put(path, text.encodeToByteArray())

    public fun remove(path: String) {
        files.remove(normalize(path))?.let { parsedStrings.remove(it) }
    }

    public fun clear() {
        files.clear()
        parsedStrings.clear()
    }

    /** Bytes of the file at [path], or null when there is none. */
    public fun read(path: String): ByteArray? = files[normalize(path)]

    override fun bytes(type: String, name: String, environment: ResourceEnvironment): ByteArray? =
        ResourceSelection.bestFile(files.keys, type, name, environment)?.let { files[it] }

    override fun string(name: String, environment: ResourceEnvironment): String? {
        for (path in ResourceSelection.stringFiles(files.keys, environment)) {
            val bytes = files[path] ?: continue
            strings(bytes)[name]?.let { return it }
        }
        return null
    }

    private fun strings(bytes: ByteArray): Map<String, String> =
        parsedStrings.getOrPut(bytes) { StringsXml.parse(bytes.decodeToString()) }

    private companion object {
        val roots = listOf("composeResources/", "res/")

        /** `src/main/res/drawable/logo.png` → `drawable/logo.png`. */
        fun normalize(path: String): String {
            val clean = path.replace('\\', '/').trim('/')
            for (root in roots) {
                if (clean.startsWith(root)) return clean.removePrefix(root)
                val index = clean.indexOf("/$root")
                if (index >= 0) return clean.substring(index + root.length + 1)
            }
            return clean
        }
    }
}
