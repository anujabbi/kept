# R8 keep rules for the release build (issue #5).
#
# Room, Hilt/Dagger and DataStore ship consumer rules in their AARs, so most of what follows is
# belt-and-braces for the parts that are reached reflectively and are not covered by a generated
# rule. Anything that is genuinely load-bearing is called out; a rule with no reason attached
# should be treated as a candidate for deletion the next time these are revisited.

# --- Kotlin -----------------------------------------------------------------------------------
# Coroutine internals and the metadata Kotlin reflection reads.
-keepclassmembers class kotlin.Metadata { *; }
-dontwarn kotlinx.coroutines.**
-keepclassmembernames class kotlinx.coroutines.** { volatile <fields>; }

# --- Room -------------------------------------------------------------------------------------
# Room generates `KeptDatabase_Impl` and one `*_Impl` per DAO, and loads them by name from the
# non-obfuscated class name of the abstract type. Entities are read/written by generated code, but
# the TypeConverters are resolved by signature, so keep both.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface *
-keepclassmembers class * { @androidx.room.TypeConverter <methods>; }
-dontwarn androidx.room.paging.**

# --- Hilt / Dagger ----------------------------------------------------------------------------
# Hilt's generated components and the entry points reached by name from Android components
# (BootReceiver, AlarmReceiver, NotificationActionReceiver, the WorkManager factory).
-keep class dagger.hilt.internal.aggregatedroot.codegen.** { *; }
-keep class hilt_aggregated_deps.** { *; }
-keep @dagger.hilt.android.EarlyEntryPoint class * { *; }
-keepclasseswithmembers class * { @dagger.hilt.android.EntryPoint *; }
-keep,allowobfuscation @interface dagger.hilt.android.EntryPoint
-keep class * extends androidx.work.ListenableWorker { <init>(...); }

# --- DataStore --------------------------------------------------------------------------------
# Preferences DataStore serialises through protobuf-lite, which resolves its message classes and
# their `newMessageInfo` members reflectively.
-keep class androidx.datastore.*.** { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}
-dontwarn com.google.protobuf.**

# --- PostHog ----------------------------------------------------------------------------------
# posthog-android ships consumer rules, but its event payloads go through Gson/Moshi-style
# reflection over the SDK's own API model, and the Android SDK reaches the JVM core by name.
# Without these a release build reports nothing and fails silently, which is the worst failure
# mode available, so they are explicit rather than trusted to the AAR.
-keep class com.posthog.** { *; }
-keepclassmembers class com.posthog.** { *; }
-dontwarn com.posthog.**

# --- Sign in with Google (#193): supabase-kt, Ktor, kotlinx.serialization -----------------------
# supabase-kt decodes the auth server's JSON (the session, the user) with kotlinx.serialization.
# Its generated serializers are found through each @Serializable class's Companion and
# `serializer()`; if R8 renames or drops them the release build fails only at runtime, with a
# SerializationException on sign-in or on restoring a stored session.
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class io.github.jan.supabase.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class io.github.jan.supabase.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class io.github.jan.supabase.**$$serializer { *; }
# The session is stored through multiplatform-settings, set up by its androidx.startup initializer
# (reached by name from the merged manifest).
-keep class com.russhwolf.settings.** { *; }
# Ktor's Android engine is passed in explicitly (no ServiceLoader lookup), but Ktor references
# JVM classes Android does not have (java.lang.management, SLF4J); they are never reached.
-dontwarn io.ktor.**
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
# Credential Manager finds the Play services provider by the class name in its manifest metadata.
-keep class androidx.credentials.playservices.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }

# --- App ------------------------------------------------------------------------------------
# Serializable/Parcelable-ish enums crossing Room's TypeConverters by `valueOf`.
-keepclassmembers enum com.example.kept.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
