import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform") version "2.4.21"
    kotlin("plugin.serialization") version "2.4.21"
    kotlin("plugin.compose") version "2.4.21"
    id("org.jetbrains.compose") version "1.12.2"
}

val telar = "0.1.0-alpha14"
val composeVersion = "1.12.2"
val ktor = "3.6.0"

version = "0.1.0"

kotlin {
    jvmToolchain(17)

    // The desktop game, where Gemma runs on the player's Ollama.
    jvm()

    // The same game in a browser, for a phone too: ./gradlew wasmJsBrowserDistribution
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName = "scaryTruc"
        browser {
            commonWebpackConfig {
                outputFileName = "scaryTruc.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation("dev.deeptelar:telar-core:$telar")
            implementation("dev.deeptelar:telar-agent:$telar")
            implementation("dev.deeptelar:telar-openai:$telar")
            implementation("org.jetbrains.compose.runtime:runtime:$composeVersion")
            implementation("org.jetbrains.compose.foundation:foundation:$composeVersion")
            implementation("org.jetbrains.compose.ui:ui:$composeVersion")
            implementation("org.jetbrains.compose.components:components-resources:$composeVersion")
            implementation("io.ktor:ktor-client-core:$ktor")
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
            implementation("io.ktor:ktor-client-cio:$ktor")
            // Ktor logs through SLF4J; without a logger it prints a warning on every start.
            runtimeOnly("org.slf4j:slf4j-nop:2.0.17")
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation("dev.deeptelar:telar-test:$telar")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
        }
        wasmJsMain.dependencies {
            implementation("io.ktor:ktor-client-js:$ktor")
        }
    }
}

compose.resources {
    packageOfResClass = "dev.deeptelar.scarytruc.resources"
}

compose.desktop {
    application {
        mainClass = "dev.deeptelar.scarytruc.MainKt"
    }
}

// Off-screen pictures of the window, for the README: ./gradlew jvmTest --tests '*Screenshots*' -Pshots=DIR
tasks.named<Test>("jvmTest") {
    listOf("shots", "seed", "turns", "night", "balance", "show", "guide", "lang", "keyfile").forEach { name -> providers.gradleProperty(name).orNull?.let { systemProperty(name, it) } }
}
