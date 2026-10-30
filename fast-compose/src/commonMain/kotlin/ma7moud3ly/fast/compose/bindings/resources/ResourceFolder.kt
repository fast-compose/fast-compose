package ma7moud3ly.fast.compose.bindings.resources

/**
 * A resource folder name such as `drawable-night-xxhdpi` or `values-pt-rBR`, split into its [type] and qualifiers.
 * [language] and [region] are lowercase/uppercase codes, [isDark] is null when the folder has no theme qualifier,
 * [density] is null for density-independent folders, and [hasOtherQualifiers] marks qualifiers previews ignore,
 * such as `land` or `sw600dp`.
 */
internal data class ResourceFolder(
    val type: String,
    val language: String? = null,
    val region: String? = null,
    val isDark: Boolean? = null,
    val density: Float? = null,
    val hasOtherQualifiers: Boolean = false,
) {
    /** How well this folder fits [environment], or null when it does not fit; higher is better. */
    fun score(environment: ResourceEnvironment): Int? {
        if (isDark != null && isDark != environment.isDark) return null
        var score = if (isDark != null) 4 else 0
        if (language != null) {
            val wanted = environment.locale?.let(::parseLocale) ?: return null
            if (wanted.first != language) return null
            if (region != null && region != wanted.second) return null
            score += if (region != null) 16 else 8
        }
        if (hasOtherQualifiers) score -= 32
        return score
    }

    companion object {
        private val densities = mapOf(
            "ldpi" to 0.75f, "mdpi" to 1f, "tvdpi" to 1.33f, "hdpi" to 1.5f,
            "xhdpi" to 2f, "xxhdpi" to 3f, "xxxhdpi" to 4f,
        )
        private val versionCode = Regex("v\\d+")
        private val languageCode = Regex("[a-z]{2,3}")
        private val regionCode = Regex("r?[A-Z]{2}")

        /** Parses a folder name such as `drawable-ar-night`, `values-b+pt+BR` or `mipmap-xxxhdpi`. */
        fun parse(name: String): ResourceFolder {
            val parts = name.split('-')
            var folder = ResourceFolder(parts.first())
            var i = 1
            while (i < parts.size) {
                val part = parts[i]
                folder = when {
                    part == "night" || part == "dark" -> folder.copy(isDark = true)
                    part == "notnight" || part == "light" -> folder.copy(isDark = false)
                    part in densities -> folder.copy(density = densities.getValue(part))
                    part == "nodpi" || part == "anydpi" || versionCode.matches(part) -> folder
                    part.startsWith("b+") -> {
                        val (language, region) = parseLocale(part.removePrefix("b+").replace('+', '-'))
                        folder.copy(language = language, region = region)
                    }
                    folder.language == null && languageCode.matches(part) -> {
                        val next = parts.getOrNull(i + 1)
                        if (next != null && regionCode.matches(next)) {
                            i++
                            folder.copy(language = part, region = next.removePrefix("r"))
                        } else {
                            folder.copy(language = part)
                        }
                    }
                    else -> folder.copy(hasOtherQualifiers = true)
                }
                i++
            }
            return folder
        }

        /** `pt-BR`, `pt_BR` or `pt-rBR` → `pt` and `BR`. */
        fun parseLocale(locale: String): Pair<String, String?> {
            val parts = locale.split('-', '_')
            return parts.first().lowercase() to parts.getOrNull(1)?.removePrefix("r")?.uppercase()
        }
    }
}
