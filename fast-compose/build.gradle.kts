import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.vanniktech.maven.publish)
}

description = "Instant Jetpack Compose previews without compilation: renders Kotlin Compose code at runtime with FastComposePreview and BasicComposePreview"

val javaVersion = libs.versions.java.version.get()
val projectPackageName = libs.versions.project.packageName.get()

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
