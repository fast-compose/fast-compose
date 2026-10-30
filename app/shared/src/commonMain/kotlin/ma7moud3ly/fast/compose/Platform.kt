package ma7moud3ly.fast.compose

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

val Platform.isAndroid: Boolean get() = name.startsWith("Android")
