package ma7moud3ly.fast.compose.interpreter.jvm

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class GradleJarsTest {

    @Test
    fun metadataJarsAreReplacedByTheirJvmVariants() {
        val cache = createTempDirectory("files-2.1").toFile()
        try {
            fun jar(path: String) = File(cache, path).apply { parentFile.mkdirs(); writeText("") }
            val metadata = jar("io.lib/editor/1.0/aaa/editor-metadata.jar")
            val jvm = jar("io.lib/editor-jvm/1.0/bbb/editor-jvm-1.0.jar")
            jar("io.lib/editor-jvm/1.0/ccc/editor-jvm-1.0-sources.jar")
            val plain = jar("io.other/util/2.0/ddd/util-2.0.jar")
            val host = jar("org.jetbrains.kotlin/kotlin-stdlib/2.4/eee/kotlin-stdlib-2.4.jar")
            val missing = File(cache, "io.gone/gone/1.0/fff/gone-metadata.jar")

            assertEquals(listOf(plain, jvm), GradleJars.jvmJars(listOf(metadata, plain, host, missing)))
        } finally {
            cache.deleteRecursively()
        }
    }
}
