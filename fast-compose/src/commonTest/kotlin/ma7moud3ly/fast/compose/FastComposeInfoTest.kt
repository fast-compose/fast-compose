package ma7moud3ly.fast.compose

import kotlin.test.Test
import kotlin.test.assertTrue

class FastComposeInfoTest {

    @Test
    fun versionsComeFromTheBuild() {
        val version = Regex("""\d+\.\d+\.\d+.*""")
        assertTrue(version.matches(FastComposeInfo.VERSION), FastComposeInfo.VERSION)
        assertTrue(FastComposeInfo.VERSION_CODE > 0)
        listOf(
            FastComposeInfo.KOTLIN_VERSION,
            FastComposeInfo.COMPOSE_VERSION,
            FastComposeInfo.MATERIAL3_VERSION,
            FastComposeInfo.COROUTINES_VERSION,
        ).forEach { assertTrue(version.matches(it), it) }
    }
}
