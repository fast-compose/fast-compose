package ma7moud3ly.fast.compose.bindings.resources

import kotlin.math.abs

/**
 * Picks resource files by path, e.g. `drawable-night-xxhdpi/logo.png` or `values-ar/strings.xml`: matching locale and
 * theme qualifiers first, then the closest density. Folders with qualifiers previews do not model, such as `land`,
 * are used last.
 */
internal object ResourceSelection {

    /** Folder types that previews read; other folders, such as `raw` or `layout`, are skipped. */
    private val previewTypes = setOf("drawable", "mipmap", "values", "font", "files")

    /** True for folders such as `drawable-night` or `values-ar` that previews read. */
    fun isPreviewFolder(folder: String): Boolean = ResourceFolder.parse(folder).type in previewTypes

    /** The path of resource [name] of [type] that best fits [environment], or null when there is none. */
    fun bestFile(paths: Iterable<String>, type: String, name: String, environment: ResourceEnvironment): String? {
        var best: String? = null
        var bestRank = Int.MIN_VALUE
        for (path in paths) {
            val folderName = path.substringBefore('/', missingDelimiterValue = "")
            if (folderName.isEmpty() || resourceName(path.substringAfterLast('/')) != name) continue
            val folder = ResourceFolder.parse(folderName)
            if (folder.type != type) continue
            val score = folder.score(environment) ?: continue
            val rank = score * 1000 + densityRank(folder.density ?: 1f, environment.density)
            if (rank > bestRank) {
                best = path
                bestRank = rank
            }
        }
        return best
    }

    /** XML files of `values` folders that fit [environment], best first: `values-pt-rBR`, `values-pt`, `values`. */
    fun stringFiles(paths: Iterable<String>, environment: ResourceEnvironment): List<String> =
        paths.filter { it.endsWith(".xml") }
            .mapNotNull { path ->
                val folder = ResourceFolder.parse(path.substringBefore('/', missingDelimiterValue = ""))
                if (folder.type != "values") null else folder.score(environment)?.let { path to it }
            }
            .sortedByDescending { it.second }
            .map { it.first }

    /** `logo.png` and `logo.9.png` → `logo`. */
    fun resourceName(fileName: String): String = fileName.substringBefore('.')

    /** Prefers the density closest to [wanted], and higher densities over lower ones. */
    private fun densityRank(density: Float, wanted: Float): Int {
        val distance = (abs(density - wanted) * 100).toInt()
        return if (density >= wanted) 500 - distance else 250 - distance
    }
}
