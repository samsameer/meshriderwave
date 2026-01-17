# Mesh Rider Wave ProGuard Rules
# Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.

# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
}
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.doodlelabs.meshriderwave.**$$serializer { *; }
-keepclassmembers class com.doodlelabs.meshriderwave.** {
    *** Companion;
}
-keepclasseswithmembers class com.doodlelabs.meshriderwave.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# WebRTC
-keep class org.webrtc.** { *; }
-keep class org.webrtc.audio.** { *; }
-keep class org.webrtc.voiceengine.** { *; }

# libsodium
-keep class org.libsodium.jni.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# ZXing
-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.barcodescanner.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Keep our models
-keep class com.doodlelabs.meshriderwave.domain.model.** { *; }
-keep class com.doodlelabs.meshriderwave.data.local.** { *; }
