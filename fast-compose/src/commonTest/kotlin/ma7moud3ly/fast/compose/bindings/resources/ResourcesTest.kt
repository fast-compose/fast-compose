package ma7moud3ly.fast.compose.bindings.resources

import ma7moud3ly.fast.compose.interpreter.Interpreter
import ma7moud3ly.fast.compose.interpreter.Registry
import ma7moud3ly.fast.compose.parser.KotlinParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ResourcesTest {

    @Test
    fun resourceReferences() {
        val program = KotlinParser.parse("fun test() = listOf(R.drawable.logo, Res.string.title)").program
        val registry = Registry().apply { install(ResourceBindings) }
        assertEquals(listOf(ResourceRef("drawable", "logo"), ResourceRef("string", "title")), Interpreter(program, registry).call("test"))
    }

    @Test
    fun stringsXml() {
        val xml = """
            <resources>
                <!-- <string name="ignored">x</string> -->
                <string name="title">Hello, %1${'$'}s!</string>
                <string name="quote">\"Hi\" it\'s &amp; me</string>
                <string name="styled">Tap <b>here</b></string>
                <string name="empty"/>
            </resources>
        """.trimIndent()
        assertEquals(
            mapOf("title" to "Hello, %1\$s!", "quote" to "\"Hi\" it's & me", "styled" to "Tap here", "empty" to ""),
            StringsXml.parse(xml),
        )
    }

    @Test
    fun folderQualifiers() {
        assertEquals(ResourceFolder("values", language = "pt", region = "BR"), ResourceFolder.parse("values-pt-rBR"))
        assertEquals(ResourceFolder("values", language = "pt", region = "BR"), ResourceFolder.parse("values-b+pt+BR"))
        assertEquals(ResourceFolder("drawable", isDark = true, density = 3f), ResourceFolder.parse("drawable-night-xxhdpi"))
        assertEquals(ResourceFolder("drawable", hasOtherQualifiers = true), ResourceFolder.parse("drawable-land"))
    }

    @Test
    fun imageVariants() {
        val resources = MemoryResources()
        resources.put("src/main/res/drawable/logo.xml", "light")
        resources.put("drawable-night/logo.xml", "dark")
        resources.put("drawable-mdpi/photo.png", "mdpi")
        resources.put("drawable-xxhdpi/photo.png", "xxhdpi")
        resources.put("drawable-land/photo.png", "land")
        fun read(name: String, environment: ResourceEnvironment) = resources.bytes("drawable", name, environment)?.decodeToString()
        assertEquals("light", read("logo", ResourceEnvironment()))
        assertEquals("dark", read("logo", ResourceEnvironment(isDark = true)))
        assertEquals("xxhdpi", read("photo", ResourceEnvironment(density = 2.75f)))
        assertEquals("mdpi", read("photo", ResourceEnvironment(density = 1f)))
        assertNull(read("missing", ResourceEnvironment()))
    }

    @Test
    fun stringVariants() {
        val resources = MemoryResources()
        resources.put("composeResources/values/strings.xml", """<resources><string name="hi">Hi</string><string name="bye">Bye</string></resources>""")
        resources.put("values-pt/strings.xml", """<resources><string name="hi">Oi</string></resources>""")
        assertEquals("Oi", resources.string("hi", ResourceEnvironment(locale = "pt-BR")))
        assertEquals("Bye", resources.string("bye", ResourceEnvironment(locale = "pt-BR")))
        assertEquals("Hi", resources.string("hi", ResourceEnvironment(locale = "ar")))
        assertNull(resources.string("missing", ResourceEnvironment()))

        resources.put("values/strings.xml", """<resources><string name="hi">Hello</string></resources>""")
        assertEquals("Hello", resources.string("hi", ResourceEnvironment()))
        resources.remove("values-pt/strings.xml")
        assertEquals("Hello", resources.string("hi", ResourceEnvironment(locale = "pt")))
    }

    @Test
    fun formatting() {
        assertEquals("Hi Ana, 3 new", formatResource("Hi %s, %d new", listOf("Ana", 3)))
        assertEquals("B then A", formatResource("%2\$s then %1\$s", listOf("A", "B")))
        assertEquals("3.14 is 100%", formatResource("%.2f is 100%%", listOf(3.14159)))
    }
}
