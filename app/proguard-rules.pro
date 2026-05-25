# ===========================================================================
# Tishina — R8 / ProGuard rules
# ===========================================================================
#
# The release build runs R8 in full mode (shrink + obfuscate + optimize) with
# resource shrinking. This file complements `proguard-android-optimize.txt`
# from the Android Gradle Plugin and the `consumer-proguard-rules.pro` shipped
# by Android library modules (Hilt, Compose, Room, Material etc.).
#
# Keep rules are kept narrow — overly broad `-keep class **` defeats the whole
# point of R8 size shrinking (NFR-4: release APK <= 6 MB).
#
# Goals:
#   * Preserve reflection targets used by Hilt-generated factories and
#     entry points that R8 cannot prove reachable.
#   * Preserve `kotlinx.serialization` `Companion`/`Serializer` objects for
#     `@Serializable` types used in Navigation routes and the OSS-licenses
#     JSON inventory.
#   * Preserve Room entity/DAO classes used by code-generated database impl.
#   * Keep attributes required by reflection-based libraries.
#   * Emit a usable mapping.txt for crash deobfuscation post-release.
# ===========================================================================

# ---------------------------------------------------------------------------
# Attributes — required by Kotlin metadata, kotlinx.serialization,
# Hilt-generated runtime helpers and stack-trace deobfuscation.
# ---------------------------------------------------------------------------
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# ---------------------------------------------------------------------------
# kotlinx.serialization — official keep rules.
# https://github.com/Kotlin/kotlinx.serialization#android
#
# R8 strips `Companion` and `$serializer` synthetic classes; without these
# rules the Compose Navigation routes (`TishinaDestination.Measure`, etc.)
# and `OssLicensesParser.Dto` throw `SerializationException` at runtime.
# ---------------------------------------------------------------------------
-keepattributes RuntimeVisibleAnnotations, AnnotationDefault

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    *** Companion;
}
-keepclasseswithmembers class **.*$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}

# Navigation 2.9+ resolves type-safe routes by reflecting on `@Serializable`
# data classes/objects. Keep the destination wrappers themselves so the
# generated route patterns survive shrinking.
-keep,includedescriptorclasses class ru.dmdp.tishina.navigation.TishinaDestination { *; }
-keep,includedescriptorclasses class ru.dmdp.tishina.navigation.TishinaDestination$* { *; }
-keep,includedescriptorclasses class ru.dmdp.tishina.feature.history.detail.DetailRoute { *; }

# ---------------------------------------------------------------------------
# Hilt / Dagger — generated factories, entry points and modules referenced
# by reflection from the runtime.
# ---------------------------------------------------------------------------
-keep class dagger.hilt.android.internal.managers.* { *; }
-keep class dagger.hilt.internal.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keepnames @dagger.hilt.android.lifecycle.HiltViewModel class *
-keepclassmembers class * {
    @dagger.hilt.android.lifecycle.HiltViewModel <init>(...);
    @javax.inject.Inject <init>(...);
}
-keep,allowobfuscation @interface dagger.hilt.**
-keep,allowobfuscation @interface javax.inject.*

# ---------------------------------------------------------------------------
# Room — entities and DAOs are used by the generated `*_Impl` database.
# ---------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase
-keep class * extends androidx.room.RoomDatabase$Builder { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keepclassmembers @androidx.room.Entity class * { *; }
-keep class ru.dmdp.tishina.core.data.db.** { *; }

# ---------------------------------------------------------------------------
# Compose runtime — reflectively accessed by the composer in some code paths.
# Slot tables themselves are reachable, but the runtime's annotation lookup
# needs the metadata to remain intact.
# ---------------------------------------------------------------------------
-keepclassmembers class androidx.compose.runtime.** { *; }

# ---------------------------------------------------------------------------
# Domain & data models reachable via reflection (kotlinx.serialization).
# ---------------------------------------------------------------------------
-keep class ru.dmdp.tishina.core.domain.model.** { *; }

# ---------------------------------------------------------------------------
# Coroutines — keep `DebugMetadata` for sane crash stack traces. (R8 already
# applies most coroutines rules from the bundled `consumer-rules.pro`.)
# ---------------------------------------------------------------------------
-dontwarn kotlinx.coroutines.debug.**

# ---------------------------------------------------------------------------
# AndroidX Lifecycle — `ViewModel(SavedStateHandle)` constructor lookup.
# ---------------------------------------------------------------------------
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# ---------------------------------------------------------------------------
# Silence non-actionable warnings from optional dependencies.
# ---------------------------------------------------------------------------
-dontwarn java.lang.invoke.StringConcatFactory
-dontwarn org.jetbrains.annotations.**
