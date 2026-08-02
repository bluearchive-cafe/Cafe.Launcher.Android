import org.gradle.api.tasks.compile.JavaCompile
import java.net.URI

plugins {
    id("com.android.application")
}

fun Project.stringProperty(name: String, defaultValue: String): String =
    (findProperty(name) as String?)?.ifBlank { defaultValue } ?: defaultValue

fun Project.signingValue(name: String): String? =
    (findProperty(name) as String?)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: providers.environmentVariable(name).orNull
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

val signingInputNames = listOf(
    "ANDROID_SIGNING_KEYSTORE_FILE",
    "ANDROID_SIGNING_STORE_PASSWORD",
    "ANDROID_SIGNING_KEY_ALIAS",
    "ANDROID_SIGNING_KEY_PASSWORD"
)

fun Project.missingSigningInputs(): List<String> =
    signingInputNames.filter { signingValue(it).isNullOrEmpty() }

fun javaStringLiteral(value: String): String {
    val escaped = buildString {
        append('"')
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (ch.code < 0x20) {
                        append("\\u%04x".format(ch.code))
                    } else {
                        append(ch)
                    }
                }
            }
        }
        append('"')
    }
    return escaped
}

fun validatePackageName(value: String, propertyName: String): String {
    require(value.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))) {
        "$propertyName must be a valid Java/Android package name"
    }
    return value
}

fun validateComponentName(value: String, propertyName: String): String {
    require(value.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))) {
        "$propertyName must be a valid fully-qualified component name"
    }
    return value
}

fun validateHttpsUrl(value: String, propertyName: String): String {
    val uri = URI(value)
    require(uri.scheme == "https") { "$propertyName must use HTTPS" }
    require(!uri.host.isNullOrBlank()) { "$propertyName must include a host" }
    require(uri.userInfo == null) { "$propertyName must not include credentials" }
    require(uri.fragment == null) { "$propertyName must not include a fragment" }
    return value
}

fun validateBase64OrEmpty(value: String, propertyName: String): String {
    if (value.isBlank()) return value
    require(value.matches(Regex("[A-Za-z0-9+/=\\r\\n]+"))) {
        "$propertyName must be Base64-encoded"
    }
    return value
}

val gamePackageName = validatePackageName(
    stringProperty("GAME_PACKAGE_NAME", "com.YostarJP.BlueArchive"),
    "GAME_PACKAGE_NAME")
val gameActivityName = validateComponentName(
    stringProperty("GAME_ACTIVITY_NAME", "com.yostarjp.bluearchive.MxUnityPlayerActivity"),
    "GAME_ACTIVITY_NAME")
val apksDownloadUrl = validateHttpsUrl(
    stringProperty("APKS_DOWNLOAD_URL", "https://download.bluearchive.cafe/android/latest"),
    "APKS_DOWNLOAD_URL")
val apksManifestUrl = validateHttpsUrl(
    stringProperty("APKS_MANIFEST_URL", "https://download.bluearchive.cafe/android/latest.manifest.json"),
    "APKS_MANIFEST_URL")
val releaseManifestPublicKey = validateBase64OrEmpty(
    stringProperty("RELEASE_MANIFEST_PUBLIC_KEY", ""),
    "RELEASE_MANIFEST_PUBLIC_KEY")

val signingKeystoreFile = signingValue("ANDROID_SIGNING_KEYSTORE_FILE")
val signingStorePassword = signingValue("ANDROID_SIGNING_STORE_PASSWORD")
val signingKeyAlias = signingValue("ANDROID_SIGNING_KEY_ALIAS")
val signingKeyPassword = signingValue("ANDROID_SIGNING_KEY_PASSWORD")
val releaseSigningConfigured = missingSigningInputs().isEmpty()

android {
    namespace = "cafe.bluearchive.installer"
    compileSdk = 35

    defaultConfig {
        applicationId = "cafe.bluearchive.installer"
        minSdk = 23
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.0-beta.2"

        // Target game and release metadata. Override via gradle.properties or CLI:
        //   ./gradlew assembleRelease -PGAME_PACKAGE_NAME=com.example.game
        buildConfigField("String", "GAME_PACKAGE_NAME", javaStringLiteral(gamePackageName))
        buildConfigField("String", "GAME_ACTIVITY_NAME", javaStringLiteral(gameActivityName))
        buildConfigField("String", "APKS_DOWNLOAD_URL", javaStringLiteral(apksDownloadUrl))
        buildConfigField("String", "APKS_MANIFEST_URL", javaStringLiteral(apksManifestUrl))
        buildConfigField("String", "RELEASE_MANIFEST_PUBLIC_KEY", javaStringLiteral(releaseManifestPublicKey))

        manifestPlaceholders["gamePackageName"] = gamePackageName
    }

    bundle {
        language {
            enableSplit = false
        }
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(signingKeystoreFile!!)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        release {
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        aidl = true
    }
}

gradle.taskGraph.whenReady {
    val releaseTaskRequested = allTasks.any { task ->
        val path = task.path.lowercase()
        path.contains("release") && (
                path.contains("assemble")
                        || path.contains("bundle")
                        || path.contains("package")
                        || path.contains("sign"))
    }
    if (releaseTaskRequested) {
        val missing = missingSigningInputs()
        require(missing.isEmpty()) {
            "Release signing is required. Missing: ${missing.joinToString()}. " +
                    "Set them as Gradle properties or environment variables."
        }
        val keystorePath = signingKeystoreFile!!
        require(file(keystorePath).isFile) {
            "Release signing keystore was not found: $keystorePath"
        }
    }
}

dependencies {
    constraints {
        implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.22")
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.22")
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.22")
    }

    implementation("androidx.activity:activity:1.9.3")
    implementation("androidx.core:core:1.13.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.android.material:material:1.12.0")

    // Shizuku — privileged install via ADB/root
    val shizukuVersion = "12.2.0"
    implementation("dev.rikka.shizuku:api:$shizukuVersion")
    implementation("dev.rikka.shizuku:provider:$shizukuVersion")

    // libsu — root shell access
    implementation("com.github.topjohnwu.libsu:core:5.2.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:deprecation")
}
