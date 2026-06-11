# Add project specific ProGuard rules here.

# Project models and JSON entities (for Gson serialization)
-keep class wangdaye.com.geometricweather.common.basic.models.** { *; }
-keep class wangdaye.com.geometricweather.weather.json.** { *; }
-keep class wangdaye.com.geometricweather.db.entities.** { *; }
-keep class wangdaye.com.geometricweather.location.services.ip.** { *; }

# AndroidX annotations
-keep,allowobfuscation @interface androidx.annotation.Keep
-keep @androidx.annotation.Keep class *
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# General Android components
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference
-keep public class * extends android.view.View

-keepclasseswithmembernames class * {
    native <methods>;
}

-keepclassmembers class * extends android.app.Activity {
    public void *(android.view.View);
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

-keepclassmembers class **.R$* {
    public static <fields>;
}

# Serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Event listeners
-keepclassmembers class * {
    void *(**On*Event);
    void *(**On*Listener);
}

# WebView
-keepclassmembers class * extends android.webkit.WebViewClient {
    public void *(android.webkit.WebView, java.lang.String, android.graphics.Bitmap);
    public boolean *(android.webkit.WebView, java.lang.String);
}

# Strip verbose/diagnostic log calls (keep errors for debugging)
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
}

# AMap
-keep class com.amap.api.location.** { *; }
-keep class com.amap.api.fence.** { *; }
-keep class com.loc.** { *; }
-keep class com.autonavi.aps.amapapi.model.** { *; }

# Baidu
-keep class com.baidu.location.** { *; }
-keep class vi.com.gdi.bgl.android.java.** { *; }

# Keep location SDK services declared in manifest
-keep,allowshrinking class com.baidu.location.f
-keep,allowshrinking class com.baidu.location.LLSInterface
-keep,allowshrinking class com.amap.api.location.APSService

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public enum com.bumptech.glide.load.resource.bitmap.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}

# Gson
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.stream.** { *; }

# OkHttp3
-dontwarn com.squareup.okhttp3.**
-keep class com.squareup.okhttp3.** { *; }

# Okio
-dontwarn okio.**
-dontwarn javax.annotation.Nullable
-dontwarn javax.annotation.ParametersAreNonnullByDefault
-keep class okio.** { *; }

# Retrofit
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions

# CircularProgressView
-keep class com.github.rahatarmanahmed.cpv.** { *; }

# Bugly
-dontwarn com.tencent.bugly.**
-keep public class com.tencent.bugly.** { *; }

# Material Sheet FAB
-keep class io.codetail.animation.arcanimator.** { *; }
