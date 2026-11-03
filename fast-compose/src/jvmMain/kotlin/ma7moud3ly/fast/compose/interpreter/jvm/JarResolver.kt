package ma7moud3ly.fast.compose.interpreter.jvm

import ma7moud3ly.fast.compose.interpreter.ExternalResolver
import ma7moud3ly.fast.compose.interpreter.ExternalSymbol
import java.io.Closeable
import java.io.File
import java.util.jar.JarFile

/**
 * Runs real library code in previews on the JVM: an [ExternalResolver] over a project's library [jars] (or class
 * folders), e.g. as Gradle resolved them, for an IDE plugin or a command-line tool. Pass it to `Interpreter(externals = …)`
 * or provide it with `LocalExternalResolver`.
 *
 * Classes load in their own class loader, except [hostPackages], which come from [host] (by default Kotlin,
 * coroutines, Compose, the JDK and Fast Compose). It finds classes and objects, top-level functions and properties of a
 * package (in its `…Kt` file classes), and members of the values library code returns. Kotlin reflection supplies
 * parameter names, default arguments, extension receivers and varargs; plain Java reflection is the fallback.
 *
 * The class loader keeps the jars open until [close], which on Windows locks them: use jars that don't change, such as
 * Gradle's caches, or copies.
 */
public class JarResolver(
    jars: List<File>,
    host: ClassLoader = JarResolver::class.java.classLoader,
    hostPackages: List<String> = DEFAULT_HOST_PACKAGES,
) : ExternalResolver, Closeable {

    private val loader = ProjectClassLoader(jars.map { it.toURI().toURL() }, host, hostPackages)
    private val index = PackageIndex(jars)
    private val resolver = ReflectionResolver(loader, index::classesIn)

    override fun find(packageName: String, name: String): ExternalSymbol? = resolver.find(packageName, name)

    override fun member(receiver: Any, name: String): ExternalSymbol? = resolver.member(receiver, name)

    override fun close(): Unit = loader.close()

    public companion object {
        /** Packages that always load from the host, so the engine and library code share their classes. */
        public val DEFAULT_HOST_PACKAGES: List<String> = listOf(
            "java.", "javax.", "jdk.", "sun.", "com.sun.",
            "kotlin.", "kotlinx.coroutines.",
            "androidx.compose.", "org.jetbrains.compose.", "org.jetbrains.skia.", "org.jetbrains.skiko.",
            "ma7moud3ly.fast.compose.", "com.intellij.", "org.intellij.",
        )
    }
}

/** Top-level class names of each package in [jars] (jar files or class folders), read on first use. */
internal class PackageIndex(private val jars: List<File>) {
    private val classes: Map<String, List<String>> by lazy(::read)

    fun classesIn(packageName: String): List<String> = classes[packageName].orEmpty()

    private fun read(): Map<String, List<String>> {
        val map = HashMap<String, MutableList<String>>()
        for (jar in jars) {
            try {
                if (jar.isDirectory) {
                    jar.walkTopDown().filter { it.isFile && it.name.endsWith(".class") }.forEach { file ->
                        add(map, file.relativeTo(jar).invariantSeparatorsPath)
                    }
                } else if (jar.isFile) {
                    JarFile(jar).use { file -> file.entries().asSequence().forEach { add(map, it.name) } }
                }
            } catch (_: Exception) {
                // An unreadable jar contributes no top-level declarations; its classes can still load by name.
            }
        }
        return map
    }

    private fun add(map: HashMap<String, MutableList<String>>, path: String) {
        if (!path.endsWith(".class") || '$' in path || path.startsWith("META-INF/")) return
        val className = path.removeSuffix(".class").replace('/', '.')
        val packageName = className.substringBeforeLast('.', "")
        map.getOrPut(packageName) { mutableListOf() } += className.substringAfterLast('.')
    }
}
