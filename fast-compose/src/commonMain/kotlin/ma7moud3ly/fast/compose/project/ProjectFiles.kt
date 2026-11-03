package ma7moud3ly.fast.compose.project

/**
 * Read access to a project's files by `/`-separated paths, for hosts that preview a project: an IDE over its own file
 * tree (with unsaved editor text), or a CLI over the disk ([DiskFiles] on the JVM). Calls may block.
 */
public interface ProjectFiles {

    /** Entries of folder [path]; empty when it is missing or not a folder. */
    public fun list(path: String): List<FileEntry>

    /** True when [path] is a folder. */
    public fun isFolder(path: String): Boolean

    /** Text of file [path], or null when it can't be read. */
    public fun read(path: String): String?

    /** A number that changes whenever [path]'s text does, e.g. its modification time; for caches. */
    public fun version(path: String): Long
}

/** An entry of a folder: its [name], whether it [isFolder], and its [size] in bytes (0 for folders). */
public class FileEntry(public val name: String, public val isFolder: Boolean, public val size: Long)

/** The folder holding [path], or null at the top. */
internal fun parentOf(path: String): String? = path.substringBeforeLast('/', "").ifEmpty { null }

/** [name] inside folder [path]; a jar's root (`a.jar!/`) ends with `/` already. */
internal fun childOf(path: String, name: String): String = path.trimEnd('/') + "/" + name

/** The last segment of [path]. */
internal fun nameOf(path: String): String = path.substringAfterLast('/')
