plugins { alias(libs.plugins.android.test) }

android {
    namespace = "client.wexbooks.benchmarktests"
    compileSdk = 37
    targetProjectPath = ":app"
    defaultConfig {
        minSdk = 28
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["androidx.benchmark.output.enable"] = "true"
    }
    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
    experimentalProperties["android.experimental.self-instrumenting"] = true
}
androidComponents { beforeVariants(selector().all()) { it.enable = it.buildType == "benchmark" } }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.benchmark.macro)
    implementation(libs.androidx.junit)
    implementation(libs.test.runner)
    implementation(libs.uiautomator)
}
