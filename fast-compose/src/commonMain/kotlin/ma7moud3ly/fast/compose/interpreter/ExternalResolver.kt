package ma7moud3ly.fast.compose.interpreter

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Library code a host can run for names that have no binding, e.g. a JVM host calling a project's dependency jars
 * through reflection. The interpreter asks it after bindings and before stand-ins: a name imported from a library, a
 * member of a value that library code returned, or an extension imported from a library. Hosts without one (the web,
 * iOS) keep stand-ins.
 *
 * Lookups run often, so implementations should cache them; the interpreter also caches [find] by name.
 */
public interface ExternalResolver {

    /** Top-level declaration [name] of package [packageName]: a class, object, function or property; null if none. */
    public fun find(packageName: String, name: String): ExternalSymbol?

    /** Member [name] of [receiver], a value that library code returned: a method or property; null if none. */
    public fun member(receiver: Any, name: String): ExternalSymbol?
}

/**
 * A declaration [ExternalResolver] found. Lambdas from interpreted code are passed as Kotlin functions (`() -> Any?`,
 * `(Any?) -> Any?`, …). Failures throw; the interpreter reports them and continues with a stand-in.
 */
public interface ExternalSymbol {

    /**
     * Its value: an object, a property (of [receiver] for a member or an extension property), or a class used as a
     * value, e.g. for its companion's members.
     */
    public fun read(receiver: Any?): Any?

    /** Calls it: a constructor, function, method or extension function (on [receiver]), with [positional] and [named] arguments. */
    public fun call(receiver: Any?, positional: List<Any?>, named: Map<String, Any?>): Any?
}

/** The [ExternalResolver] previews use; null runs library names as stand-ins. Hosts provide one around previews. */
public val LocalExternalResolver: ProvidableCompositionLocal<ExternalResolver?> = staticCompositionLocalOf { null }
