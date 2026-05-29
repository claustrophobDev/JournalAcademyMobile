# --- kotlinx.serialization -------------------------------------------------
# The plugin generates a companion `serializer()` per @Serializable class; R8's
# shipped rules cover the common cases, these pin down our own model classes.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.claustrophob.journal.** {
    *** Companion;
}
-keepclasseswithmembers class com.claustrophob.journal.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.claustrophob.journal.data.**$$serializer { *; }

# --- WorkManager -----------------------------------------------------------
# Workers are constructed reflectively from the class name stored in the DB.
-keep class com.claustrophob.journal.work.SyncWorker { <init>(...); }

# --- OkHttp ----------------------------------------------------------------
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Keep line numbers so release crash reports stay readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
