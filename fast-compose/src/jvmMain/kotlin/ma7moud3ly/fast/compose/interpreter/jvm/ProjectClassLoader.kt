package ma7moud3ly.fast.compose.interpreter.jvm

import java.net.URL
import java.net.URLClassLoader

/**
 * Loads a project's library classes from [urls], except [hostPackages], which always come from [host]: Kotlin,
 * coroutines, Compose, the JDK and the engine, so values passed between library code and the engine's bindings have
 * the same classes (otherwise they fail with `ClassCastException`).
 */
internal class ProjectClassLoader(urls: List<URL>, private val host: ClassLoader, private val hostPackages: List<String>) :
    URLClassLoader(urls.toTypedArray(), null) {

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        if (hostPackages.any { name.startsWith(it) }) return host.loadClass(name)
        synchronized(getClassLoadingLock(name)) {
            findLoadedClass(name)?.let { return it }
            val loaded = try {
                findClass(name)
            } catch (_: ClassNotFoundException) {
                host.loadClass(name)
            }
            if (resolve) resolveClass(loaded)
            return loaded
        }
    }

    override fun getResource(name: String): URL? = findResource(name) ?: host.getResource(name)
}
