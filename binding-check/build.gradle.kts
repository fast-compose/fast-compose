plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

description = "Checks binding signatures against the AndroidX Compose API files"

kotlin {
    jvm()

    sourceSets {
        jvmMain.dependencies {
            implementation(projects.fastCompose)
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
