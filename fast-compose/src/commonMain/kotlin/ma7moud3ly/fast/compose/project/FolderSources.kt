package ma7moud3ly.fast.compose.project

import ma7moud3ly.fast.compose.interpreter.SourceFile
import ma7moud3ly.fast.compose.interpreter.SourceProvider

/**
 * A [SourceProvider] for a preview of the file at [from], over [roots] (by default [KotlinSources.folderRoots]: its
 * own source root and its module's shared source sets), for hosts without a project model such as a CLI. Packages
 * with a folder under [roots] are the project's; library declarations come from [libraryRoots], e.g. extracted
 * sources jars.
 */
public class FolderSources(
    private val sources: KotlinSources,
    private val from: String,
    private val roots: List<String> = sources.folderRoots(from),
    private val libraryRoots: List<String> = emptyList(),
) : SourceProvider {

    override suspend fun find(packageName: String, name: String): SourceFile? = sources.find(packageName, name, roots, from)

    override suspend fun isProjectPackage(packageName: String): Boolean =
        packageName.isEmpty() || sources.hasPackage(packageName, roots)

    override suspend fun findExpect(packageName: String, name: String): SourceFile? =
        sources.findExpect(packageName, name, roots, from)

    override suspend fun findLibrary(packageName: String, name: String): SourceFile? =
        sources.findLibrary(packageName, name, libraryRoots)
}
