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
-keepclassmembers class com.whatstat.domain.model.** { *; }
-keepclassmembers class com.whatstat.data.db.model.** { *; }

# ── Compose ───────────────────────────────────────────────────────
-dontwarn androidx.compose.**

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
