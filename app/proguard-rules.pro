# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# General Android
-keepattributes *Annotation*, InnerClasses, Signature, Exception
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# Kotlin Reflect
-dontwarn kotlin.reflect.jvm.internal.**
-keep class kotlin.reflect.jvm.internal.** { *; }

# JsonElement nicht umbenennen. Sonst brechen Home-Assistant-Zustände
# und die Radio-Browser-Antwort nur im Release.
-keep class kotlinx.serialization.json.** { *; }
-keepclassmembers class kotlinx.serialization.json.** { *; }

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class io.qzz.xandll.morgo.**$$serializer { *; }
-keepclassmembers class io.qzz.xandll.morgo.** {
    *** Companion;
}
-keepclasseswithmembers class io.qzz.xandll.morgo.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep @Serializable classes
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# Ktor Client
-keep class io.ktor.client.** { *; }
-keep class io.ktor.http.** { *; }
-keep class io.ktor.utils.io.** { *; }
-keepclassmembers class io.ktor.** { volatile <fields>; }
-dontwarn io.netty.**
-dontwarn com.typesafe.**
-dontwarn org.slf4j.**
-dontwarn reactor.blockhound.**

# Kotlinx Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepclassmembers class kotlin.coroutines.SafeContinuation {
    volatile <fields>;
}
-dontwarn kotlinx.atomicfu.**

# Hilt
-dontwarn com.google.errorprone.annotations.*
-keepnames @dagger.hilt.android.lifecycle.HiltViewModel class * extends androidx.lifecycle.ViewModel
-keep class * extends androidx.lifecycle.ViewModel {
    <init>();
}
-keep class * extends androidx.lifecycle.AndroidViewModel {
    <init>(android.app.Application);
}

# WorkManager
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker
-keep class * extends androidx.work.ListenableWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keepclassmembers class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Hilt WorkManager
-keep class androidx.hilt.work.** { *; }
-keep @androidx.hilt.work.HiltWorker class * extends androidx.work.ListenableWorker

# Jetpack Compose
-keep class androidx.compose.** { *; }
-keepclassmembers class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Material 3
-keep class androidx.compose.material3.** { *; }

# Security Crypto
-keep class androidx.security.crypto.** { *; }
-keepclassmembers class * extends androidx.security.crypto.** { *; }

# DataStore
-keep class androidx.datastore.** { *; }
-keepclassmembers class androidx.datastore.** { *; }

# Enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# App-specific models - keep everything for safety
-keep class io.qzz.xandll.morgo.data.model.** { *; }
-keep class io.qzz.xandll.morgo.domain.model.** { *; }

# Media3. Der Stream-Player lädt Extractor und Decoder direkt.
# Ohne diese Regeln fehlt im Release der MP3-Pfad.
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.extractor.** { *; }
-keep class androidx.media3.datasource.** { *; }
-keep class androidx.media3.decoder.** { *; }
-keep class androidx.media3.common.** { *; }
-dontwarn androidx.media3.**

# ZXing Barcode Scanning
-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.barcodescanner.** { *; }
-dontwarn com.google.zxing.**
