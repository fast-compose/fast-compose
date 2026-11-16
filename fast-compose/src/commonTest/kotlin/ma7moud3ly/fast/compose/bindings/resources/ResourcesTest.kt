package ma7moud3ly.fast.compose.bindings.resources

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import ma7moud3ly.fast.compose.bindings.foundation.FoundationBindings
import ma7moud3ly.fast.compose.interpreter.StandIn
import ma7moud3ly.fast.compose.interpreter.Interpreter
import ma7moud3ly.fast.compose.interpreter.Registry
import ma7moud3ly.fast.compose.parser.KotlinParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ResourcesTest {

    @Test
    fun resourceReferences() {
        val program = KotlinParser.parse("fun test() = listOf(R.drawable.logo, Res.string.title)").program
        val registry = Registry().apply { install(ResourceBindings) }
        assertEquals(listOf(ResourceRef("drawable", "logo"), ResourceRef("string", "title")), Interpreter(program, registry).call("test"))
    }

    @Test
    fun vectorDrawablesAfterComments() {
        val vector = """
            <?xml version="1.0"?>
            <!--
              Licensed under the Apache License <2.0>
            -->
            <vector android:width="24dp"/>
        """.trimIndent()
        assertEquals("vector", xmlRoot(vector.encodeToByteArray()))
        assertEquals("svg", xmlRoot("<svg/>".encodeToByteArray()))
        assertNull(xmlRoot(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), '<'.code.toByte())))
    }

    @Test
    fun importedResourceClasses() {
        val code = "import app.R.string\nimport app.resources.Res.drawable\n\nfun test() = listOf(string.title, drawable.logo)"
        val program = KotlinParser.parse(code).program
        val registry = Registry().apply { install(ResourceBindings) }
        assertEquals(listOf(ResourceRef("string", "title"), ResourceRef("drawable", "logo")), Interpreter(program, registry).call("test"))
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
    fun pluralsAndStringArrays() {
        val strings = StringsXml.parse(
            """
            <resources>
                <plurals name="items">
                    <item quantity="one">%d item</item>
                    <item quantity="other">%d items</item>
                </plurals>
                <string-array name="days">
                    <item>Sat</item>
                    <item>Sun</item>
                </string-array>
                <string name="title">Title</string>
            </resources>
            """,
        )
        assertEquals("%d items", strings[StringsXml.pluralKey("items", "other")])
        assertEquals(listOf("Sat", "Sun"), listOf(0, 1).map { strings[StringsXml.arrayKey("days", it)] })
        assertEquals("Title", strings["title"])
        assertEquals(listOf("one", "other", "zero", "two", "few", "many"), listOf(
            pluralQuantity(1, null), pluralQuantity(0, "en"), pluralQuantity(0, "ar"),
            pluralQuantity(2, "ar"), pluralQuantity(5, "ar-EG"), pluralQuantity(11, "ar"),
        ))
    }

    @Test
    fun fontsFromResources() {
        val resources = MemoryResources().apply { put("src/main/res/font/inter.ttf", byteArrayOf(0, 1, 0, 0)) }
        val program = KotlinParser.parse("fun test() = listOf(Font(R.font.inter, FontWeight.Bold), Font(R.font.missing))").program
        val registry = Registry().apply {
            install(FoundationBindings)
            install(ResourceBindings)
        }
        val interpreter = Interpreter(program, registry).apply { hostValues[RESOURCES_HOST_VALUE] = resources }
        val (found, missing) = interpreter.call("test") as List<*>
        assertEquals(FontWeight.Bold, assertIs<Font>(found).weight)
        assertIs<StandIn>(missing)
    }

    @Test
    fun colorReferences() {
        val colors = ColorsXml.parse(
            """
            <resources>
                <!-- <color name="old">#000</color> -->
                <color name="purple">#FF6200EE</color>
                <color name="primary">@color/purple</color>
            </resources>
            """,
        )
        assertEquals(mapOf("purple" to "#FF6200EE", "primary" to "@color/purple"), colors)
        val vector = """<path android:fillColor="@color/primary" android:strokeColor="@android:color/white"
            android:tint="?attr/colorControlNormal" android:pathData="M0,0"/>"""
        assertEquals(
            """<path android:fillColor="#FF6200EE" android:strokeColor="#FFFFFFFF"
            android:tint="#FF000000" android:pathData="M0,0"/>""",
            resolveColorReferences(vector, colors),
        )
        assertEquals(Color(0xFF6200EE), parseColor("#6200EE"))
        assertEquals(Color(0xFFFF0000), parseColor("#F00"))
        assertNull(parseColor("@color/purple"))
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
