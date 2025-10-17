import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoReport
import java.nio.file.Files
import java.time.Instant

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.dagger.hilt.android")
    id("jacoco")
}

val localProperties = gradleLocalProperties(rootDir, providers)
val realtimeBaseUrl =
    (localProperties.getProperty("realtime.apiBaseUrl") ?: "https://api.realtime-proxy.example/")
        .replace("\"", "\\\"")

android {
    namespace = "com.example.translatorapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.translatorapp"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "REALTIME_BASE_URL", "\"$realtimeBaseUrl\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("staging") {
            initWith(getByName("debug"))
            isDebuggable = true
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kapt {
    correctErrorTypes = true
}

configurations.all {
    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
}

jacoco {
    toolVersion = "0.8.11"
}

tasks.withType<Test>().configureEach {
    extensions.configure(JacocoTaskExtension::class.java) {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
    testLogging {
        events("passed", "skipped", "failed")
    }
}

androidComponents {
    onVariants { variant ->
        val variantName = variant.name
        val capitalizedVariant = variantName.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        val unitTestTaskName = "test${capitalizedVariant}UnitTest"
        val jacocoTaskName = "jacoco${capitalizedVariant}Report"
        tasks.register<JacocoReport>(jacocoTaskName) {
            group = "verification"
            description = "Generates Jacoco coverage for the $variantName unit tests."
            dependsOn(unitTestTaskName)

            val javaClasses = layout.buildDirectory.dir("intermediates/javac/${variantName}/classes")
            val kotlinClasses = layout.buildDirectory.dir("tmp/kotlin-classes/${variantName}")

            classDirectories.setFrom(
                files(
                    javaClasses.map { directory ->
                        project.fileTree(directory) {
                            exclude("**/R.class", "**/R$*.class", "**/BuildConfig.class", "**/Manifest*.*", "**/*Test*.*")
                        }
                    },
                    kotlinClasses.map { directory ->
                        project.fileTree(directory) {
                            exclude("**/R.class", "**/R$*.class", "**/BuildConfig.*", "**/Manifest*.*", "**/*Test*.*")
                        }
                    }
                )
            )
            sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))
            executionData.setFrom(
                files(
                    layout.buildDirectory.file("jacoco/${unitTestTaskName}.exec"),
                    layout.buildDirectory.file("outputs/unit_test_code_coverage/${variantName}UnitTest/${unitTestTaskName}.exec")
                )
            )
            reports {
                xml.required.set(true)
                html.required.set(true)
            }
        }
    }
}

val collectTestMetrics by tasks.registering {
    group = "verification"
    description = "Runs unit tests, generates coverage, and persists summary metrics."
    val variant = "debug"
    val capitalizedVariant = variant.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    dependsOn(
        tasks.named("test${capitalizedVariant}UnitTest"),
        tasks.named("jacoco${capitalizedVariant}Report")
    )
    doLast {
        val resultsDir = layout.buildDirectory.dir("test-results/test${capitalizedVariant}UnitTest").get().asFile.toPath()
        val coverageFile = layout.buildDirectory.file("reports/jacoco/jacoco${capitalizedVariant}Report/jacoco${capitalizedVariant}Report.xml").get().asFile.toPath()
        var totalTests = 0
        var totalFailures = 0
        var totalErrors = 0
        var totalSkipped = 0
        if (Files.exists(resultsDir)) {
            Files.list(resultsDir).use { stream ->
                stream.filter { path -> Files.isRegularFile(path) && path.fileName.toString().endsWith(".xml") }
                    .forEach { path ->
                        val content = Files.readString(path)
                        Regex("""tests="(\d+)" failures="(\d+)" errors="(\d+)" skipped="(\d+)"""")
                            .findAll(content)
                            .forEach { match ->
                                totalTests += match.groupValues[1].toInt()
                                totalFailures += match.groupValues[2].toInt()
                                totalErrors += match.groupValues[3].toInt()
                                totalSkipped += match.groupValues[4].toInt()
                            }
                    }
            }
        }
        var lineCoverage = 0.0
        if (Files.exists(coverageFile)) {
            val content = Files.readString(coverageFile)
            Regex("""<counter type="LINE" missed="(\d+)" covered="(\d+)"""")
                .find(content)
                ?.let { counter ->
                    val missed = counter.groupValues[1].toDouble()
                    val covered = counter.groupValues[2].toDouble()
                    val total = missed + covered
                    if (total > 0) {
                        lineCoverage = covered / total * 100.0
                    }
                }
        }
        val metricsDir = layout.buildDirectory.dir("metrics").get().asFile
        metricsDir.mkdirs()
        val metricsFile = metricsDir.resolve("test-metrics.json")
        val payload = """
            {
              "timestamp": "${Instant.now()}",
              "variant": "$variant",
              "totalTests": $totalTests,
              "failures": $totalFailures,
              "errors": $totalErrors,
              "skipped": $totalSkipped,
              "lineCoveragePercent": ${"%.2f".format(lineCoverage)}
            }
        """.trimIndent()
        metricsFile.writeText(payload)
        println("Test metrics written to ${metricsFile.relativeTo(projectDir)}")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.01")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    implementation("com.google.dagger:hilt-android:2.52")
    kapt("com.google.dagger:hilt-compiler:2.52")

    implementation("io.github.webrtc-sdk:android:125.6422.07")

    implementation("androidx.work:work-runtime-ktx:2.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("app.cash.turbine:turbine:1.0.0")

    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
