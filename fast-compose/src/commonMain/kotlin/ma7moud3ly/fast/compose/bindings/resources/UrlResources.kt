package ma7moud3ly.fast.compose.bindings.resources

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/**
 * Resources downloaded from [baseUrl] when previews first need them, laid out like `res/` or `composeResources/`.
 * Until a file arrives, images show a placeholder and strings are empty; previews update once it is stored.
 *
 * When `resources.json` at [baseUrl] lists the files (a JSON array of paths such as `"drawable/logo.png"`), only
 * listed files are downloaded, including locale, night and density variants. Without it, strings are read from
 * `values/strings.xml` and `values-<locale>/strings.xml`, and images are found by trying `.png`, `.webp`, `.jpg`
 * and `.xml` in the default and `-night` folders.
 *
 * [fetch] downloads a URL and returns null when it fails; [scope] runs the downloads and should use the UI thread.
 */
public class UrlResources(
    baseUrl: String,
    private val scope: CoroutineScope,
    private val fetch: suspend (url: String) -> ByteArray?,
) : ResourceProvider {

    private val base = if (baseUrl.isEmpty() || baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
    private val files = MemoryResources()
    private val requested = mutableSetOf<String>()

    /** Requests still downloading. Not state, since requests start during composition; [finished] signals the end. */
    private val pending = mutableSetOf<String>()
    private var finished by mutableIntStateOf(0)
    private val index = scope.async(start = CoroutineStart.LAZY) {
        fetch(base + INDEX)?.decodeToString()?.let { json -> quoted.findAll(json).map { it.groupValues[1] }.toList() }
    }

    override fun bytes(type: String, name: String, environment: ResourceEnvironment): ByteArray? {
        request(imageKey(type, name, environment)) { paths ->
            if (paths != null) {
                downloadAll(paths.filter { fileName(it) == name && ResourceFolder.parse(it.substringBefore('/')).type == type })
            } else {
                val folders = listOfNotNull(type, if (environment.isDark) "$type-night" else null)
                for (folder in folders) downloadFirst(extensions.map { "$folder/$name.$it" })
            }
        }
        return files.bytes(type, name, environment)
    }

    override fun string(name: String, environment: ResourceEnvironment): String? {
        request(stringsKey(environment)) { paths ->
            if (paths != null) {
                downloadAll(paths.filter { it.endsWith(".xml") && ResourceFolder.parse(it.substringBefore('/')).let { folder -> folder.type == "values" && folder.score(environment) != null } })
            } else {
                downloadAll(stringFolders(environment.locale).map { "$it/strings.xml" })
            }
        }
        return files.string(name, environment)
    }

    override fun isLoading(type: String, name: String, environment: ResourceEnvironment): Boolean {
        finished // read so previews check again when a download finishes
        return (if (type == "string") stringsKey(environment) else imageKey(type, name, environment)) in pending
    }

    /** Runs [download] once per [key], with the paths listed in `resources.json`, or null when there is no index. */
    private fun request(key: String, download: suspend (paths: List<String>?) -> Unit) {
        if (!requested.add(key)) return
        pending.add(key)
        scope.launch {
            try {
                download(index.await())
            } finally {
                pending.remove(key)
                finished++
            }
        }
    }

    private fun imageKey(type: String, name: String, environment: ResourceEnvironment) = "$type/$name/${environment.isDark}"

    private fun stringsKey(environment: ResourceEnvironment) = "strings/${environment.locale}"

    private suspend fun downloadAll(paths: List<String>) {
        for (path in paths) if (files.read(path) == null) fetch(base + path)?.let { files.put(path, it) }
    }

    /** Stores the first of [paths] that downloads. */
    private suspend fun downloadFirst(paths: List<String>) {
        for (path in paths) {
            val bytes = fetch(base + path) ?: continue
            files.put(path, bytes)
            return
        }
    }

    private companion object {
        const val INDEX = "resources.json"
        val extensions = listOf("png", "webp", "jpg", "xml")
        val quoted = Regex("\"([^\"]+)\"")

        fun fileName(path: String): String = path.substringAfterLast('/').substringBefore('.')

        /** `values`, then `values-pt` and `values-pt-rBR` for locale `pt-BR`. */
        fun stringFolders(locale: String?): List<String> {
            if (locale == null) return listOf("values")
            val (language, region) = ResourceFolder.parseLocale(locale)
            return listOfNotNull("values", "values-$language", region?.let { "values-$language-r$it" })
        }
    }
}
