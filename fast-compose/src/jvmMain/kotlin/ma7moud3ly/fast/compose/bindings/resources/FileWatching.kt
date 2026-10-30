package ma7moud3ly.fast.compose.bindings.resources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import java.nio.file.WatchKey
import java.util.concurrent.TimeUnit

/**
 * Calls [onChange] after files under [FileStorage.root] are added, changed or deleted, until the calling coroutine is
 * cancelled. Changes that arrive together are reported once. Run it in a `LaunchedEffect`, e.g.
 * `storage.watch { resources.refresh() }`.
 */
public suspend fun FileStorage.watch(onChange: suspend () -> Unit) {
    FileSystems.getDefault().newWatchService().use { watcher ->
        val folders = mutableMapOf<WatchKey, Path>()
        fun register(dir: File) {
            dir.walkTopDown().filter { it.isDirectory }.forEach {
                folders[it.toPath().register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY)] = it.toPath()
            }
        }
        withContext(Dispatchers.IO) { register(root) }
        while (currentCoroutineContext().isActive) {
            val first = withContext(Dispatchers.IO) { watcher.poll(POLL_MILLIS, TimeUnit.MILLISECONDS) } ?: continue
            var key: WatchKey? = first
            while (key != null) {
                val dir = folders[key]
                for (event in key.pollEvents()) {
                    val file = dir?.resolve(event.context() as? Path ?: continue)?.toFile() ?: continue
                    if (event.kind() == ENTRY_CREATE && file.isDirectory) withContext(Dispatchers.IO) { register(file) }
                }
                if (!key.reset()) folders.remove(key)
                key = withContext(Dispatchers.IO) { watcher.poll(SETTLE_MILLIS, TimeUnit.MILLISECONDS) }
            }
            onChange()
        }
    }
}

private const val POLL_MILLIS = 300L

/** Waits this long for more changes after one arrives, so an editor saving several files triggers one refresh. */
private const val SETTLE_MILLIS = 100L
