import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile

plugins {
    kotlin("multiplatform") version "1.7.10"
}

group = "net.sergeych"
version = "1.2"

repositories {
    mavenCentral()
    maven("https://maven.universablockchain.com/")
}

kotlin {
    val hostOs = System.getProperty("os.name")
    val isMingwX64 = hostOs.startsWith("Windows")
    val nativeTarget = when {
        hostOs == "Mac OS X" -> macosX64("native")
        hostOs == "Linux" -> linuxX64("native")
        isMingwX64 -> mingwX64("native")
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }

    nativeTarget.apply {
        binaries {
            executable {
                entryPoint = "main"
            }
        }
    }
    sourceSets {
        val nativeMain by getting {
            dependencies {
                implementation("com.github.ajalt.clikt:clikt:3.4.0")
                implementation("com.squareup.okio:okio:3.1.0")
                implementation("net.sergeych:mp_stools:1.2.2")
                implementation("net.sergeych:boss-serialization-mp:0.1.2-SNAPSHOT")
            }
        }
        val nativeTest by getting
    }
}

tasks.withType<KotlinNativeCompile>() {
    doFirst {
        val versionFile = file("src/nativeMain/kotlin/version.kt")
        versionFile.writeText(
            "val version = \"${rootProject.version}\""
        )
    }
}
