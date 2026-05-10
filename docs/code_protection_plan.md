# Piano di Protezione del Codice — WhatStat Reborn

> **Versione**: 1.0  
> **Data**: 2026-05-10  
> **Autore**: Luigi Scialpi 
> **Stato**: Bozza

---

## 1. Analisi della Minaccia (Threat Model)

### 1.1 Vettori di attacco identificati

| # | Vettore | Livello di Rischio | Descrizione |
|---|---------|-------------------|-------------|
| T1 | Decompilazione APK | **Alto** | Un attaccante può estrarre `classes.dex` e decompilarlo con `jadx`/`apktool` per ottenere codice quasi-Java leggibile |
| T2 | Estrazione chiavi crittografiche | **Critico** | Chiavi AES e logica di decryption sono nel codice dell'app e possono essere estratte |
| T3 | Ingegneria inversa della logica di decifratura | **Alto** | Il formato crypt14/crypt15 di WhatsApp è ben documentato; l'app implementa l'algoritmo |
| T4 | Hooking runtime (Frida/Xposed) | **Medio** | Un attaccante può intercettare le chiavi in memoria durante l'esecuzione |
| T5 | Modifica dell'APK (tampering) | **Medio** | Possibilità di modificare l'APK per rimuovere limitazioni o iniettare codice |
| T6 | Backup ADB | **Basso** | Estrazione del database Room via `adb backup` |

### 1.2 Asset protetti

- **Chiavi crittografiche** del database WhatsApp (file `.key`)
- **Logica di decifratura** (Crypt14Decryptor, KeyReader)
- **Dati utente** locali (backup decifrati, risultati analisi)
- **Database Room** con risultati delle analisi

---

## 2. Struttura di Difesa in Profondità

### Livello 1 — Code Obfuscation (ProGuard/R8)

**Stato attuale**: ✅ Configurato con regole minime  
**Azioni richieste**:

#### 2.1.1 Potenziamento delle regole ProGuard

Aggiornare `app/proguard-rules.pro` con:

```proguard
# ── Obfuscation Aggressiva ─────────────────────────────────────
# Rinomina classi, metodi e campi con nomi corti e senza senso
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontpreverify
-verbose

# Rimuove informazioni di debug
-keepattributes Exceptions,InnerClasses,Signature,Deprecated,
                SourceFile,LineNumberTable,*Annotation*,EnclosingMethod
# NOTA: Rimuovere SourceFile e LineNumberTable in produzione
-keepattributes SourceFile,LineNumberTable

# Ofusca il package crypto (logica critica)
-repackageclasses 'com.whatstat.reborn.data.crypto.obf'
-flattenpackagehierarchy 'com.whatstat.reborn.data.crypto.obf'

# Rimuove logging in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# Rimuove assert in produzione
-assumenosideeffects class junit.framework.Assert {
    public static *** assert*(...);
}
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
}

# Rimuove StackTrace dettagliati (opzionale, può complicare il debug)
-renamesourcefileattribute "Obfuscated"

# ── Mantenimento minimo necessario ─────────────────────────────
-keep class com.whatstat.reborn.App { *; }
-keep class com.whatstat.reborn.di.** { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * extends androidx.lifecycle.ViewModel

# libsu
-keep class com.topjohnwu.superuser.** { *; }
-keep class com.topjohnwu.superuser.internal.** { *; }

# Vico Charts
-keep class com.patrykandpatrick.vico.** { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class * {
    *** $getDefaultNetwork();
}

# Room
-keep class * extends androidx.room.RoomDatabase { *; }
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
-keep class com.whatstat.reborn.data.db.model.** { *; }

# Hilt
-keep class com.whatstat.reborn.data.db.** { *; }
-dontwarn com.whatstat.reborn.data.db.**

# Data Classes / Serialization
-keepclassmembers class com.whatstat.reborn.domain.model.** { *; }
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Compose
-dontwarn androidx.compose.**
-dontwarn kotlinx.collections.immutable.**
-keepclassmembers class androidx.compose.** { *** INSTANCE; }

# Hilt modules
-keepclassmembers class * {
    @dagger.Module <init>(...);
}
-keep class * {
    @dagger.Provides <methods>;
}
```

#### 2.1.2 Configurazione Aggiuntiva R8

Aggiungere in `app/build.gradle.kts`:

```kotlin
buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
        // Abilita R8 full mode per ofuscamento più aggressivo
        // (abilitato di default, verificare che non sia disabilitato)
    }
}
```

---

### Livello 2 — Ofuscamento delle Stringhe Sensibili

**Stato attuale**: ❌ Non implementato  
**Priorità**: Critica

#### 2.2.1 Stringhe hardcoded (Secure Implementation)

Le stringhe `String` sono immutabili e rimangono nello heap. Usare `CharArray` per permettere la cancellazione manuale immediata.

```kotlin
// Util: String obfuscation con XOR + Base64 + Wipe
object SecureStringProtector {
    private const val XOR_KEY = 0x42.toByte()
    
    /**
     * Decifra la stringa in un CharArray cancellabile.
     * MAI convertire il risultato in String se non strettamente necessario.
     */
    fun decodeToChars(encoded: String): CharArray {
        val decodedBytes = Base64.decode(encoded, Base64.NO_WRAP)
        val result = CharArray(decodedBytes.size)
        for (i in decodedBytes.indices) {
            result[i] = (decodedBytes[i] xor XOR_KEY).toInt().toChar()
        }
        // Cancella i byte intermedi
        decodedBytes.fill(0)
        return result
    }
    
    /**
     * Sovrascrive il CharArray con zeri per rimuoverlo dalla memoria.
     */
    fun wipe(chars: CharArray) {
        chars.fill('\u0000')
    }
}
```

Utilizzo sicuro:
```kotlin
val apiKeyChars = SecureStringProtector.decodeToChars("BASE64_ENCODED_XOR_STRING")
try {
    // Usa apiKeyChars per operazioni crittografiche o network
} finally {
    SecureStringProtector.wipe(apiKeyChars)
}
```

#### 2.2.2 Rimozione di stringhe dal bytecode

Aggiungere a ProGuard:
```proguard
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}
```

---

### Livello 3 — Hardening del Manifest e Network

**Stato attuale**: ✅ Buona configurazione base  
**Azioni**:

| Check | Stato Attuale | Azione |
|-------|---------------|--------|
| `allowBackup="false"` | ✅ Presente | Mantenere |
| `tools:node="remove"` su INTERNET | ✅ Presente | Mantenere |
| `networkSecurityConfig` bloccante | ✅ Presente | Mantenere |
| `android:debuggable="false"` | ✅ Implicito in release | Verificare che nessuno lo imposti |
| `android:testOnly="false"` | ✅ Implicito | Non preoccuparsi |

#### 2.3.1 Debug Flag Check Runtime

Aggiungere nell'`Application` class:

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            // Logica di debug solo in modalità debug
        }
        
        // Verifica anti-debug aggiuntiva
        if (isDebuggableBuild()) {
            // Opzionale: notifica, crash silenzioso, o funzionalità limitate
        }
    }
    
    private fun isDebuggableBuild(): Boolean {
        return (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 ||
               System.getProperty("ro.debuggable") == "1" ||
               checkDebuggerAttached()
    }
    
    private fun checkDebuggerAttached(): Boolean {
        return Debug.isDebuggerConnected() || Debug.waitingForDebugger()
    }
}
```

#### 2.3.2 Protezione Overlay e Screenshot (POSTICIPATO — NON IMPLEMENTARE)

> **STATO**: NON IMPLEMENTARE. La protezione overlay impedisce screenshot legittimi e peggiora la UX. Rimane nel piano solo come riferimento teorico.

```kotlin
// NON IMPLEMENTARE ALLO STATO ATTUALE
// window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, ...)
```

---

### Livello 4 — Protezione dei Dati in Memoria (Anti-Hooking)

**Stato attuale**: ❌ Non implementato  
**Priorità**: Alta

#### 2.4.1 Protezione chiavi crittografiche in memoria

Le chiavi AES gestite in `Crypt14Decryptor` e `KeyReader` risiedono in memoria come `ByteArray`. Implementare:

```kotlin
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

object SecureKeyManager {
    private val secureRandom = SecureRandom()
    
    /**
     * Genera una chiave AES effimera e la conserva in un array
     * che deve essere cancellato esplicitamente dopo l'uso.
     */
    fun createEphemeralAesKey(): SecretKeySpec {
        val key = ByteArray(32)
        secureRandom.nextBytes(key)
        return SecretKeySpec(key, "AES").also {
            // Cancella l'array originale immediatamente
            key.fill(0)
        }
    }
    
    /**
     * Cancella sicura di un array di byte dalla memoria
     */
    fun wipeBytes(bytes: ByteArray) {
        bytes.fill(0)
        // Suggerimento: chiamare anche System.gc() per accelerare
        // la rimozione dei dati dallo heap
    }
}
```

Modificare `KeyReader` per cancellare le chiavi dopo l'uso:

```kotlin
// In KeyReader.readKey() e deriveEncryptionKey()
// Dopo aver usato la chiave:
key.fill(0)  // Cancella il ByteArray della chiave
```

#### 2.4.2 Protezione contro Frida/Xposed

Aggiungere rilevamento di hooking runtime:

```kotlin
object AntiHookDetector {
    
    fun detectFrida(): Boolean {
        val suspiciousLibraries = listOf(
            "frida", "substrate", "xposed", "edxp",
            "de.robv.android.xposed", "io.objects.frida"
        )
        
        // 1. Controlla processi in esecuzione
        try {
            Runtime.getRuntime().exec("ps").inputStream.bufferedReader()
                .readLines()
                .forEach { line ->
                    suspiciousLibraries.forEach { lib ->
                        if (line.contains(lib)) return true
                    }
                }
        } catch (_: Exception) {}
        
        // 2. Controlla porte di default di Frida
        try {
            val ports = listOf(27042, 27043, 27044, 27045, 27046)
            for (port in ports) {
                try {
                    java.net.Socket("127.0.0.1", port).close()
                    return true
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        
        return false
    }
    
    fun detectXposed(): Boolean {
        try {
            // Verifica la firma dell'APK (tamper detection)
            val sig = getPackageManager()
                .getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                .signatures[0]
            val currentHash = MessageDigest.getInstance("SHA-256")
                .digest(sig.toByteArray())
                .joinToString("") { "%02x".format(it) }
            // Confronta con hash noto (salvato in resources cifrate)
            val expectedHash = getExpectedSignatureHash()
            return currentHash != expectedHash
        } catch (_: Exception) {}
        return false
    }
    
    fun detectDebugger(): Boolean {
        return Debug.isDebuggerConnected() || Debug.waitingForDebugger()
    }
}
```

Integrare nel flusso di decryption:

// In Crypt14Decryptor.decrypt()
companion object {
    init {
        // Check anti-hooking all'avvio del decryptor
        if (AntiHookDetector.detectFrida() || AntiHookDetector.detectDebugger()) {
            // Opzione A: Bloccare l'operazione con messaggio generico
            // Opzione B: Restituire errore finto
            throw SecurityException("Ambiente non sicuro rilevato")
        }
    }
}
```


---

### Livello 5 — Ofuscamento del DEX e Native Code

**Stato attuale**: ❌ Non implementato  
**Priorità**: Media-Alta

#### 2.5.1 MultiDEX con Split

Suddividere il codice critico in DEX secondari caricati dinamicamente:

```kotlin
// Caricamento dinamico di un DEX critico
fun loadSecureDex(context: Context) {
    val dexFile = File(context.filesDir, "core_utils.dex")
    val classLoader = DexClassLoader(
        dexFile.absolutePath,
        context.codeCacheDir.absolutePath,
        null,
        context.classLoader
    )
    
    val cryptoClass = classLoader.loadClass("com.obf.crypto.CoreDecryptor")
    val method = cryptoClass.getMethod("decrypt", File::class.java, File::class.java)
    method.invoke(null, cryptFile, outputFile)
}
```

> **Nota**: La distribuzione del `.dex` deve avvenire tramite download crittografato o embedding nel resources. Questo approccio complica l'analisi ma richiede attenzione alla sicurezza del download.

#### 2.5.2 Codice Nativo (C/C++ con JNI)

Spostare la logica crittografica in libreria nativa:

```
app/src/main/cpp/
├── native-lib.cpp       # Wrapper JNI
├── crypto_engine.c       # Logica AES/GCM implementata in C
├── gf_multiply.c         # Galois Field multiplication
└── CMakeLists.txt
```

Vantaggi:
- Il codice nativo `.so` è molto più difficile da decompilare rispetto a DEX/bytecode
- Le stringhe sensibili possono essere costruite a runtime nei registri CPU

**Implementazione consigliata per**: `Crypt14Decryptor` e `KeyReader`

---

### Livello 6 — Tamper Detection

**Stato attuale**: ❌ Non implementato  
**Priorità**: Alta

#### 2.6.1 Firma APK Check

```kotlin
object IntegrityChecker {
    
    private const val KNOWN_SIGNATURE_HASH = "SHA256_OF_RELEASE_KEY"
    
    fun verifyAppSignature(context: Context): Boolean {
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_SIGNATURES
        )
        val cert = packageInfo.signatures[0]
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(cert.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return hash == KNOWN_SIGNATURE_HASH
    }
    
    fun verifyInstaller(context: Context): Boolean {
        val installer = context.packageManager.getInstallerPackageName(context.packageName)
        val allowedInstallers = listOf(
            "com.android.vending",  // Google Play
            null  // Sideloading consentito
        )
        return installer in allowedInstallers
    }
}
```

#### 2.6.2 Debug Certificate Detection

```kotlin
fun isSignedWithDebugKey(context: Context): Boolean {
    val packageInfo = context.packageManager.getPackageInfo(
        context.packageName, PackageManager.GET_SIGNATURES
    )
    val debugCert = context.packageManager.getPackageInfo(
        context.packageName, PackageManager.GET_SIGNATURES
    ).signatures[0]
    
    // La chiave di debug ha un hash noto e prevedibile
    return debugCert.toCharsString() == "ANDROID DEBUG KEY" ||
           debugCert.toCharsString().contains("android debug")
}
```

---

### Livello 7 — Protezione del Database Locale

**Stato attuale**: ❌ Database Room non cifrato  
**Priorità**: Alta

#### 2.7.1 Cifratura del database Room

Implementare cifratura SQLCipher per il database Room:

```kotlin
// build.gradle.kts
// implementation("net.zetetic:android-database-sqlcipher:4.5.4")

@Provides
@Singleton
fun provideDatabase(@ApplicationContext ctx: Context, keyReader: KeyReader): MyDatabase {
    val passphrase = SQLiteDatabase.getBytes(keyReader.getDbEncryptionKey())
    val factory = SupportFactory(passphrase)
    return Room.databaseBuilder(ctx, MyDatabase::class.java, "whatstat.db")
        .openHelperFactory(factory)
        .build()
}
```

---

### Livello 8 — CI/CD e Signing Sicuro

**Stato attuale**: ⚠️ Keystore nel progetto locale  
**Priorità**: Alta

#### 2.8.1 Keystore Management

**Problema attuale**: Il keystore `whatstat-keystore.jks` è nel directory del progetto. Le credenziali sono in `local.properties` (gitignored).

**Miglioramento raccomandato**:

```
Opzione A: GitHub Actions Secrets (se si implementa CI/CD)
├── Salvare STORE_PASSWORD e KEY_PASSWORD come GitHub Secrets
├── Generare/render disponibile il .jks in CI
└── Nessun file sensibile nel repository

Opzione B: Keystore in ambiente di build isolato
├── Conservare il keystore su macchina dedicata
├── Firmare l'APK solo dall'ambiente di build sicuro
└── Non committare mai il .jks
```

#### 2.8.2 Vulnerabilità attuale da correggere IMMEDIATAMENTE

⚠️ **Il file `local.properties` contiene le password del keystore in chiaro:**
```
STORE_PASSWORD=Zibbibbu@990
KEY_PASSWORD=Zibbibbu@990
```

**Azione**: Spostare questi valori in variabili d'ambiente del sistema (`~/.gradle/gradle.properties` con permessi `chmod 600`) o in un gestore di segreti.

---

## 3. Riepilogo delle Azioni Prioritarie

### Fase 1 — Critica (Implementazione Immediata)

| # | Azione | File | Difficoltà |
|---|--------|------|-----------|
| 1.1 | Potenziamento ProGuard/R8 | `app/proguard-rules.pro` | 🟢 Bassa |
| 1.2 | Rimozione logging in produzione | `app/proguard-rules.pro` | 🟢 Bassa |
| 1.3 | Spostare password keystore in env vars | `~/.gradle/gradle.properties` | 🟢 Bassa |
| 1.4 | Cancellazione `ByteArray` chiavi dopo uso | `KeyReader.kt`, `Crypt14Decryptor.kt` | 🟡 Media |
| 1.5 | Verifica firma APK a runtime | Nuovo: `IntegrityChecker.kt` | 🟡 Media |
| 1.6 | Protezione Overlay (`FLAG_SECURE`) | `BaseActivity.kt` | 🔴 SKIPPED |

### Fase 2 — Alta Priorità (Entro 2 settimane)

| # | Azione | File | Difficoltà |
|---|--------|------|-----------|
| 2.1 | Anti-tamper / anti-debug runtime | Nuovo: `AntiHookDetector.kt` | 🟡 Media |
| 2.2 | Offuscamento stringhe (`SecureStringProtector`) | Nuovo: `SecureStringProtector.kt` | 🟡 Media |
| 2.3 | Cifratura database Room con SQLCipher | Modifica: `DatabaseModule.kt` | 🟠 Alta |
| 2.4 | Ripulire i log di debug dal codebase | Tutti i file | 🟢 Bassa |

### Fase 3 — Media Priorità (Entro 1 mese)

| # | Azione | File | Difficoltà |
|---|--------|------|-----------|
| 3.1 | Configurazione CI/CD con GitHub Actions | Nuovo: `.github/workflows/` | 🟡 Media |
| 3.2 | Keystore gestito in CI (no locale) | Modifica: `build.gradle.kts` | 🟡 Media |
| 3.3 | Spostare logica crypto in codice nativo (JNI) | Nuovo: `cpp/crypto_engine.c` | 🔴 Alta |
| 3.4 | MultiDEX con modulo crittografico separato | Nuovo: `core-module.aar` | 🟠 Alta |

### Fase 4 — Long Term (Entro 3 mesi)

| # | Azione | Descrizione | Difficoltà |
|---|--------|-------------|-----------|
| 4.1 | SafetyNet / Play Integrity API | Rilevare APK modificati e dispositivi rootati | 🟡 Media |
| 4.2 | Emulator Detection | Bloccare esecuzione su emulatori | 🟢 Bassa |
| 4.3 | Certificate Pinning | Per eventuali connessioni future | 🟡 Media |
| 4.4 | Obfuscation con libreria commerciale | (es. DexGuard, Armer) per protezione avanzata | 🟡 Media |

---

## 4. Metriche di Successo

| Metrica | Baseline (attuale) | Target Fase 1 | Target Fase 3 |
|---------|-------------------|----------------|----------------|
| Tempo decompilazione completa | ~15 min (jadx) | ~45 min (R8 aggressivo) | >2 ore (+ nativo) |
| Stringhe sensibili leggibili | ✅ Sì | ❌ No (XOR + Base64) | ❌ No (nativo + runtime) |
| Chiavi in chiaro nel bytecode | ✅ Sì | ❌ No (wiped) | ❌ No (nativo) |
| Tamper detection | ❌ No | ✅ Sì (firma APK) | ✅ Sì (+ runtime) |
| Debug detect | ❌ No | ✅ Sì basico | ✅ Sì avanzato |

---

## 5. Tool di Verifica

Dopo ogni fase, verificare l'efficacia con:

```bash
# 1. Decompilare l'APK e verificare ofuscamento
jadx --output-dir ./decompiled app-release.apk
# Verificare che: i nomi delle classi siano brevi/illeggibili

# 2. Cercare stringhe sensibili nell'APK
strings app-release.apk | grep -iE "(password|key|api|secret|database)"

# 3. Verificare risorse rimosse
apkanalyzer manifest permissions app-release.apk

# 4. Verificare firma
apksigner verify --print-certs app-release.apk

# 5. Verificare assenza di debug code
adb logcat | grep -i "WhatStat"
# Non deve apparire output in release
```

---

## 6. Considerazioni Finali

1. **Nessuna protezione è assoluta**: l'obfuscation può essere sconfitto da un analista esperto con tempo sufficiente. L'obiettivo è rendere l'analisi costosa e dispendiosa.

2. **L'app è 100% offline**: questo è già un vantaggio enorme per la sicurezza. Non ci sono API che possono essere intercettate o endpoint che possono essere attaccati.

3. **Il vero rischio è la distribuzione**: se l'APK viene distribuito al di fuori del Play Store senza App Signing by Play Store, un attaccante potrebbe iniettare codice malevolo. Considerare sempre la pubblicazione tramite canale ufficiale.

4. **Performance trade-off**: tecniche come la cifratura SQLCipher e il codice nativo JNI possono introdurre overhead. Eseguire benchmark prima e dopo ogni modifica.

5. **Manutenibilità**: l'ofuscamento aggressivo può complicare il debugging di crash in produzione. Implementare un sistema di crash reporting che funzioni anche con codice ofuscato (ProGuard mapping upload).