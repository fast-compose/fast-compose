package ma7moud3ly.fast.compose.project

import java.io.File

/**
 * [ProjectFiles] on the local file system, e.g. for a CLI. Paths are absolute, with `/` separators; versions combine the
 * last-modified time and size.
 */
public object DiskFiles : ProjectFiles {

    override fun list(path: String): List<FileEntry> =
        File(path).listFiles().orEmpty().map { FileEntry(it.name, it.isDirectory, if (it.isFile) it.length() else 0L) }

    override fun isFolder(path: String): Boolean = File(path).isDirectory

    override fun read(path: String): String? = File(path).takeIf { it.isFile }?.let { runCatching { it.readText() }.getOrNull() }

    override fun version(path: String): Long = File(path).let { it.lastModified() * 31 + it.length() }

    /** [file]'s path as the other functions take it: absolute, with `/` separators. */
    public fun pathOf(file: File): String = file.absoluteFile.invariantSeparatorsPath
}
