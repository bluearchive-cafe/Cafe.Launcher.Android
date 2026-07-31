import org.gradle.api.tasks.compile.JavaCompile

plugins {
    id("com.android.application")
}

android {
    namespace = "cafe.bluearchive.installer"
    compileSdk = 35

    defaultConfig {
        applicationId = "cafe.bluearchive.installer"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        // The target game package name — override via gradle.properties or CLI:
        //   ./gradlew assembleRelease -PGAME_PACKAGE_NAME=com.example.game
        buildConfigField("String", "GAME_PACKAGE_NAME", "\"${findProperty("GAME_PACKAGE_NAME") ?: "com.YostarJP.BlueArchive"}\"")
        buildConfigField("String", "GAME_ACTIVITY_NAME", "\"${findProperty("GAME_ACTIVITY_NAME") ?: "com.yostarjp.bluearchive.MxUnityPlayerActivity"}\"")
        buildConfigField("String", "APKS_DOWNLOAD_URL", "\"${findProperty("APKS_DOWNLOAD_URL") ?: "https://download.bluearchive.cafe/android/latest"}\"")
    }

    buildTypes {
        release {
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
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:deprecation")
}