# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Firebase and Gson deserialize persisted/server data reflectively.
-keepattributes Signature,*Annotation*
-keep class com.avapod.app.models.** { *; }
-keep class com.avapod.app.utils.DownloadRecord { *; }
-keep class * extends com.google.gson.reflect.TypeToken { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
# Category icons are selected from names supplied by the catalog.

# Room 2.2.5 creates generated database implementations through reflection.
# Keep their no-argument constructors in R8 full mode, including WorkDatabase_Impl
# which WorkManager initializes before MainActivity starts.
-keep class * extends androidx.room.RoomDatabase {
    public <init>();
}

# Retrofit 2.9 does not bundle the full-mode suspend/response rules added later.
# These types are inspected reflectively when parsing getRssFeed's signature.
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault
-keep interface com.avapod.app.network.PodcastApiService { *; }
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep,allowobfuscation,allowshrinking class retrofit2.Response
