# ProGuard rules for WhatStat

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
