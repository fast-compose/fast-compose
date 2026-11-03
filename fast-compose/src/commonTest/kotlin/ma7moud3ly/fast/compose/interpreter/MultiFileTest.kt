package ma7moud3ly.fast.compose.interpreter

import ma7moud3ly.fast.compose.parser.KotlinParser
import ma7moud3ly.fast.compose.parser.ast.Program
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MultiFileTest {

    /** Project files by path. */
    private val files = mapOf(
        "ui/Cards.kt" to """
            package app.ui

            import app.model.User

            fun card(user: User) = "[" + label(user.name) + "]"

            fun label(text: String) = text.uppercase()

            private fun secret() = "ui"
        """.trimIndent(),
        "ui/Theme.kt" to """
            package app.ui

            val accent = "blue"

            fun themed() = accent + "/" + Style.BOLD.name

            enum class Style { PLAIN, BOLD }
        """.trimIndent(),
        "model/User.kt" to """
            package app.model

            data class User(val name: String) {
                fun greeting() = "Hi " + name
            }

            object Users {
                val default = User("guest")
            }
        """.trimIndent(),
        "other/Label.kt" to """
            package app.other

            fun label(text: String) = "other " + text
        """.trimIndent(),
        "model/Settings.kt" to """
            package app.model

            object Settings {
                val isDark = true
                fun greet(name: String) = "hi " + name
            }

            enum class Mode { LIGHT, DARK }
        """.trimIndent(),
        "ui/Extras.kt" to """
            package app.ui

            import app.gone.Ghost

            fun used() = "used"

            fun unused() = Ghost()

            fun String.shout() = uppercase() + "!"
        """.trimIndent(),
    )

    private val parsed = files.mapValues { (path, text) -> KotlinParser.parse(text, path).program }

    /** Finds the file of [packageName] that declares [name]. */
    private val provider = SourceProvider { packageName, name ->
        files.entries.firstOrNull { (path, text) ->
            parsed.getValue(path).packageName == packageName &&
                Regex("""(fun|class|object|val|var) (\w+\.)?$name\b(?!\.)""").containsMatchIn(text)
        }?.let { SourceFile(it.key, it.value) }
    }

    private fun program(code: String): Program = KotlinParser.parse(code.trimIndent(), "Main.kt").program

    private suspend fun run(code: String): Any? {
        val main = program(code)
        val loaded = SourceLoader(provider).load(main)
        return Interpreter(main, Registry(), dependencies = loaded.dependencies).call("test")
    }

    @Test
    fun importedFunctionsUseTheirOwnFileDeclarations() = runTest {
        val code = """
            package app

            import app.ui.card
            import app.model.User

            fun test() = card(User("ana"))
        """
        assertEquals("[ANA]", run(code))
    }

    @Test
    fun samePackageAndStarImports() = runTest {
        val code = """
            package app.ui

            import app.model.*

            fun test() = themed() + " " + Users.default.greeting() + " " + label("x")
        """
        assertEquals("blue/BOLD Hi guest X", run(code))
    }

    @Test
    fun aliasesAndExplicitImportsWin() = runTest {
        val code = """
            package app.ui

            import app.other.label
            import app.ui.label as loud

            fun test() = label("a") + "|" + loud("b")
        """
        assertEquals("other a|B", run(code))
    }

    @Test
    fun onlyReachedFilesAreLoaded() = runTest {
        val main = program(
            """
            package app

            import app.ui.label

            fun test() = label("a")
            """,
        )
        val loaded = SourceLoader(provider).load(main)
        // Only `label` is reached in Cards.kt, so the User import of its `card` function is not followed.
        assertEquals(listOf("ui/Cards.kt"), loaded.dependencies.map { it.path })
    }

    @Test
    fun extensionsOfTheSamePackageNeedNoImport() = runTest {
        val code = """
            package app.ui

            fun test() = "hi".shout()
        """
        assertEquals("HI!", run(code))
    }

    @Test
    fun privateDeclarationsStayInTheirFile() = runTest {
        val code = """
            package app.ui

            fun test() = secret()
        """
        // The private function of Cards.kt is not visible here, so the call gives a stand-in, not its "ui".
        assertTrue(run(code) is StandIn)
    }

    @Test
    fun missingImportsAreReported() = runTest {
        val main = program(
            """
            package app

            import app.ui.Missing
            import androidx.compose.material3.Text

            fun test() = Missing() + Text()
            """,
        )
        val loaded = SourceLoader(provider).load(main)
        val missing = loaded.missing.map { it.toString() }
        assertEquals(listOf("Unresolved reference 'Missing' (imported from app.ui)"), missing)
        assertEquals("Main.kt", loaded.missing.single().file)
    }

    @Test
    fun onlyReachedDeclarationsReportMissingNames() = runTest {
        val main = program(
            """
            package app

            import app.ui.used

            fun test() = used()
            """,
        )
        assertEquals(emptyList(), SourceLoader(provider).load(main).missing)

        val reachingGhost = program(
            """
            package app

            import app.ui.unused

            fun test() = unused()
            """,
        )
        val missing = SourceLoader(provider).load(reachingGhost).missing.single()
        assertEquals("Ghost", missing.name)
        assertEquals("ui/Extras.kt", missing.file)
    }

    @Test
    fun resourceClassesAreNeverLookedUp() = runTest {
        val main = program(
            """
            package app

            import app.resources.Res
            import app.R

            fun test() = Res.string.title + R.string.name
            """,
        )
        assertEquals(emptyList(), SourceLoader(provider).load(main).missing)
    }

    @Test
    fun libraryPackagesFromTheProviderAreNotLookedUp() = runTest {
        val lookedUp = mutableListOf<String>()
        val projectOnly = object : SourceProvider {
            override suspend fun find(packageName: String, name: String): SourceFile? {
                lookedUp += "$packageName.$name"
                return provider.find(packageName, name)
            }

            override suspend fun isProjectPackage(packageName: String) = packageName.startsWith("app")
        }
        val main = program(
            """
            package app

            import io.ktor.util.date.getTimeMillis
            import app.ui.label

            fun test() = label("a") + getTimeMillis()
            """,
        )
        val loaded = SourceLoader(projectOnly).load(main)
        assertEquals(emptyList(), loaded.missing)
        assertTrue(lookedUp.none { it.startsWith("io.ktor") }, lookedUp.toString())
    }

    @Test
    fun importedMembersOfObjectsAndEnums() = runTest {
        val code = """
            package app

            import app.model.Settings.isDark
            import app.model.Settings.greet
            import app.model.Mode.DARK

            fun test() = "" + isDark + "|" + greet("a") + "|" + DARK.name
        """
        assertEquals("true|hi a|DARK", run(code))
    }

    @Test
    fun importedExtensionsUsedAfterADotAreLoaded() = runTest {
        val code = """
            package app

            import app.ui.shout

            fun test() = "hi".shout()
        """
        assertEquals("HI!", run(code))
    }

    @Test
    fun withoutDependenciesImportsAreIgnored() {
        val main = program(
            """
            package app.ui

            fun label(text: String) = "own " + text

            fun test() = label("a")
            """,
        )
        assertEquals("own a", Interpreter(main, Registry()).call("test"))
    }

    @Test
    fun errorsNameTheirFile() = runTest {
        val errors = mutableListOf<RuntimeError>()
        val main = program(
            """
            package app

            import app.ui.card

            fun test() = card(42)
            """,
        )
        val loaded = SourceLoader(provider).load(main)
        val interpreter = Interpreter(main, Registry(), onError = { errors += it }, dependencies = loaded.dependencies)
        interpreter.call("test")
        // `user.name` on an Int is a missing member, reported where it is used: line 5 of Cards.kt.
        assertTrue(errors.first().toString().startsWith("Cards.kt:5:"), errors.toString())
    }

    @Test
    fun libraryDeclarationsFromSources() = runTest {
        val library = SourceFile(
            "nemo-sources.jar!/io/nemo/model/EditorTheme.kt",
            "package io.nemo.model\n\ndata class EditorTheme(val background: Int = 0, val dark: Boolean = false)",
        )
        val sources = object : SourceProvider {
            override suspend fun find(packageName: String, name: String): SourceFile? = null
            override suspend fun isProjectPackage(packageName: String) = packageName.startsWith("app")
            override suspend fun findLibrary(packageName: String, name: String) =
                library.takeIf { packageName == "io.nemo.model" && name == "EditorTheme" }
        }
        val main = program(
            """
            package app

            import io.nemo.model.EditorTheme
            import io.nemo.ui.NemoCodeEditor

            fun test() = EditorTheme(background = 3).copy(dark = true).toString()
            """,
        )
        val loaded = SourceLoader(sources).load(main)
        assertEquals(emptyList(), loaded.missing)
        assertEquals("EditorTheme(background=3, dark=true)", Interpreter(main, Registry(), dependencies = loaded.dependencies).call("test"))
    }

    @Test
    fun actualFunctionsUseTheDefaultsOfTheirExpect() = runTest {
        val expect = SourceFile(
            "common/Back.kt",
            "package app.ui\n\nexpect fun back(enabled: Boolean = true, label: String = \"back\"): String",
        )
        val actual = SourceFile(
            "android/Back.android.kt",
            "package app.ui\n\nactual fun back(enabled: Boolean, label: String): String = label + \" \" + enabled",
        )
        val main = program(
            """
            package app

            import app.ui.back

            fun test() = back(label = "up")
            """,
        )

        val withExpect = object : SourceProvider {
            override suspend fun find(packageName: String, name: String) = actual
            override suspend fun findExpect(packageName: String, name: String) = expect
        }
        val loaded = SourceLoader(withExpect).load(main)
        assertEquals("up true", Interpreter(main, Registry(), dependencies = loaded.dependencies).call("test"))

        // Without the expect, missing arguments get empty values with a warning.
        val errors = mutableListOf<RuntimeError>()
        val alone = SourceLoader(SourceProvider { _, _ -> actual }).load(main)
        val result = Interpreter(main, Registry(), dependencies = alone.dependencies, onError = { errors += it }).call("test")
        assertEquals("up false", result)
        assertTrue(errors.single().isWarning, errors.toString())
    }
}
