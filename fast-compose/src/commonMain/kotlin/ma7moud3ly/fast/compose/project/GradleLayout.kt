package ma7moud3ly.fast.compose.project

import ma7moud3ly.fast.compose.bindings.resources.ResourceSelection

/** Gradle's project layout as previews read it: source sets, source roots and resource folders, by path. */
public object GradleLayout {

    /** Folders inside a source set that hold Kotlin sources. */
    public val SOURCE_ROOTS: List<String> = listOf("kotlin", "java")

    /** Folders inside a source set that hold resources: Compose Multiplatform's `Res` and Android's `R`. */
    public val RESOURCE_FOLDERS: List<String> = listOf("composeResources", "res")

    /** Source sets every other source set of a module builds on: KMP `commonMain` and Android `main`. */
    public val SHARED_SOURCE_SETS: List<String> = listOf("commonMain", "main")

    /** JVM source sets of a KMP module, whose libraries are JVM jars. */
    public val JVM_SOURCE_SETS: List<String> = listOf("jvmMain", "desktopMain")

    /** The `src/<sourceSet>` folder holding [path], e.g. `app/src/main` or `shared/src/commonMain`; null outside one. */
    public fun sourceSetOf(path: String): String? {
        var dir: String? = path
        while (dir != null) {
            val parent = parentOf(dir) ?: return null
            if (nameOf(parent) == "src") return dir
            dir = parent
        }
        return null
    }

    /**
     * True for source roots previews read: the project's own, and generated ones such as BuildConfig, BuildKonfig or a
     * `BuildInfo` task's output, but not Compose resource accessors, which the engine provides as `Res`.
     */
    public fun isPreviewRoot(path: String): Boolean {
        if ("/build/" !in path) return true
        return "/build/generated/" in path && "/generated/compose/resourceGenerator/" !in path
    }

    /**
     * Order of the platform source sets to take an `actual` from, lowest first: JVM (`jvmMain`, `desktopMain`), then
     * Android, then others, and native and web last.
     */
    public fun platformRank(sourceSet: String): Int {
        val name = sourceSet.lowercase()
        return when {
            name == "jvmmain" || name == "desktopmain" -> 0
            "jvm" in name || "desktop" in name -> 1
            name == "androidmain" -> 2
            listOf("ios", "apple", "macos", "native", "js", "wasm", "linux", "mingw", "tvos", "watchos").any { it in name } -> 4
            else -> 3
        }
    }

    /** True for resource folders such as `drawable-night-xxhdpi` or `values-ar` whose type previews use. */
    public fun isPreviewResourceFolder(name: String): Boolean = ResourceSelection.isPreviewFolder(name)

    /**
     * Resource folders of the file at [path], most specific first: its own source set, then [sourceSets] (e.g. those
     * of the modules it depends on, in classpath order), then its module's shared source sets.
     */
    public fun resourceFolders(files: ProjectFiles, path: String, sourceSets: List<String> = emptyList()): List<String> {
        val all = LinkedHashSet<String>()
        val own = sourceSetOf(path)
        own?.let(all::add)
        all += sourceSets
        own?.let(::parentOf)?.let { src -> all += sharedSourceSets(files, src) }
        return all.flatMap { sourceSet -> RESOURCE_FOLDERS.map { "$sourceSet/$it" }.filter(files::isFolder) }
    }

    /** The shared source sets ([SHARED_SOURCE_SETS]) in the `src` folder [src] that exist. */
    public fun sharedSourceSets(files: ProjectFiles, src: String): List<String> =
        SHARED_SOURCE_SETS.map { "$src/$it" }.filter(files::isFolder)
}
