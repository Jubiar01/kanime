import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}
val tmdbApiKey: String = (localProps.getProperty("TMDB_API_KEY") ?: System.getenv("TMDB_API_KEY") ?: "").trim()

// Load release signing credentials from private app/info.txt (gitignored)
val releaseSigningInfo: Map<String, String> = run {
    val infoFile = file("info.txt")
    if (!infoFile.exists()) {
        emptyMap()
    } else {
        infoFile.readLines().mapNotNull { line ->
            val match = Regex("""^\s*(\w+)\s+['"]([^'"]+)['"]\s*$""").find(line)
            match?.let { it.groupValues[1] to it.groupValues[2] }
        }.toMap()
    }
}
val releaseKeystoreFile = file("marvin-J.jks")

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.kanime.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.kanime.app"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "TMDB_API_KEY", "\"${tmdbApiKey.replace("\"", "\\\"")}\"")
    }

    signingConfigs {
        if (releaseKeystoreFile.exists() && releaseSigningInfo.isNotEmpty()) {
            create("release") {
                storeFile = releaseKeystoreFile
                storePassword = releaseSigningInfo["storePassword"]
                keyAlias = releaseSigningInfo["keyAlias"]
                keyPassword = releaseSigningInfo["keyPassword"]
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfigs.findByName("release")?.let { signingConfig = it }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.media3.common.util.UnstableApi"
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.work.runtime)
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.effect)

    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
