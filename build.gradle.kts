import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.ktor) apply false
}

val nodeJsVersion = "22.11.0"

allprojects {
    plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin> {
        extensions.configure<NodeJsEnvSpec>(NodeJsEnvSpec.EXTENSION_NAME) {
            version.set(nodeJsVersion)
        }
    }

    plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsPlugin> {
        extensions.configure<WasmNodeJsEnvSpec>(WasmNodeJsEnvSpec.EXTENSION_NAME) {
            version.set(nodeJsVersion)
        }
    }
}