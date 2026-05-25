# ProGuard rules for WhatStat

# ── Offuscamento Aggressivo ───────────────────────────────────────
-repackageclasses ''
-allowaccessmodification
-overloadaggressively
-optimizationpasses 5

# ── Hilt ──────────────────────────────────────────────────────────
-keepnames @dagger.hilt.android.lifecycle.HiltViewModel class * extends androidx.lifecycle.ViewModel

# ── libsu ─────────────────────────────────────────────────────────
-keep class com.topjohnwu.superuser.** { *; }
-keep class com.topjohnwu.superuser.internal.** { *; }

# ── Vico Charts ───────────────────────────────────────────────────
-keep class com.patrykandpatrick.vico.** { *; }

# ── Kotlin Coroutines ─────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ── Data Classes (mantenere per serializzazione) ──────────────────
-keepclassmembers class com.whatstats.domain.model.** { *; }
-keepclassmembers class com.whatstats.data.db.model.** { *; }

# ── Compose ───────────────────────────────────────────────────────
-dontwarn androidx.compose.**

# ── Google Mobile Ads + UMP ───────────────────────────────────────
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.android.gms.internal.ads.** { *; }
-keep class com.google.android.ump.** { *; }
-keep class com.google.ads.** { *; }

# ── Rimozione Log in Release ──────────────────────────────────────
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# ── Rinomina Source File ──────────────────────────────────────────
-renamesourcefileattribute "SourceFile"
-keepattributes SourceFile,LineNumberTable
