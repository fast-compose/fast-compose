import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.vanniktech.maven.publish) apply false
}

val publishGroup = "io.github.fast-compose"
val publishVersion = libs.versions.project.versionName.get()
val repositoryUrl = "https://github.com/fast-compose/fast-compose"

/**
 * Maven publishing shared by every module that applies the maven-publish
 * plugin. The artifact id is the module name, and the POM description is the
 * module's own `description`.
 */
subprojects {
    val module = this
    plugins.withId("com.vanniktech.maven.publish") {
        // Published modules must give every declaration an explicit visibility and return type, so nothing becomes
        // public API by accident.
        plugins.withId("org.jetbrains.kotlin.multiplatform") {
            extensions.configure<KotlinMultiplatformExtension> { explicitApi() }
        }
        extensions.configure<MavenPublishBaseExtension> {
            coordinates(publishGroup, module.name, publishVersion)

            pom {
                // "fast-compose" becomes "Fast Compose"
                name.set(module.name.split('-').joinToString(" ") { word ->
                    word.replaceFirstChar { it.uppercase() }
                })
                description.set(provider { module.description })
                url.set(repositoryUrl)
                inceptionYear.set("2026")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("repo")
                    }
                }

                developers {
                    developer {
                        id.set("ma7moud3ly")
                        name.set("Mahmoud Aly")
                        email.set("engma7moud3ly@gmail.com")
                        url.set("https://github.com/Ma7moud3ly")
                    }
                }

                scm {
                    url.set(repositoryUrl)
                    connection.set("scm:git:git://github.com/fast-compose/fast-compose.git")
                    developerConnection.set("scm:git:ssh://git@github.com/fast-compose/fast-compose.git")
                }
            }

            publishToMavenCentral(automaticRelease = false)
            signAllPublications()
        }
    }
}
