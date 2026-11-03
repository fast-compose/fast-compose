package ma7moud3ly.fast.compose.interpreter.jvm

import java.io.File

/** Library jars for [JarResolver] from a project's resolved dependencies, as Gradle caches them. */
public object GradleJars {

    /**
     * Libraries a host provides itself, whose classes always come from the host (see
     * [JarResolver.DEFAULT_HOST_PACKAGES]), as parts of their jar paths.
     */
    public val HOST_JARS: List<String> = listOf(
        "/kotlin-stdlib", "/kotlinx-coroutines-", "/androidx.compose.", "/org.jetbrains.compose.", "/skiko",
        "/kotlin-reflect",
    )

    /**
     * The jars of [classpath] that hold JVM classes, in order: KMP metadata artifacts (`nemo-editor-metadata.jar`,
     * klibs) are replaced by their JVM variants ([jvmVariant]); host libraries ([HOST_JARS]) and libraries built inside
     * the project (`build/` folders) are left out.
     */
    public fun jvmJars(classpath: List<File>): List<File> {
        val usable = classpath.filter { file ->
            val path = file.invariantSeparatorsPath
            file.exists() && "/build/" !in path && HOST_JARS.none { it in path }
        }
        return (usable.filterNot(::isMetadata) + usable.filter(::isMetadata).mapNotNull(::jvmVariant)).distinct()
    }

    /** True for KMP metadata artifacts, which hold no JVM classes. */
    public fun isMetadata(file: File): Boolean = file.name.endsWith("-metadata.jar") || file.extension == "klib"

    /**
     * The JVM jar of a KMP library, next to its metadata jar in Gradle's cache:
     * `<group>/<artifact>/<version>/<hash>/<artifact>-metadata.jar` → a jar in `<group>/<artifact>-jvm/<version>/`
     * (or `-desktop`); null when it isn't downloaded.
     */
    public fun jvmVariant(metadata: File): File? {
        val version = metadata.parentFile?.parentFile ?: return null
        val artifact = version.parentFile ?: return null
        val group = artifact.parentFile ?: return null
        for (suffix in listOf("-jvm", "-desktop")) {
            val folder = File(group, "${artifact.name}$suffix/${version.name}")
            if (!folder.isDirectory) continue
            folder.walkTopDown().maxDepth(2)
                .firstOrNull { it.isFile && it.extension == "jar" && !it.name.endsWith("-sources.jar") && !it.name.endsWith("-javadoc.jar") }
                ?.let { return it }
        }
        return null
    }
}
