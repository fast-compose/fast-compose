package ma7moud3ly.fast.compose.bindingcheck

import ma7moud3ly.fast.compose.bindings.coil.CoilBindings
import ma7moud3ly.fast.compose.bindings.foundation.FoundationBindings
import ma7moud3ly.fast.compose.bindings.icons.IconBindings
import ma7moud3ly.fast.compose.bindings.material3.Material3Bindings
import ma7moud3ly.fast.compose.bindings.resources.ResourceBindings
import ma7moud3ly.fast.compose.interpreter.Registry
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BindingCheckTest {

    @Test
    fun bindingsMatchComposeApi() {
        val api = ApiFiles.load(File("api"))
        val registry = Registry().apply { install(FoundationBindings, Material3Bindings, IconBindings, ResourceBindings, CoilBindings) }
        val report = BindingCheck(api, lines("allowlist.txt"), lines("excluded.txt")).run(registry.bindings())
        File("build/reports/binding-check.txt").apply { parentFile.mkdirs() }.writeText(report.text())
        println(report.summary())
        assertTrue(report.errors.isEmpty(), report.errors.joinToString("\n", "Bindings differ from Compose:\n"))
    }

    @Test
    fun bindingsReadAllParameters() {
        val sources = File("../fast-compose/src/commonMain/kotlin/ma7moud3ly/fast/compose/bindings").walk()
            .filter { file -> file.extension == "kt" }.toList()
        val allowed = lines("unused-allowlist.txt")
        val unused = UnusedParameters.find(sources).filter { it.key !in allowed }
        assertTrue(unused.isEmpty(), unused.joinToString("\n", "Parameters declared but never read:\n"))
    }

    /** Non-empty lines of [path] without `#` comments. */
    private fun lines(path: String): Set<String> =
        File(path).readLines().map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }.toSet()

    @Test
    fun parsesApiLines() {
        val text = """
            package androidx.compose.foundation.layout {
              public final class PaddingKt {
                method @KotlinOnly @androidx.compose.runtime.Stable public static androidx.compose.ui.Modifier padding(androidx.compose.ui.Modifier, optional androidx.compose.ui.unit.Dp horizontal, optional androidx.compose.ui.unit.Dp vertical);
                method @BytecodeOnly public static androidx.compose.ui.Modifier padding-YgX7TsA(androidx.compose.ui.Modifier, float, float);
              }
              public interface RowScope {
                method @androidx.compose.runtime.Stable public androidx.compose.ui.Modifier weight(androidx.compose.ui.Modifier, @FloatRange(from=0.0, fromInclusive=false) float weight, optional boolean fill);
              }
            }
        """.trimIndent()
        val functions = ApiFiles.parse("foundation-layout", text)
        assertEquals(listOf("padding(horizontal, vertical)", "weight(weight, fill)"), functions.map { it.toString() })
        assertEquals("Modifier", functions[0].receiver)
        assertEquals(listOf(true, true), functions[0].params.map { it.isOptional })
        assertEquals("RowScope", functions[1].owner)
    }
}
