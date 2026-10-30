package ma7moud3ly.fast.compose.bindings.resources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A `res/` or `composeResources/` folder on the local file system, for [StorageResources]. Only folders previews use
 * are listed; hidden files and files over 20 MB are skipped. Versions combine the last-modified time and size.
 */
public class FileStorage(public val root: File) : ResourceStorage {

    override suspend fun list(): List<StoredFile> = withContext(Dispatchers.IO) {
        root.walkTopDown()
            .onEnter { it == root || it.parentFile == root && ResourceSelection.isPreviewFolder(it.name) }
            .filter { it.isFile && !it.name.startsWith(".") && it.length() <= MAX_FILE_BYTES }
            .map { StoredFile(it.relativeTo(root).invariantSeparatorsPath, it.lastModified() * 31 + it.length()) }
            .toList()
    }

    override suspend fun read(path: String): ByteArray? = withContext(Dispatchers.IO) {
        root.resolve(path).takeIf { it.isFile }?.readBytes()
    }

    private companion object {
        const val MAX_FILE_BYTES = 20L * 1024 * 1024
    }
}
