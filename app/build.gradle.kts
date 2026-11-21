import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("app.cash.paparazzi")
}

android {
    namespace = "io.qzz.xandll.morgo"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.qzz.xandll.morgo"
        minSdk = 29
        targetSdk = 35
        versionCode = 16
        versionName = "1.6.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    val localProps = Properties()
    val localFile = rootProject.file("local.properties")
    if (localFile.isFile) localFile.inputStream().use { localProps.load(it) }
    fun secret(envName: String, localKey: String): String? {
        return System.getenv(envName)?.takeIf { it.isNotBlank() } ?: localProps.getProperty(localKey)?.takeIf { it.isNotBlank() }
    }
    val releaseKeystore = secret("MORGO_KEYSTORE_FILE", "morgo.keystore")?.let(::File)
    val releaseKeyProps = secret("MORGO_KEYSTORE_PROPERTIES", "morgo.keystore.properties")?.let(::File)
    val releaseStorePassword = secret("MORGO_KEYSTORE_PASSWORD", "morgo.storePassword")
    val releaseKeyAlias = secret("MORGO_KEY_ALIAS", "morgo.keyAlias")
    val releaseKeyPassword = secret("MORGO_KEY_PASSWORD", "morgo.keyPassword")
    signingConfigs {
        if (releaseKeystore != null && releaseKeystore.isFile) {
            val fromFile = if (releaseKeyProps != null && releaseKeyProps.isFile) {
                Properties().apply { releaseKeyProps.inputStream().use { load(it) } }
            } else {
                null
            }
            val storePassword = releaseStorePassword ?: fromFile?.getProperty("storePassword")
            val keyAlias = releaseKeyAlias ?: fromFile?.getProperty("keyAlias")
            val keyPassword = releaseKeyPassword ?: fromFile?.getProperty("keyPassword")
            if (!storePassword.isNullOrBlank() && !keyAlias.isNullOrBlank() && !keyPassword.isNullOrBlank()) {
                create("release") {
                    storeFile = releaseKeystore
                    this.storePassword = storePassword
                    this.keyAlias = keyAlias
                    this.keyPassword = keyPassword
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // AndroidX Core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.3")
    
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.10.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    
    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-android-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    
    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")
    
    // ZXing Barcode Scanning (smaller than ML Kit)
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    
    // Ktor Client
    val ktorVersion = "2.3.12"
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-android:$ktorVersion")
    implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-client-logging:$ktorVersion")
    implementation("io.ktor:ktor-client-auth:$ktorVersion")
    
    // Kotlinx Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    
    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    
    // Security (EncryptedSharedPreferences)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Stream-Wecker und Sonara-Session
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("io.mockk:mockk:1.13.12")
    testImplementation("app.cash.turbine:turbine:1.1.0")
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
    
    // Android Testing
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("org.robolectric:robolectric:4.13")
    
    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
