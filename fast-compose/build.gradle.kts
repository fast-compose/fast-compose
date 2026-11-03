import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.vanniktech.maven.publish)
}

description = "An instant runtime for Jetpack Compose code. No compiler, no build, no waiting."

val javaVersion = libs.versions.java.version.get()
val projectPackageName = libs.versions.project.packageName.get()

val generateBuildInfo = tasks.register<GenerateBuildInfo>("generateBuildInfo") {
    group = "build"
    description = "Writes FastComposeInfo.kt: the library's version and the versions it is built with."
    packageName.set(projectPackageName)
    versionName.set(libs.versions.project.versionName)
    versionCode.set(libs.versions.project.versionCode)
    kotlinVersion.set(libs.versions.kotlin.asProvider())
    composeVersion.set(libs.versions.composeMultiplatform)
    material3Version.set(libs.versions.material3)
    coroutinesVersion.set(libs.versions.kotlinx.coroutines)
    outputDir.set(layout.buildDirectory.dir("generated/buildInfo/kotlin"))
}

// The one published module. Its sources are grouped by package:
// parser, interpreter, bindings (foundation, material3, icons, resources, coil) and ui.
kotlin {
    android {
        namespace = "$projectPackageName.preview"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(javaVersion))
        }
    }

    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    js {
        browser()
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.configure { kotlin.srcDir(generateBuildInfo.map { it.outputDir }) }
        commonMain.dependencies {
            api(libs.compose.runtime)
            api(libs.compose.ui)
            implementation(libs.compose.foundation)
            implementation(libs.compose.animation)
            implementation(libs.compose.material3)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.compose.components.resources)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
        }
        // Network engines for Coil's images.
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            // Kotlin reflection for JarResolver: parameter names, default arguments and extension receivers.
            implementation(kotlin("reflect"))
            // Kotlin metadata for library composables: parameter names and defaults before the Compose compiler's changes.
            implementation(libs.kotlin.metadata.jvm)
        }
        jsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        // FileStorage uses java.io, available on both the JVM and Android.
        jvmMain {
            kotlin.srcDir("src/jvmAndAndroidMain/kotlin")
        }
        androidMain {
            kotlin.srcDir("src/jvmAndAndroidMain/kotlin")
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        // Graphics values such as paths and brushes need Skia's native library when tests run on the JVM.
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}

mavenPublishing {
    pom {
        name.set("Fast Compose")
    }
}

/** Generates `FastComposeInfo.kt` into `commonMain`, so every target knows the library's version. Reruns when a value changes. */
abstract class GenerateBuildInfo : DefaultTask() {

    @get:Input
    abstract val packageName: Property<String>

    @get:Input
    abstract val versionName: Property<String>

    @get:Input
    abstract val versionCode: Property<String>

    @get:Input
    abstract val kotlinVersion: Property<String>

    @get:Input
    abstract val composeVersion: Property<String>

    @get:Input
    abstract val material3Version: Property<String>

    @get:Input
    abstract val coroutinesVersion: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val pkg = packageName.get()
        val file = outputDir.get().asFile.resolve(pkg.replace('.', '/')).resolve("FastComposeInfo.kt")
        file.parentFile.mkdirs()
        file.writeText(
            """
            |package $pkg
            |
            |/** The Fast Compose library's version and the versions it is built with. Generated by `generateBuildInfo`. */
            |public object FastComposeInfo {
            |    /** This library's version, e.g. `0.2.0`. */
            |    public const val VERSION: String = "${versionName.get()}"
            |
            |    /** This library's version code, increasing with each release. */
            |    public const val VERSION_CODE: Int = ${versionCode.get()}
            |
            |    /** Kotlin version the library is built with. */
            |    public const val KOTLIN_VERSION: String = "${kotlinVersion.get()}"
            |
            |    /** Compose Multiplatform version previews run on. */
            |    public const val COMPOSE_VERSION: String = "${composeVersion.get()}"
            |
            |    /** Compose Multiplatform Material 3 version previews run on. */
            |    public const val MATERIAL3_VERSION: String = "${material3Version.get()}"
            |
            |    /** kotlinx.coroutines version the library is built with. */
            |    public const val COROUTINES_VERSION: String = "${coroutinesVersion.get()}"
            |}
            |""".trimMargin(),
        )
    }
}
