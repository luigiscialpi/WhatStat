# Piano di Protezione del Codice — WhatStat

> **Versione**: 2.0 (rivista: correzioni threat model, scartate difese controproducenti)  
> **Data**: 2026-05-16  
> **Autore**: Luigi Scialpi  
> **Stato**: Rivisto

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
-repackageclasses 'com.whatstat.data.crypto.obf'
-flattenpackagehierarchy 'com.whatstat.data.crypto.obf'

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
-keep class com.whatstat.App { *; }
-keep class com.whatstat.di.** { *; }
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
-keep class com.whatstat.data.db.model.** { *; }

# Hilt
-keep class com.whatstat.data.db.** { *; }
-dontwarn com.whatstat.data.db.**

# Data Classes / Serialization
-keepclassmembers class com.whatstat.domain.model.** { *; }
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

### Livello 2 — Rimozione Log e Stringhe Rivelatrici

**Stato attuale**: ❌ Non implementato  
**Priorità**: Alta

> **NOTA**: La proposta originale `SecureStringProtector` con XOR a chiave fissa
> (`0x42`) e' stata SCARTATA. Una chiave XOR da 1 byte e' banale da reversare:
> chiunque decompili il codice vede `0x42` e decifra tutto in 10 secondi.
> Non aggiunge sicurezza reale, solo complessita' al codice.
>
> L'app non ha API key, token, o stringhe segrete da proteggere con offuscamento.
> L'unico segreto e' la chiave AES di WhatsApp, che viene letta da file a runtime
> e va protetta con wiping dalla memoria (vedi Livello 4).

#### 2.2.1 Rimozione di log e stringhe rivelatrici dal bytecode

L'app contiene ~200 chiamate `android.util.Log.d("WhatStat", ...)` che rivelano
nomi di metodi, flussi interni e struttura del codice.
Aggiungere a `proguard-rules.pro`:

```proguard
# Rimuove TUTTE le chiamate a Log in release
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
```

Questo elimina sia le chiamate che le stringhe argomento dal bytecode finale.

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

> **STATO: SCARTATO** — WhatStat richiede root (Magisk) per funzionare.
> Gli utenti rootati usano legittimamente Xposed/LSPosed/Frida.
> Bloccare questi strumenti significherebbe bloccare il target primario dell'app.
>
> Inoltre:
> - `ps` non funziona su Android 10+ per processi di altre app (Scoped Process)
> - Le porte di Frida sono personalizzabili (il check sulle porte 27042-27046 e' inefficace)
> - Lucky Patcher NON usa Frida: modifica l'APK offline, non fa hooking runtime
>
> La difesa contro il tampering va fatta tramite **signature check** (Livello 6),
> non tramite rilevamento di tool che gli utenti legittimi installano.


---

### Livello 5 — Ofuscamento del DEX e Native Code

**Stato attuale**: ❌ Non implementato  
**Priorità**: Bassa (rivalutata)

#### 2.5.1 MultiDEX con Split

> **STATO: SCARTATO** — Caricare DEX cifrati a runtime e' complesso, fragile su
> device diversi, e non protegge contro un attaccante che semplicemente dumpa
> la memoria. Il rapporto costo/beneficio e' pessimo.
> Inoltre, con il flusso Live DB, la decifratura crypt14 e' solo un fallback
> raramente usato: non vale la pena complicare il codice per proteggerla.

#### 2.5.2 Codice Nativo (C/C++ con JNI)

> **STATO: POSTICIPATO A TEMPO INDETERMINATO** — Spostare la logica crypto in C/C++
> complicherebbe enormemente la manutenzione per un beneficio marginale.
> Con il flusso Live DB (copia diretta senza decifratura), la logica di
> decifratura non e' piu' il percorso principale. Proteggere un fallback
> con JNI non giustifica il costo di sviluppo e debug.
>
> Se in futuro l'app dovesse gestire informazioni premium lato client
> (es. feature gating), rivalutare questa opzione.

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

**Stato attuale**: N/A  
**Priorità**: Bassa (rivalutata)

> **NOTA**: La proposta originale di cifrare "il database Room con SQLCipher"
> e' stata CORRETTA. WhatStat **non usa Room** per i propri dati.
> Il database `msgstore.db` e' una copia del DB di WhatsApp, aperto direttamente
> con `SQLiteDatabase.openDatabase()`. La cache usa DataStore Preferences, non SQLite.
>
> Cifrare `msgstore.db` con SQLCipher sarebbe possibile in teoria, ma:
> - Richiederebbe decifrare e ri-cifrare il DB ad ogni setup (overhead significativo)
> - Il DB e' gia' in `filesDir` (inaccessibile senza root)
> - L'utente ha gia' root, quindi puo' accedere a `filesDir` comunque
>
> Il file e' gia' protetto a sufficienza da `allowBackup=false` e dalla directory
> privata. `secureDelete()` va usata in `cleanup()` per cancellazione sicura.

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

#### 2.8.2 Vulnerabilita' attuale da correggere IMMEDIATAMENTE

> **ATTENZIONE**: Il file `local.properties` contiene le password del keystore in chiaro.
> `local.properties` e' in `.gitignore` e non viene committato, ma le credenziali
> NON devono MAI apparire in documenti di testo come questo piano.

**Azione**: Spostare questi valori in variabili d'ambiente del sistema (`~/.gradle/gradle.properties` con permessi `chmod 600`) o in un gestore di segreti.

---

## 3. Protocollo di Implementazione

> **REGOLA**: L'utente lavora SOLO in release build. Non esiste build debug come safety net.
> Ogni modifica va testata sul device reale in release prima di passare alla successiva.
>
> **DebugLogManager**: L'app ha un sistema di log su file (`whatstat_debug_logs.txt`) indipendente
> da `android.util.Log`. Rimuovere `android.util.Log.*` via ProGuard NON impatta la diagnostica.

### Ordine di implementazione (dal meno rischioso al piu' rischioso)

Ogni step e' atomico: implementa, `assembleRelease`, installa, testa, conferma.
Se uno step causa regressioni, si reverta e si investiga.

#### Step 1: Spostare password keystore in env vars
**Rischio**: ZERO (solo build config)

Cosa fare:
1. Creare `~/.gradle/gradle.properties` con `chmod 600`
2. Spostare `STORE_PASSWORD` e `KEY_PASSWORD` da `local.properties`
3. Aggiornare `build.gradle.kts` per leggere da `gradle.properties`

Test: `./gradlew assembleRelease` compila con successo.

#### Step 2: Usare `secureDelete()` in `cleanup()`
**Rischio**: ZERO (identico a `File.delete()`, solo piu' lento)

Cosa fare: In `FileExtractor.cleanup()`, usare `SecurityUtils.secureDelete()` al posto di `File.delete()`.

Test: Cancella sessione Root, rientra, avvia nuovo setup. Funziona normalmente.

#### Step 3: Rimozione log via ProGuard
**Rischio**: BASSO

Aggiungere a `proguard-rules.pro`:
```proguard
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
```

Perche' sicuro: `DebugLogManager.log()` scrive su file. La `android.util.Log.d` alla riga 56
di `DebugLogManager.kt` e' solo un echo ridondante che verrebbe rimosso. Zero impatto.

Prima di applicare, verificare che nessun argomento Log abbia side-effect:
```bash
grep -rn "Log\.\(d\|w\|e\|i\|v\)(" app/src/ | grep -i "also\|apply\|let\|run"
```

Test: Avvia app, Dashboard, apri chat, filtri, torna indietro. Tutto normale.
DebugLogManager continua a mostrare entry nella schermata log interna.

#### Step 4: Potenziamento R8 (UNA regola alla volta)
**Rischio**: ALTO

Procedere con sotto-step. Ogni sotto-step: build release, installa, testa 5 minuti.

**4a**: Aggiungere SOLO `-repackageclasses ''`
**4b**: Aggiungere `-allowaccessmodification`
**4c**: Aggiungere `-overloadaggressively` (la piu' rischiosa — puo' rompere reflection)
**4d**: Aggiungere `-renamesourcefileattribute "SourceFile"` (cosmetico, basso rischio)

Test per ogni sotto-step:
1. `assembleRelease` compila?
2. L'app si apre senza crash?
3. Setup Root: avvia analisi (o riprendi sessione) — funziona?
4. Dashboard: le statistiche si caricano?
5. Chat detail: apri una chat — dati corretti?
6. Filtri temporali: cambia filtro — nessun crash?
7. Torna alla setup, riprendi sessione — funziona?
8. Se presente: prova anche la modalita' Lite

#### Step 5: Key wiping in KeyReader/Crypt14Decryptor
**Rischio**: MEDIO (solo flusso fallback crypt14, non Live DB)

`SecretKeySpec(key, "AES")` su Android fa copia difensiva del ByteArray.
Wipare l'originale DOPO aver creato il SecretKeySpec e' sicuro:
```
1. keyBytes = readKey()
2. secretKey = SecretKeySpec(keyBytes, "AES")  // copia interna
3. keyBytes.fill(0)                            // wipe originale (sicuro)
4. cipher.init(DECRYPT_MODE, secretKey)
5. cipher.doFinal(...)
```

Test: Cancella sessione Root, riesegui setup completo. Verifica che la decifratura
funzioni (nel caso il flusso Live DB non sia disponibile).

Nota: Con il flusso Live DB, questo codice viene eseguito raramente (solo fallback).

#### Step 6: Verifica firma APK (anti-Lucky Patcher)
**Rischio**: MEDIO-ALTO

Cosa fare:
1. Calcolare hash SHA-256 del certificato release:
   `keytool -list -v -keystore whatstat-keystore.jks | grep SHA256`
2. Creare `IntegrityChecker.kt`
3. In `App.onCreate()`, chiamare il check

**CRITICO: NON bloccare l'app se la firma non corrisponde.**
Loggare via `DebugLogManager` e impostare un flag consultabile dalle feature premium.
Se l'hash e' sbagliato per errore nostro, l'app resterebbe utilizzabile.

Attenzione Play App Signing: Google ri-firma l'APK. Opzioni:
- Accettare entrambi gli hash (locale + Play)
- Saltare il check se l'installer e' `com.android.vending`

Test: Installa release firmata. L'app parte normalmente. Se possibile, ri-firma
un APK con chiave diversa (apktool) e verifica che il flag tampering si attivi.

### Azioni SCARTATE (con motivazione)

| Azione proposta | Motivo dello scarto |
|----------------|--------------------
| AntiHookDetector (Frida/Xposed) | L'app richiede root; bloccare tool root = bloccare i propri utenti |
| SecureStringProtector (XOR 0x42) | Chiave XOR 1 byte: sicurezza illusoria, complessita' reale |
| SQLCipher su Room | L'app non usa Room; il DB e' gia' in filesDir protetto |
| MultiDEX dinamico | Overkill, fragile, aggirabile con memory dump |
| Codice nativo JNI | Costo altissimo per proteggere un fallback (crypt14) raramente usato |
| SafetyNet / Play Integrity | L'app e' 100% offline, non puo' chiamare server Google |
| Emulator Detection | Utenti legittimi usano emulatori rootati per test |

---

## 4. Metriche di Successo

| Metrica | Baseline (attuale) | Target Fase 1 | Target Fase 2 |
|---------|-------------------|----------------|----------------|
| Tempo decompilazione completa | ~15 min (jadx) | ~45 min (R8 aggressivo) | ~1 ora (+ dispersione logica) |
| Log rivelatrici nel bytecode | ~200 chiamate | 0 (assumenosideeffects) | 0 |
| Chiavi in chiaro in memoria | Si' (mai cancellate) | No (wiped dopo uso) | No |
| Tamper detection (Lucky Patcher) | No | Si' (firma APK) | Si' (+ dispersione) |
| Debug certificate detection | No | Si' (basico) | Si' |

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

1. **Nessuna protezione e' assoluta**: l'obfuscation puo' essere sconfitto da un analista esperto con tempo sufficiente. L'obiettivo e' rendere l'analisi costosa e dispendiosa.

2. **L'app e' 100% offline**: questo e' gia' un vantaggio enorme per la sicurezza. Non ci sono API che possono essere intercettate o endpoint che possono essere attaccati.

3. **L'app gira su device rootati per design**: questo vincola le difese disponibili. Non possiamo bloccare Frida, Xposed, o rilevare root — i nostri utenti li hanno per definizione. La difesa deve concentrarsi sul **tampering dell'APK** (Lucky Patcher), non sull'ambiente di esecuzione.

4. **Lucky Patcher opera offline**: modifica il DEX dell'APK cercando pattern noti (`isPremium()`, `isLicensed()`, `verifyPurchase()`). Le difese efficaci sono: (a) signature check per rilevare APK ripacchettizzati, (b) offuscamento R8 per rendere i pattern irriconoscibili, (c) dispersione della logica premium in piu' punti con nomi offuscati.

5. **Il flusso Live DB ha cambiato il threat model**: con la copia diretta del database live, la decifratura crypt14 e' solo un fallback. Proteggere la logica di decifratura con codice nativo o MultiDEX ha un rapporto costo/beneficio non giustificabile.

6. **Manutenibilita'**: l'offuscamento aggressivo puo' complicare il debugging di crash in produzione. Conservare sempre il mapping file di ProGuard per ogni release.