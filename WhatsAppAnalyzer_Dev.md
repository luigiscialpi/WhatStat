# WhatStat Reborn — Guida completa allo sviluppo

> App Android nativa in Kotlin che legge, decifra e analizza il database di WhatsApp su dispositivi rootati (crypt14, backup E2E disattivato).

---

## Indice

1. [Panoramica del progetto](#1-panoramica-del-progetto)
2. [Prerequisiti e setup](#2-prerequisiti-e-setup)
3. [Struttura del progetto](#3-struttura-del-progetto)
4. [Livello 1 — Root Access](#4-livello-1--root-access)
5. [Livello 2 — Decryption Engine](#5-livello-2--decryption-engine)
6. [Livello 3 — Database Layer](#6-livello-3--database-layer)
7. [Livello 4 — Analytics Engine](#7-livello-4--analytics-engine)
8. [Livello 5 — UI con Jetpack Compose](#8-livello-5--ui-con-jetpack-compose)
9. [Architettura MVVM completa](#9-architettura-mvvm-completa)
10. [Gestione degli errori](#10-gestione-degli-errori)
11. [Sicurezza e privacy](#11-sicurezza-e-privacy)
12. [Testing](#12-testing)
13. [Roadmap e feature future](#13-roadmap-e-feature-future)
14. [Riferimenti e risorse](#14-riferimenti-e-risorse)

---

## 1. Panoramica del progetto

### Obiettivo

Creare un'app Android che, su dispositivo rootato, acceda direttamente al database cifrato di WhatsApp, lo decifri in locale e produca statistiche dettagliate sulle conversazioni — senza mai inviare dati a server esterni.

### Stack tecnologico

| Componente | Tecnologia |
|---|---|
| Linguaggio | Kotlin 1.9+ |
| UI | Jetpack Compose + Material 3 |
| Root access | libsu 5.x |
| Crittografia | javax.crypto (AES-GCM, built-in Android) |
| Database | SQLite diretto + Room (opzionale) |
| Grafici | Vico (Compose-native) |
| Architettura | MVVM + Repository pattern |
| DI | Hilt |
| Async | Kotlin Coroutines + Flow |
| Build | Gradle KTS |

### Flusso generale

```
Dispositivo rootato
       |
       v
[libsu] Copia file protetti
  - /data/data/com.whatsapp/files/key
  - /sdcard/WhatsApp/Databases/msgstore.db.crypt14
  - /data/data/com.whatsapp/databases/wa.db
       |
       v
[Decryption Engine] Decifra crypt14
  Legge header → estrae IV + salt → AES-GCM → msgstore.db
       |
       v
[Database Layer] Legge SQLite
  Tabelle: message, chat, jid + JOIN con wa.db
       |
       v
[Analytics Engine] Calcola statistiche
  Query aggregate SQL → modelli dati
       |
       v
[UI Compose] Mostra risultati
  Dashboard → Dettaglio chat → Grafici
```

---

## 2. Prerequisiti e setup

### Ambiente di sviluppo

- Android Studio Hedgehog o superiore
- Android SDK 34 (target), minSdk 26 (Android 8.0)
- Dispositivo fisico rootato con Magisk (o emulatore con root — sconsigliato per test reali)
- WhatsApp installato con backup E2E **disattivato** (formato crypt14)

### Verifica del formato del backup

Prima di sviluppare, verifica il formato del tuo backup:

```bash
# Da shell root sul dispositivo
ls -la /sdcard/WhatsApp/Databases/
# Deve mostrare msgstore.db.crypt14
# Se mostra msgstore.db.crypt15 → backup E2E attivo → approccio diverso
```

### build.gradle.kts (app)

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.tuonome.whatsappanalyzer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tuonome.whatsappanalyzer"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Root access
    implementation("com.github.topjohnwu.libsu:core:5.2.2")
    implementation("com.github.topjohnwu.libsu:io:5.2.2")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.50")
    ksp("com.google.dagger:hilt-android-compiler:2.50")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // Room (per la lettura del db decifrato)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Grafici
    implementation("com.patrykandpatrick.vico:compose-m3:1.13.1")
    implementation("com.patrykandpatrick.vico:core:1.13.1")

    // DataStore (preferenze app)
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Permessi Compose
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")

    // Test
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
}
```

### settings.gradle.kts

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") } // per libsu
    }
}
```

### AndroidManifest.xml — permessi necessari

```xml
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"/>
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="28"/>
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE"/>

<!-- Dichiara che l'app richiede root (non tecnico, solo informativo) -->
<uses-feature android:name="android.hardware.root" android:required="false"/>
```

---

## 3. Struttura del progetto

```
app/src/main/
├── java/com/tuonome/whatsappanalyzer/
│   ├── MainActivity.kt
│   ├── App.kt                          ← Application class (Hilt)
│   │
│   ├── data/
│   │   ├── root/
│   │   │   ├── RootManager.kt          ← libsu wrapper
│   │   │   └── FileExtractor.kt        ← copia file protetti
│   │   │
│   │   ├── crypto/
│   │   │   ├── Crypt14Decryptor.kt     ← motore di decifratura
│   │   │   └── KeyReader.kt            ← lettura e parsing della key
│   │   │
│   │   ├── db/
│   │   │   ├── WhatsAppDatabase.kt     ← SQLiteOpenHelper custom
│   │   │   ├── dao/
│   │   │   │   ├── MessageDao.kt
│   │   │   │   ├── ChatDao.kt
│   │   │   │   └── ContactDao.kt
│   │   │   └── model/
│   │   │       ├── MessageEntity.kt
│   │   │       ├── ChatEntity.kt
│   │   │       ├── JidEntity.kt
│   │   │       └── ContactEntity.kt
│   │   │
│   │   └── repository/
│   │       ├── WhatsAppRepository.kt   ← orchestratore principale
│   │       └── AnalyticsRepository.kt  ← statistiche
│   │
│   ├── domain/
│   │   ├── model/
│   │   │   ├── ChatStats.kt
│   │   │   ├── ContactStats.kt
│   │   │   ├── GlobalStats.kt
│   │   │   ├── HourlyActivity.kt
│   │   │   ├── WordFrequency.kt
│   │   │   └── MediaStats.kt
│   │   │
│   │   └── usecase/
│   │       ├── ExtractDatabaseUseCase.kt
│   │       ├── DecryptDatabaseUseCase.kt
│   │       ├── GetGlobalStatsUseCase.kt
│   │       ├── GetChatStatsUseCase.kt
│   │       └── GetContactRankingUseCase.kt
│   │
│   └── ui/
│       ├── theme/
│       │   ├── Theme.kt
│       │   ├── Color.kt
│       │   └── Type.kt
│       │
│       ├── navigation/
│       │   └── AppNavigation.kt
│       │
│       ├── screen/
│       │   ├── setup/
│       │   │   ├── SetupScreen.kt      ← prima apertura, richiesta root
│       │   │   └── SetupViewModel.kt
│       │   │
│       │   ├── dashboard/
│       │   │   ├── DashboardScreen.kt  ← panoramica globale
│       │   │   └── DashboardViewModel.kt
│       │   │
│       │   ├── chatlist/
│       │   │   ├── ChatListScreen.kt   ← lista chat con ranking
│       │   │   └── ChatListViewModel.kt
│       │   │
│       │   └── chatdetail/
│       │       ├── ChatDetailScreen.kt ← statistiche singola chat
│       │       └── ChatDetailViewModel.kt
│       │
│       └── component/
│           ├── StatCard.kt
│           ├── HourlyHeatmap.kt
│           ├── WordCloudView.kt
│           ├── BarChartCard.kt
│           └── TimelineChart.kt
│
└── res/
    ├── values/
    │   ├── strings.xml
    │   └── themes.xml
    └── drawable/
        └── ic_launcher.xml
```

---

## 4. Livello 1 — Root Access

### Concetti chiave

libsu è la libreria Android più usata e affidabile per eseguire comandi shell con privilegi root. Mantiene una shell root persistente per tutta la sessione, evitando il costo di aprire una nuova shell a ogni operazione.

### App.kt — inizializzazione libsu

```kotlin
@HiltAndroidApp
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        // Imposta la shell root come shell di default per tutta l'app
        Shell.enableVerboseLogging = BuildConfig.DEBUG
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(10)
        )
    }
}
```

### RootManager.kt

```kotlin
class RootManager @Inject constructor() {

    /**
     * Verifica se il dispositivo ha root disponibile e accessibile.
     * Da chiamare prima di qualsiasi operazione privilegiata.
     */
    suspend fun checkRootAccess(): RootStatus = withContext(Dispatchers.IO) {
        val shell = Shell.getShell()
        return@withContext when {
            !shell.isAlive -> RootStatus.NOT_AVAILABLE
            !shell.isRoot  -> RootStatus.NOT_GRANTED
            else           -> RootStatus.GRANTED
        }
    }

    /**
     * Esegue un comando root e restituisce l'output.
     */
    suspend fun execute(command: String): ShellResult = withContext(Dispatchers.IO) {
        val result = Shell.cmd(command).exec()
        ShellResult(
            success = result.isSuccess,
            output  = result.out,
            error   = result.err
        )
    }
}

enum class RootStatus { GRANTED, NOT_GRANTED, NOT_AVAILABLE }

data class ShellResult(
    val success: Boolean,
    val output: List<String>,
    val error: List<String>
)
```

### FileExtractor.kt

```kotlin
class FileExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val rootManager: RootManager
) {
    // Directory privata dell'app, accessibile solo a noi
    private val workDir = context.filesDir

    companion object {
        const val KEY_PATH          = "/data/data/com.whatsapp/files/key"
        const val CRYPT14_PATH      = "/sdcard/WhatsApp/Databases/msgstore.db.crypt14"
        const val WA_DB_PATH        = "/data/data/com.whatsapp/databases/wa.db"

        const val KEY_DEST          = "whatsapp.key"
        const val CRYPT14_DEST      = "msgstore.db.crypt14"
        const val WA_DB_DEST        = "wa.db"
    }

    /**
    suspend fun extractAll(): Result<ExtractedFiles> = withContext(Dispatchers.IO) {
        runCatching {
            copyWithRoot(KEY_PATH,     File(workDir, KEY_DEST))
            copyWithRoot(CRYPT14_PATH, File(workDir, CRYPT14_DEST))
            copyWithRoot(WA_DB_PATH,   File(workDir, WA_DB_DEST))

            ExtractedFiles(
                keyFile     = File(workDir, KEY_DEST),
                crypt14File = File(workDir, CRYPT14_DEST),
                waDbFile    = File(workDir, WA_DB_DEST)
            )
        }
    }

    private suspend fun copyWithRoot(sourcePath: String, dest: File) {
        val result = rootManager.execute("cat '$sourcePath' > '${dest.absolutePath}'")
        if (!result.success) {
            throw IOException("Errore copia $sourcePath: ${result.error.joinToString()}")
        }
        rootManager.execute("chmod 644 '${dest.absolutePath}'")
    }

    suspend fun copyFromUri(uri: Uri, destFileName: String): File = withContext(Dispatchers.IO) {
        val destFile = File(workDir, destFileName)
        if (destFile.exists() && !destFile.delete()) {
            rootManager.execute("rm -f '${destFile.absolutePath}'")
        }
        context.contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IOException("Fallback fallito per $uri")
        destFile
    }
    suspend fun detectCryptVersion(crypt14File: File): Int = withContext(Dispatchers.IO) {
        crypt14File.inputStream().use { stream ->
            val header = ByteArray(15)
            stream.read(header)
            // I primi byte del file protobuf contengono la versione di cifratura
            // crypt14: header contiene 0x0A seguito dal campo versione = 14
            // Analisi semplificata: cerchiamo il valore 14 nella posizione attesa
            return@withContext if (header.contains(14.toByte())) 14 else -1
        }
    }
}

data class ExtractedFiles(
    val keyFile: File,
    val crypt14File: File,
    val waDbFile: File
)
```

---

## 5. Livello 2 — Decryption Engine

> Implementazione basata sull'analisi del codice sorgente di
> [wa-crypt-tools](https://github.com/ElDavoo/wa-crypt-tools) (ElDavoo).
> Testata con WhatsApp 2.24.16.76 stable e 2.24.26.11 beta.

### Concetti fondamentali

Il file `msgstore.db.crypt14` e il file `key` NON sono dati raw.
Entrambi usano formati strutturati che vanno parsati correttamente:

- **File key**: e un oggetto Java serializzato (`java.io.ObjectOutputStream`), 
  non un semplice array di byte.
- **File .crypt14**: ha un header protobuf di dimensione variabile, 
  non un header a offset fisso.

### Struttura del file key (crypt14)

Il file `/data/data/com.whatsapp/files/key` e un Java Serialized `byte[]`.
Dopo la deserializzazione con `ObjectInputStream`, si ottiene un array di **131 byte**
con questo layout:

```
Offset  Lunghezza  Campo
------  ---------  -----
0-1     2 byte     cipher_version     (atteso: 0x0001)
2       1 byte     key_version        (supportati: 0x01, 0x02, 0x03)
3-34    32 byte    server_salt
35-50   16 byte    googleIdSalt
51-82   32 byte    hashedGoogleID     (= SHA-256 di googleIdSalt)
83-98   16 byte    padding            (sempre zero, IV placeholder)
99-130  32 byte    cipherKey          <-- LA CHIAVE AES-256 EFFETTIVA
```

> **ATTENZIONE**: la chiave AES e all'offset **99 dell'array deserializzato**,
> NON all'offset 126 del file raw. L'overhead della serializzazione Java e variabile.

### Struttura del file .crypt14

Il file cifrato ha questa struttura:

```
+------+------+---------------------------+---------------------+-----------+----------+
| 1 B  | 0/1B | N byte (variabile)        | M byte              | 16 byte   | 16 byte  |
| size | feat | Protobuf BackupPrefix     | Dati cifrati        | Auth Tag  | Checksum |
|      | flag |                           | (AES-GCM)           | (GCM)     | (MD5)    |
+------+------+---------------------------+---------------------+-----------+----------+
```

L'header e un messaggio Protobuf di tipo `BackupPrefix` che contiene:
- `key_type`: 0 = WA_PROVIDED (crypt14), 1 = HSM_CONTROLLED (crypt15)
- `c14_cipher.IV`: l'Initialization Vector per AES-GCM (**16 byte**, non 12)
- `c14_cipher.server_salt`, `c14_cipher.google_id`, ecc.
- `info`: metadati (versione WhatsApp, feature flags, jid suffix)

Il footer (ultimi 32 byte) contiene:
- **Authentication Tag** GCM (16 byte) 
- **MD5 checksum** di tutto il file precedente (16 byte)

### KeyReader.kt

```kotlin
class KeyReader @Inject constructor() {

    companion object {
        // Layout dell'array deserializzato (131 byte per crypt14)
        // Fonte: wa-crypt-tools/src/wa_crypt_tools/lib/key/key14.py
        private const val CRYPT14_KEY_ARRAY_SIZE = 131
        private const val CRYPT15_KEY_ARRAY_SIZE = 32
        private const val AES_KEY_OFFSET = 99
        private const val AES_KEY_LENGTH = 32
        private const val SERVER_SALT_OFFSET = 3
        private const val SERVER_SALT_LENGTH = 32
        private const val GOOGLE_ID_OFFSET = 35
        private const val GOOGLE_ID_LENGTH = 16
    }

    /**
     * Risultato della lettura del file key.
     * Contiene la chiave AES e il tipo di formato rilevato.
     */
    data class KeyData(
        val aesKey: ByteArray,
        val keyType: KeyType
    ) {
        enum class KeyType { CRYPT14, CRYPT15 }
    }

    /**
     * Legge il file key di WhatsApp deserializzandolo come Java Object.
     *
     * Il file key e un oggetto Java serializzato (byte[]).
     * - Se l'array deserializzato e 131 byte -> crypt14
     *   La chiave AES-256 e all'offset 99 (32 byte).
     * - Se l'array deserializzato e 32 byte -> crypt15
     *   E la root key (richiede derivazione HMAC ulteriore).
     *
     * Fonte: wa-crypt-tools keyfactory.py + key14.py
     */
    fun readKey(keyFile: File): KeyData {
        val keyArray: ByteArray = try {
            ObjectInputStream(keyFile.inputStream().buffered()).use { ois ->
                ois.readObject() as ByteArray
            }
        } catch (e: Exception) {
            throw IllegalStateException(
                "Impossibile deserializzare il file key come Java Object: ${e.message}", e
            )
        }

        return when (keyArray.size) {
            CRYPT14_KEY_ARRAY_SIZE -> {
                // Validazione cipher_version (primi 2 byte devono essere 0x0001)
                val cipherVersion = keyArray.copyOfRange(0, 2)
                if (!cipherVersion.contentEquals(byteArrayOf(0x00, 0x01))) {
                    throw IllegalStateException(
                        "Versione cipher non supportata: ${cipherVersion.toHex()}"
                    )
                }
                // Validazione key_version (byte 2, supportati: 0x01, 0x02, 0x03)
                val keyVersion = keyArray[2]
                if (keyVersion !in 1..3) {
                    throw IllegalStateException(
                        "Versione key non supportata: $keyVersion"
                    )
                }
                // Estrazione chiave AES-256 dall'offset 99
                val aesKey = keyArray.copyOfRange(AES_KEY_OFFSET, AES_KEY_OFFSET + AES_KEY_LENGTH)
                KeyData(aesKey, KeyData.KeyType.CRYPT14)
            }

            CRYPT15_KEY_ARRAY_SIZE -> {
                // La root key per crypt15 (richiede derivazione HMAC)
                KeyData(keyArray, KeyData.KeyType.CRYPT15)
            }

            else -> throw IllegalStateException(
                "Formato key non riconosciuto: ${keyArray.size} byte " +
                "(attesi $CRYPT14_KEY_ARRAY_SIZE per crypt14 o $CRYPT15_KEY_ARRAY_SIZE per crypt15)"
            )
        }
    }

    /**
     * Per crypt15: deriva la chiave di cifratura dalla root key
     * usando HMAC-SHA256 con il messaggio "backup encryption".
     *
     * Fonte: wa-crypt-tools key15.py, metodo get()
     */
    fun deriveEncryptionKey(rootKey: ByteArray): ByteArray {
        // Step 1: privatekey = HMAC-SHA256(seed=0x00*32, data=rootKey)
        val seed = ByteArray(32) // 32 byte di zeri
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(seed, "HmacSHA256"))
        val privateKey = mac.doFinal(rootKey)

        // Step 2: encryptionKey = HMAC-SHA256(privateKey, data="" + "backup encryption" + 0x01)
        val mac2 = Mac.getInstance("HmacSHA256")
        mac2.init(SecretKeySpec(privateKey, "HmacSHA256"))
        mac2.update("backup encryption".toByteArray(Charsets.US_ASCII))
        mac2.update(byteArrayOf(0x01))
        return mac2.doFinal()
    }

    /**
     * Restituisce la chiave come stringa esadecimale (utile per debug).
     * NON usare in produzione: non loggare mai la chiave.
     */
    fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }
}
```

### Crypt14Decryptor.kt

```kotlin
class Crypt14Decryptor @Inject constructor(
    private val keyReader: KeyReader
) {
    companion object {
        private const val CIPHER_ALGO    = "AES/GCM/NoPadding"
        private const val IV_LENGTH      = 16       // WhatsApp usa IV di 16 byte, non 12
        private const val AUTH_TAG_BITS  = 128      // 16 byte di authentication tag
        private const val FOOTER_SIZE    = 32       // auth_tag (16) + md5 checksum (16)
        private const val BUFFER_SIZE    = 16384    // 16KB per streaming
    }

    /**
     * Decifra un file .crypt14 (o .crypt15) usando la chiave dal file key.
     *
     * Algoritmo (basato su wa-crypt-tools wadecrypt.py):
     * 1. Deserializza il file key -> estrai chiave AES-256
     * 2. Parsa l'header protobuf del file cifrato -> estrai IV (16 byte)
     * 3. Separa dati cifrati dal footer (auth tag + checksum)
     * 4. Decifra con AES-GCM(key, iv)
     * 5. Decompri con zlib (standard, CON header)
     * 6. Verifica output: deve iniziare con "SQLite format 3"
     */
    suspend fun decrypt(
        crypt14File: File,
        keyFile: File,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            // -- Step 1: Carica e deserializza la chiave --
            val keyData = keyReader.readKey(keyFile)
            val aesKey: ByteArray = when (keyData.keyType) {
                KeyReader.KeyData.KeyType.CRYPT14 -> keyData.aesKey
                KeyReader.KeyData.KeyType.CRYPT15 -> keyReader.deriveEncryptionKey(keyData.aesKey)
            }
            val secretKey = SecretKeySpec(aesKey, "AES")

            // -- Step 2: Parsa l'header protobuf ed estrai l'IV --
            val allBytes = crypt14File.readBytes()
            val headerParseResult = parseProtobufHeader(allBytes)

            // -- Step 3: Separa dati cifrati dal footer --
            // I dati cifrati iniziano subito dopo l'header protobuf.
            // Gli ultimi 32 byte sono: auth_tag (16) + md5_checksum (16).
            val dataStart = headerParseResult.dataOffset
            val dataEnd = allBytes.size - FOOTER_SIZE
            if (dataEnd <= dataStart) {
                throw DecryptionException("File cifrato troppo piccolo o corrotto")
            }
            val encryptedData = allBytes.copyOfRange(dataStart, dataEnd)
            val authTag = allBytes.copyOfRange(dataEnd, dataEnd + 16)
            // val md5Checksum = allBytes.copyOfRange(dataEnd + 16, allBytes.size)

            // -- Step 4: Decifra con AES-GCM --
            // In Java, GCM si aspetta l'auth tag concatenato ai dati cifrati
            val ciphertextWithTag = encryptedData + authTag
            val cipher = Cipher.getInstance(CIPHER_ALGO)
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey,
                GCMParameterSpec(AUTH_TAG_BITS, headerParseResult.iv)
            )
            val decrypted = cipher.doFinal(ciphertextWithTag)

            // -- Step 5: Decomprimi con zlib (standard, CON header) --
            // WhatsApp comprime con zlib (header 78 01), NON raw deflate.
            // Inflater() SENZA il parametro "true" (che indica raw deflate).
            val inflater = Inflater()
            inflater.setInput(decrypted)
            val bos = ByteArrayOutputStream(decrypted.size * 2)
            val buffer = ByteArray(BUFFER_SIZE)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0 && inflater.needsInput()) break
                bos.write(buffer, 0, count)
            }
            inflater.end()
            val decompressed = bos.toByteArray()

            // -- Step 6: Verifica output --
            if (decompressed.size < 16) {
                throw DecryptionException(
                    "Output decompresso troppo piccolo: ${decompressed.size} byte"
                )
            }
            val magic = String(decompressed, 0, 15, Charsets.US_ASCII)
            if (magic != "SQLite format 3") {
                throw DecryptionException(
                    "Output non e un database SQLite valido (magic: '$magic')"
                )
            }

            outputFile.writeBytes(decompressed)
            outputFile
        }
    }

    /**
     * Risultato del parsing dell'header protobuf.
     */
    data class HeaderParseResult(
        val iv: ByteArray,
        val dataOffset: Int
    )

    /**
     * Parsa l'header protobuf di un file .crypt14/.crypt15.
     *
     * Formato dell'header (fonte: wa-crypt-tools dbfactory.py):
     * - Byte 0: dimensione del messaggio protobuf (1 byte)
     * - Byte 1 (opzionale): flag feature table (0x01 se presente)
     * - Byte 1/2..N: messaggio protobuf BackupPrefix
     *
     * L'IV viene estratto dal campo c14_cipher.IV (crypt14)
     * o c15_iv.IV (crypt15) del messaggio protobuf.
     */
    private fun parseProtobufHeader(data: ByteArray): HeaderParseResult {
        var offset = 0

        // Byte 0: dimensione del messaggio protobuf
        val protobufSize = data[offset].toInt() and 0xFF
        offset++

        // Byte 1: verifica se c'e il feature flag
        if (data[offset].toInt() and 0xFF == 0x01) {
            offset++ // Consuma il feature flag
        }

        // Leggi il messaggio protobuf raw
        val protobufRaw = data.copyOfRange(offset, offset + protobufSize)
        offset += protobufSize

        // Parsa il protobuf per estrarre l'IV.
        // Usiamo un parser minimale per estrarre il campo IV
        // senza dipendere dalla libreria protobuf completa.
        val iv = extractIvFromProtobuf(protobufRaw)
            ?: throw DecryptionException("Impossibile estrarre IV dal header protobuf")

        if (iv.size != IV_LENGTH) {
            throw DecryptionException(
                "IV ha lunghezza ${iv.size} byte, attesi $IV_LENGTH"
            )
        }

        return HeaderParseResult(iv = iv, dataOffset = offset)
    }

    /**
     * Estrae il campo IV dal messaggio protobuf BackupPrefix.
     *
     * Struttura protobuf BackupPrefix:
     *   field 1 (key_type): varint
     *   field 2 (c14_cipher): messaggio annidato C14_cipher
     *     -> field 5 (IV): bytes (16 byte)
     *   field 3 (c15_iv): messaggio annidato C15_IV
     *     -> field 1 (IV): bytes (16 byte)
     *
     * Questo parser minimale evita la dipendenza dalla libreria protobuf.
     * Per i dettagli del formato wire protobuf, vedi:
     * https://protobuf.dev/programming-guides/encoding/
     */
    private fun extractIvFromProtobuf(data: ByteArray): ByteArray? {
        // Cerca il campo IV nei sotto-messaggi c14_cipher o c15_iv.
        // Per crypt14: il campo 2 (c14_cipher) contiene il sotto-campo 5 (IV).
        // Per crypt15: il campo 3 (c15_iv) contiene il sotto-campo 1 (IV).
        //
        // Parser protobuf wire format minimale:
        // Ogni campo e preceduto da un "tag" = (field_number << 3) | wire_type
        // wire_type 0 = varint, 2 = length-delimited (bytes/string/embedded message)

        var pos = 0
        while (pos < data.size) {
            val tag = readVarint(data, pos)
            pos = tag.second
            val fieldNumber = (tag.first ushr 3).toInt()
            val wireType = (tag.first and 0x07).toInt()

            when (wireType) {
                0 -> {
                    // Varint: leggi e ignora
                    val v = readVarint(data, pos)
                    pos = v.second
                }
                2 -> {
                    // Length-delimited: leggi lunghezza e contenuto
                    val lenResult = readVarint(data, pos)
                    val len = lenResult.first.toInt()
                    pos = lenResult.second
                    val fieldData = data.copyOfRange(pos, pos + len)
                    pos += len

                    when (fieldNumber) {
                        2 -> {
                            // c14_cipher: parsa il sotto-messaggio per estrarre field 5 (IV)
                            val iv = extractFieldFromSubmessage(fieldData, targetField = 5)
                            if (iv != null) return iv
                        }
                        3 -> {
                            // c15_iv: parsa il sotto-messaggio per estrarre field 1 (IV)
                            val iv = extractFieldFromSubmessage(fieldData, targetField = 1)
                            if (iv != null) return iv
                        }
                    }
                }
                else -> {
                    // Altri wire type non attesi nell'header, interrompi
                    break
                }
            }
        }
        return null
    }

    /**
     * Estrae un campo bytes (length-delimited) da un sotto-messaggio protobuf.
     */
    private fun extractFieldFromSubmessage(data: ByteArray, targetField: Int): ByteArray? {
        var pos = 0
        while (pos < data.size) {
            val tag = readVarint(data, pos)
            pos = tag.second
            val fieldNumber = (tag.first ushr 3).toInt()
            val wireType = (tag.first and 0x07).toInt()

            when (wireType) {
                0 -> {
                    val v = readVarint(data, pos)
                    pos = v.second
                }
                2 -> {
                    val lenResult = readVarint(data, pos)
                    val len = lenResult.first.toInt()
                    pos = lenResult.second
                    val fieldData = data.copyOfRange(pos, pos + len)
                    pos += len
                    if (fieldNumber == targetField) return fieldData
                }
                else -> break
            }
        }
        return null
    }

    /**
     * Legge un varint protobuf dal buffer alla posizione data.
     * Restituisce il valore e la nuova posizione nel buffer.
     */
    private fun readVarint(data: ByteArray, startPos: Int): Pair<Long, Int> {
        var result = 0L
        var shift = 0
        var pos = startPos
        while (pos < data.size) {
            val b = data[pos].toInt() and 0xFF
            result = result or ((b.toLong() and 0x7F) shl shift)
            pos++
            if (b and 0x80 == 0) break
            shift += 7
        }
        return Pair(result, pos)
    }
}

class DecryptionException(message: String) : Exception(message)
```

> **Note implementative:**
> - L'implementazione sopra carica l'intero file in memoria (`readBytes()`).
>   Per backup molto grandi (> 100 MB), considerare un approccio streaming con
>   chunk di dati, come fa `chunked_decrypt()` in wa-crypt-tools.
> - Il parser protobuf e minimale e gestisce solo i campi necessari.
>   Se si preferisce robustezza, si possono compilare i file `.proto` di wa-crypt-tools
>   con `protoc --java_out` e usare le classi generate.
> - La verifica MD5 del checksum nel footer e opzionale ma consigliata
>   per rilevare corruzioni del file.

### Test della decifratura

```kotlin
// Test rapido da eseguire prima di procedere con il resto
class DecryptorTest {
    @Test
    fun `decifratura produce un file SQLite valido`() = runTest {
        val keyFile    = File("test_assets/key")
        val cryptFile  = File("test_assets/msgstore.db.crypt14")
        val outputFile = File.createTempFile("msgstore", ".db")

        val decryptor = Crypt14Decryptor(KeyReader())
        val result = decryptor.decrypt(cryptFile, keyFile, outputFile)

        assertTrue(result.isSuccess)

        // I primi 16 byte di un SQLite valido sono sempre "SQLite format 3\000"
        val magic = outputFile.readBytes().take(16)
        val expected = "SQLite format 3\u0000".toByteArray()
        assertArrayEquals(expected, magic.toByteArray())
    }

    @Test
    fun `chiave sbagliata genera errore di decifratura`() = runTest {
        // Crea un file key fake: un Java Serialized byte[131] con contenuto random
        val wrongKeyFile = createFakeKeyFile()
        val cryptFile    = File("test_assets/msgstore.db.crypt14")
        val outputFile   = File.createTempFile("output", ".db")

        val decryptor = Crypt14Decryptor(KeyReader())
        val result = decryptor.decrypt(cryptFile, wrongKeyFile, outputFile)
        assertTrue(result.isFailure)
    }

    @Test
    fun `il KeyReader distingue crypt14 da crypt15`() {
        val keyReader = KeyReader()

        // File key crypt14 (131 byte dopo deserializzazione)
        val key14 = keyReader.readKey(File("test_assets/key"))
        assertEquals(KeyReader.KeyData.KeyType.CRYPT14, key14.keyType)
        assertEquals(32, key14.aesKey.size)

        // File key crypt15 (32 byte dopo deserializzazione)
        val key15 = keyReader.readKey(File("test_assets/encrypted_backup.key"))
        assertEquals(KeyReader.KeyData.KeyType.CRYPT15, key15.keyType)
        assertEquals(32, key15.aesKey.size)
    }
}

---

## 6. Livello 3 — Database Layer

### Schema del database msgstore.db (versione moderna, WhatsApp 2.23+)

Le versioni recenti di WhatsApp hanno separato i dati in più tabelle rispetto al vecchio schema monolitico. Le tabelle principali sono:

```sql
-- Tabella principale messaggi
CREATE TABLE message (
    _id                     INTEGER PRIMARY KEY AUTOINCREMENT,
    chat_row_id             INTEGER NOT NULL,   -- FK → chat._id
    from_me                 INTEGER NOT NULL,   -- 0 = ricevuto, 1 = inviato
    key_id                  TEXT NOT NULL,
    sender_jid_row_id       INTEGER,            -- FK → jid._id (null se from_me=1)
    status                  INTEGER,            -- 0=attesa, 4=consegnato, 5=letto
    broadcast               INTEGER,
    timestamp               INTEGER,            -- Unix timestamp milliseconds (UTC)
    received_timestamp      INTEGER,
    message_type            INTEGER,            -- 0=testo, 1=foto, 2=audio, 3=video
    text_data               TEXT,               -- contenuto testuale
    starred                 INTEGER             -- 1 = messaggio con stella
);

-- Tabella chat (una riga per ogni conversazione)
CREATE TABLE chat (
    _id                     INTEGER PRIMARY KEY AUTOINCREMENT,
    jid_row_id              INTEGER NOT NULL,   -- FK → jid._id
    hidden                  INTEGER,
    subject                 TEXT,               -- nome del gruppo (null per chat 1:1)
    created_timestamp       INTEGER,
    display_message_row_id  INTEGER,
    last_message_row_id     INTEGER,
    unseen_message_count    INTEGER
);

-- Tabella identificatori WhatsApp (JID = phone@s.whatsapp.net)
CREATE TABLE jid (
    _id                     INTEGER PRIMARY KEY AUTOINCREMENT,
    user                    TEXT,               -- numero di telefono
    server                  TEXT,               -- "s.whatsapp.net" o "g.us" (gruppi)
    agent                   INTEGER,
    device                  INTEGER,
    raw_string              TEXT,               -- "3391234567@s.whatsapp.net"
    cached_name             TEXT                -- nome salvato (spesso null → usa wa.db)
);

-- Tabella media allegati
CREATE TABLE message_media (
    message_row_id          INTEGER NOT NULL,   -- FK → message._id
    file_path               TEXT,
    file_size               INTEGER,
    mime_type               TEXT,
    media_name              TEXT,
    media_caption           TEXT,
    media_duration          INTEGER,            -- millisecondi (per audio/video)
    message_url             TEXT
);

-- Tabella reazioni e modifiche (WhatsApp 2022+)
CREATE TABLE message_add_on (
    _id                     INTEGER PRIMARY KEY AUTOINCREMENT,
    parent_message_row_id   INTEGER,            -- FK → message._id
    timestamp               INTEGER,
    add_on_type             INTEGER             -- 1=reazione, 2=modifica, 3=visualizzazione unica
);
```

### wa.db — database contatti

```sql
-- In wa.db (database separato)
CREATE TABLE wa_contacts (
    _id                     INTEGER PRIMARY KEY AUTOINCREMENT,
    jid                     TEXT,               -- "3391234567@s.whatsapp.net"
    display_name            TEXT,               -- nome come appare in WhatsApp
    given_name              TEXT,
    family_name             TEXT,
    number                  TEXT                -- numero normalizzato
);
```

### WhatsAppDatabase.kt — helper SQLite custom

Non usiamo Room direttamente sul file decifrato perché Room si aspetta di gestire il database dall'inizio. Usiamo SQLiteOpenHelper (o SQLiteDatabase direttamente) puntando al file decifrato.

```kotlin
class WhatsAppDatabase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var msgDb: SQLiteDatabase? = null
    private var waDb: SQLiteDatabase?  = null

    /**
     * Apre il database decifrato in modalità read-only.
     * Il file deve già essere stato decifrato in precedenza.
     */
    fun open(msgstoreFile: File, waDbFile: File) {
        msgDb = SQLiteDatabase.openDatabase(
            msgstoreFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        )
        waDb = SQLiteDatabase.openDatabase(
            waDbFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        )
    }

    fun close() {
        msgDb?.close()
        waDb?.close()
        msgDb = null
        waDb  = null
    }

    fun getMsgDb(): SQLiteDatabase = msgDb
        ?: throw IllegalStateException("Database non aperto. Chiama open() prima.")

    fun getWaDb(): SQLiteDatabase = waDb
        ?: throw IllegalStateException("Database wa.db non aperto. Chiama open() prima.")
}
```

### MessageDao.kt — query principali

```kotlin
class MessageDao @Inject constructor(
    private val database: WhatsAppDatabase
) {

    /**
     * Conta i messaggi totali per chat, separando inviati e ricevuti.
     */
    fun getMessageCountByChat(): List<ChatMessageCount> {
        val query = """
            SELECT
                c._id               AS chat_id,
                j.raw_string        AS jid,
                j.cached_name       AS name,
                COUNT(*)            AS total,
                SUM(m.from_me)      AS sent,
                COUNT(*) - SUM(m.from_me) AS received
            FROM message m
            JOIN chat c ON m.chat_row_id = c._id
            JOIN jid j  ON c.jid_row_id  = j._id
            WHERE m.message_type = 0        -- solo messaggi di testo
            GROUP BY c._id
            ORDER BY total DESC
        """.trimIndent()

        val cursor = database.getMsgDb().rawQuery(query, null)
        return cursor.use { parseChatMessageCount(it) }
    }

    /**
     * Distribuzione oraria dei messaggi per una chat specifica.
     * Restituisce 24 valori (uno per ora).
     */
    fun getHourlyDistribution(chatId: Long): List<HourlyCount> {
        val query = """
            SELECT
                CAST(strftime('%H', datetime(timestamp / 1000, 'unixepoch', 'localtime')) AS INTEGER) AS hour,
                COUNT(*) AS count
            FROM message
            WHERE chat_row_id = ?
            GROUP BY hour
            ORDER BY hour
        """.trimIndent()

        val cursor = database.getMsgDb().rawQuery(query, arrayOf(chatId.toString()))
        return cursor.use { parseHourlyCount(it) }
    }

    /**
     * Distribuzione per giorno della settimana (0=domenica, 6=sabato).
     */
    fun getDayOfWeekDistribution(chatId: Long): List<DayCount> {
        val query = """
            SELECT
                CAST(strftime('%w', datetime(timestamp / 1000, 'unixepoch', 'localtime')) AS INTEGER) AS day,
                COUNT(*) AS count
            FROM message
            WHERE chat_row_id = ?
            GROUP BY day
            ORDER BY day
        """.trimIndent()

        val cursor = database.getMsgDb().rawQuery(query, arrayOf(chatId.toString()))
        return cursor.use { parseDayCount(it) }
    }

    /**
     * Evoluzione mensile del numero di messaggi.
     */
    fun getMonthlyTrend(chatId: Long): List<MonthlyCount> {
        val query = """
            SELECT
                strftime('%Y-%m', datetime(timestamp / 1000, 'unixepoch')) AS month,
                COUNT(*) AS count
            FROM message
            WHERE chat_row_id = ?
            GROUP BY month
            ORDER BY month
        """.trimIndent()

        val cursor = database.getMsgDb().rawQuery(query, arrayOf(chatId.toString()))
        return cursor.use { parseMonthlyCount(it) }
    }

    /**
     * Parole più frequenti in una chat (esclude stop words italiane).
     */
    fun getTopWords(chatId: Long, limit: Int = 50): List<WordFrequency> {
        // Le stop words vengono filtrate in memoria dopo la query
        val query = """
            SELECT text_data
            FROM message
            WHERE chat_row_id = ? AND from_me = 0 AND text_data IS NOT NULL
              AND message_type = 0
        """.trimIndent()

        val cursor = database.getMsgDb().rawQuery(query, arrayOf(chatId.toString()))
        val texts = cursor.use {
            buildList {
                while (it.moveToNext()) add(it.getString(0))
            }
        }
        return countWords(texts, limit)
    }

    /**
     * Statistiche sui media inviati/ricevuti per tipo MIME.
     */
    fun getMediaStats(chatId: Long): List<MediaTypeStat> {
        val query = """
            SELECT
                mm.mime_type,
                COUNT(*) AS count,
                SUM(mm.file_size) AS total_size
            FROM message m
            JOIN message_media mm ON m._id = mm.message_row_id
            WHERE m.chat_row_id = ?
            GROUP BY mm.mime_type
            ORDER BY count DESC
        """.trimIndent()

        val cursor = database.getMsgDb().rawQuery(query, arrayOf(chatId.toString()))
        return cursor.use { parseMediaStats(it) }
    }

    /**
     * Chi ha iniziato più conversazioni.
     * Una "conversazione" è un messaggio che arriva dopo almeno 3 ore di silenzio.
     */
    fun getConversationStarters(chatId: Long): ConversationStarterStats {
        val query = """
            SELECT
                from_me,
                COUNT(*) AS starter_count
            FROM (
                SELECT
                    from_me,
                    timestamp,
                    LAG(timestamp) OVER (ORDER BY timestamp) AS prev_timestamp
                FROM message
                WHERE chat_row_id = ? AND message_type = 0
            )
            WHERE prev_timestamp IS NULL
               OR (timestamp - prev_timestamp) > 10800000  -- 3 ore in ms
            GROUP BY from_me
        """.trimIndent()

        val cursor = database.getMsgDb().rawQuery(query, arrayOf(chatId.toString()))
        return cursor.use { parseConversationStarters(it) }
    }

    // --- Parsing helpers ---

    private fun parseChatMessageCount(cursor: Cursor): List<ChatMessageCount> = buildList {
        while (cursor.moveToNext()) {
            add(ChatMessageCount(
                chatId   = cursor.getLong(cursor.getColumnIndexOrThrow("chat_id")),
                jid      = cursor.getString(cursor.getColumnIndexOrThrow("jid")),
                name     = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                total    = cursor.getInt(cursor.getColumnIndexOrThrow("total")),
                sent     = cursor.getInt(cursor.getColumnIndexOrThrow("sent")),
                received = cursor.getInt(cursor.getColumnIndexOrThrow("received"))
            ))
        }
    }

    private fun parseHourlyCount(cursor: Cursor): List<HourlyCount> = buildList {
        while (cursor.moveToNext()) {
            add(HourlyCount(
                hour  = cursor.getInt(0),
                count = cursor.getInt(1)
            ))
        }
    }

    private fun parseDayCount(cursor: Cursor): List<DayCount> = buildList {
        while (cursor.moveToNext()) {
            add(DayCount(
                dayOfWeek = cursor.getInt(0),
                count     = cursor.getInt(1)
            ))
        }
    }

    private fun parseMonthlyCount(cursor: Cursor): List<MonthlyCount> = buildList {
        while (cursor.moveToNext()) {
            add(MonthlyCount(
                month = cursor.getString(0),
                count = cursor.getInt(1)
            ))
        }
    }

    private fun parseMediaStats(cursor: Cursor): List<MediaTypeStat> = buildList {
        while (cursor.moveToNext()) {
            add(MediaTypeStat(
                mimeType  = cursor.getString(0) ?: "unknown",
                count     = cursor.getInt(1),
                totalSize = cursor.getLong(2)
            ))
        }
    }

    private fun parseConversationStarters(cursor: Cursor): ConversationStarterStats {
        var iStarted  = 0
        var theyStarted = 0
        while (cursor.moveToNext()) {
            val fromMe = cursor.getInt(0)
            val count  = cursor.getInt(1)
            if (fromMe == 1) iStarted    = count
            else             theyStarted = count
        }
        return ConversationStarterStats(iStarted, theyStarted)
    }

    private fun countWords(texts: List<String>, limit: Int): List<WordFrequency> {
        val stopWords = setOf(
            "il", "lo", "la", "i", "gli", "le", "un", "uno", "una",
            "di", "da", "in", "con", "su", "per", "tra", "fra",
            "e", "o", "ma", "se", "che", "non", "ho", "hai", "ha",
            "a", "al", "del", "della", "dei", "degli", "delle",
            "mi", "ti", "ci", "si", "ne", "è", "sono", "sei", "siamo",
            "come", "quando", "dove", "perché", "poi", "già", "anche",
            "the", "a", "is", "in", "of", "and", "to", "it", "for"
        )
        val freq = mutableMapOf<String, Int>()
        texts.forEach { text ->
            text.lowercase()
                .replace(Regex("[^a-zàáâãäåæçèéêëìíîïðñòóôõöøùúûüý\\s]"), "")
                .split(Regex("\\s+"))
                .filter { it.length > 2 && it !in stopWords }
                .forEach { word -> freq[word] = (freq[word] ?: 0) + 1 }
        }
        return freq.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { WordFrequency(it.key, it.value) }
    }
}
```

---

## 7. Livello 4 — Analytics Engine

### Modelli di dominio

```kotlin
// domain/model/GlobalStats.kt
data class GlobalStats(
    val totalMessages: Int,
    val totalChats: Int,
    val totalContacts: Int,
    val totalMediaFiles: Int,
    val oldestMessageDate: Long,    // Unix timestamp ms
    val mostActiveChat: String,
    val mostActiveHour: Int,        // 0-23
    val mostActiveDayOfWeek: Int,   // 0=domenica
    val averageMessagesPerDay: Float,
    val longestStreak: Int          // giorni consecutivi con almeno un messaggio
)

// domain/model/ChatStats.kt
data class ChatStats(
    val chatId: Long,
    val contactName: String,
    val jid: String,
    val isGroup: Boolean,
    val totalMessages: Int,
    val sentByMe: Int,
    val receivedFromThem: Int,
    val sentRatio: Float,           // percentuale messaggi inviati
    val firstMessageDate: Long,
    val lastMessageDate: Long,
    val averageResponseTimeMs: Long,
    val iStartedConversations: Int,
    val theyStartedConversations: Int,
    val longestMessage: String,
    val averageMessageLength: Float,
    val mediaCount: Int,
    val audioCount: Int,
    val videoCount: Int,
    val hourlyDistribution: List<HourlyCount>,   // 24 elementi
    val weeklyDistribution: List<DayCount>,       // 7 elementi
    val monthlyTrend: List<MonthlyCount>,
    val topWords: List<WordFrequency>
)

data class HourlyCount(val hour: Int, val count: Int)
data class DayCount(val dayOfWeek: Int, val count: Int)
data class MonthlyCount(val month: String, val count: Int)
data class WordFrequency(val word: String, val frequency: Int)

data class MediaTypeStat(
    val mimeType: String,
    val count: Int,
    val totalSize: Long
)

data class ConversationStarterStats(
    val startedByMe: Int,
    val startedByThem: Int
)
```

### AnalyticsRepository.kt

```kotlin
class AnalyticsRepository @Inject constructor(
    private val messageDao: MessageDao,
    private val chatDao: ChatDao,
    private val contactDao: ContactDao
) {

    suspend fun getGlobalStats(): GlobalStats = withContext(Dispatchers.IO) {
        val totalMessages = messageDao.getTotalMessageCount()
        val totalChats    = chatDao.getChatCount()
        val hourly        = messageDao.getGlobalHourlyDistribution()
        val weekly        = messageDao.getGlobalWeeklyDistribution()
        val streak        = calculateLongestStreak()

        GlobalStats(
            totalMessages        = totalMessages,
            totalChats           = totalChats,
            totalContacts        = contactDao.getContactCount(),
            totalMediaFiles      = messageDao.getMediaCount(),
            oldestMessageDate    = messageDao.getOldestTimestamp(),
            mostActiveChat       = chatDao.getMostActiveChat(),
            mostActiveHour       = hourly.maxByOrNull { it.count }?.hour ?: 0,
            mostActiveDayOfWeek  = weekly.maxByOrNull { it.count }?.dayOfWeek ?: 0,
            averageMessagesPerDay = calculateAvgPerDay(totalMessages),
            longestStreak        = streak
        )
    }

    suspend fun getChatStats(chatId: Long): ChatStats = withContext(Dispatchers.IO) {
        val basicInfo       = chatDao.getChatBasicInfo(chatId)
        val msgCount        = messageDao.getMessageCountForChat(chatId)
        val hourly          = messageDao.getHourlyDistribution(chatId)
        val weekly          = messageDao.getDayOfWeekDistribution(chatId)
        val monthly         = messageDao.getMonthlyTrend(chatId)
        val topWords        = messageDao.getTopWords(chatId)
        val mediaStats      = messageDao.getMediaStats(chatId)
        val starters        = messageDao.getConversationStarters(chatId)
        val avgResponseTime = calculateAverageResponseTime(chatId)

        ChatStats(
            chatId                   = chatId,
            contactName              = basicInfo.name,
            jid                      = basicInfo.jid,
            isGroup                  = basicInfo.jid.contains("@g.us"),
            totalMessages            = msgCount.total,
            sentByMe                 = msgCount.sent,
            receivedFromThem         = msgCount.received,
            sentRatio                = msgCount.sent.toFloat() / msgCount.total,
            firstMessageDate         = messageDao.getFirstMessageTimestamp(chatId),
            lastMessageDate          = messageDao.getLastMessageTimestamp(chatId),
            averageResponseTimeMs    = avgResponseTime,
            iStartedConversations    = starters.startedByMe,
            theyStartedConversations = starters.startedByThem,
            longestMessage           = messageDao.getLongestMessage(chatId),
            averageMessageLength     = messageDao.getAverageMessageLength(chatId),
            mediaCount               = mediaStats.sumOf { it.count },
            audioCount               = mediaStats.filter { it.mimeType.startsWith("audio") }.sumOf { it.count },
            videoCount               = mediaStats.filter { it.mimeType.startsWith("video") }.sumOf { it.count },
            hourlyDistribution       = hourly,
            weeklyDistribution       = weekly,
            monthlyTrend             = monthly,
            topWords                 = topWords
        )
    }

    /**
     * Calcola il tempo medio di risposta in millisecondi.
     * Considera solo i cambi di mittente consecutivi (io → loro, loro → io).
     */
    private suspend fun calculateAverageResponseTime(chatId: Long): Long =
        withContext(Dispatchers.IO) {
            val query = """
                SELECT AVG(response_time) FROM (
                    SELECT
                        timestamp - LAG(timestamp) OVER (ORDER BY timestamp) AS response_time,
                        from_me,
                        LAG(from_me) OVER (ORDER BY timestamp) AS prev_from_me
                    FROM message
                    WHERE chat_row_id = ? AND message_type = 0
                )
                WHERE prev_from_me IS NOT NULL AND from_me != prev_from_me
                  AND response_time < 86400000   -- ignora risposte dopo 24h
            """.trimIndent()

            // Esegui tramite MessageDao (qui semplificato)
            0L // placeholder
        }

    private suspend fun calculateLongestStreak(): Int = withContext(Dispatchers.IO) {
        // Recupera tutte le date con almeno un messaggio
        val dates = messageDao.getActiveDates()
        if (dates.isEmpty()) return@withContext 0

        var maxStreak = 1
        var currentStreak = 1

        for (i in 1 until dates.size) {
            val diff = dates[i] - dates[i - 1]
            if (diff == 1L) { // giorni consecutivi
                currentStreak++
                if (currentStreak > maxStreak) maxStreak = currentStreak
            } else {
                currentStreak = 1
            }
        }
        maxStreak
    }

    private fun calculateAvgPerDay(totalMessages: Int): Float {
        // Semplificazione: calcola su base 365 giorni
        return totalMessages / 365f
    }
}
```

---

## 8. Livello 5 — UI con Jetpack Compose

### Navigazione

```kotlin
// ui/navigation/AppNavigation.kt
sealed class Screen(val route: String) {
    object Setup       : Screen("setup")
    object Dashboard   : Screen("dashboard")
    object ChatList    : Screen("chat_list")
    object ChatDetail  : Screen("chat_detail/{chatId}") {
        fun createRoute(chatId: Long) = "chat_detail/$chatId"
    }
}

@Composable
fun AppNavigation(navController: NavHostController) {
    NavHost(navController, startDestination = Screen.Setup.route) {
        composable(Screen.Setup.route) {
            SetupScreen(onSetupComplete = {
                navController.navigate(Screen.Dashboard.route) {
                    popUpTo(Screen.Setup.route) { inclusive = true }
                }
            })
        }
        composable(Screen.Dashboard.route) {
            DashboardScreen(onNavigateToChatList = {
                navController.navigate(Screen.ChatList.route)
            })
        }
        composable(Screen.ChatList.route) {
            ChatListScreen(onChatSelected = { chatId ->
                navController.navigate(Screen.ChatDetail.createRoute(chatId))
            })
        }
        composable(
            route = Screen.ChatDetail.route,
            arguments = listOf(navArgument("chatId") { type = NavType.LongType })
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getLong("chatId") ?: return@composable
            ChatDetailScreen(chatId = chatId)
        }
    }
}
```

### SetupScreen.kt — primo avvio

```kotlin
@Composable
fun SetupScreen(
    viewModel: SetupViewModel = hiltViewModel(),
    onSetupComplete: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.isComplete) {
        if (state.isComplete) onSetupComplete()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement   = Arrangement.Center,
        horizontalAlignment   = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(24.dp))
        Text("WhatStat Reborn", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "L'app accede al database di WhatsApp sul tuo dispositivo. " +
            "Tutti i dati rimangono locali, nulla viene inviato.",
            style    = MaterialTheme.typography.bodyMedium,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(40.dp))

        // Stato delle operazioni
        SetupStep(
            label     = "Accesso root",
            status    = state.rootStatus,
            icon      = Icons.Outlined.AdminPanelSettings
        )
        SetupStep(
            label     = "Estrazione file",
            status    = state.extractionStatus,
            icon      = Icons.Outlined.FolderOpen
        )
        SetupStep(
            label     = "Decifratura database",
            status    = state.decryptionStatus,
            icon      = Icons.Outlined.Key
        )
        SetupStep(
            label     = "Lettura contatti",
            status    = state.contactsStatus,
            icon      = Icons.Outlined.Contacts
        )

        Spacer(Modifier.height(40.dp))

        if (state.error != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    state.error!!,
                    modifier = Modifier.padding(16.dp),
                    color    = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        Button(
            onClick  = { viewModel.startSetup() },
            enabled  = !state.isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(if (state.isLoading) "Elaborazione..." else "Inizia analisi")
        }
    }
}

@Composable
fun SetupStep(label: String, status: StepStatus, icon: ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, modifier = Modifier.weight(1f))
        when (status) {
            StepStatus.IDLE     -> Icon(Icons.Outlined.RadioButtonUnchecked, null, tint = MaterialTheme.colorScheme.outline)
            StepStatus.LOADING  -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            StepStatus.SUCCESS  -> Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
            StepStatus.ERROR    -> Icon(Icons.Filled.Error, null, tint = MaterialTheme.colorScheme.error)
        }
    }
}
```

### DashboardScreen.kt — panoramica globale

```kotlin
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onNavigateToChatList: () -> Unit
) {
    val stats by viewModel.globalStats.collectAsState()

    LazyColumn(
        modifier          = Modifier.fillMaxSize(),
        contentPadding    = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Panoramica", style = MaterialTheme.typography.headlineSmall)
        }

        // Statistiche principali in grid 2x2
        item {
            stats?.let { s ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        label    = "Messaggi totali",
                        value    = "%,d".format(s.totalMessages),
                        icon     = Icons.Outlined.Chat
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        label    = "Chat attive",
                        value    = s.totalChats.toString(),
                        icon     = Icons.Outlined.People
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        label    = "Media/giorno",
                        value    = "%.1f".format(s.averageMessagesPerDay),
                        icon     = Icons.Outlined.TrendingUp
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        label    = "Streak più lungo",
                        value    = "${s.longestStreak} giorni",
                        icon     = Icons.Outlined.LocalFireDepartment
                    )
                }
            } ?: CircularProgressIndicator()
        }

        // Heatmap oraria globale
        item {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("Quando sei più attivo", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    stats?.let {
                        HourlyHeatmap(hourlyData = viewModel.hourlyData.collectAsState().value)
                    }
                }
            }
        }

        // Pulsante lista chat
        item {
            Button(
                onClick  = onNavigateToChatList,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Analizza le singole chat")
            }
        }
    }
}
```

### Componenti UI riutilizzabili

```kotlin
// ui/component/StatCard.kt
@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    icon: ImageVector
) {
    Card(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ui/component/HourlyHeatmap.kt
@Composable
fun HourlyHeatmap(hourlyData: List<HourlyCount>) {
    if (hourlyData.isEmpty()) return
    val maxCount = hourlyData.maxOf { it.count }.toFloat()
    val primary = MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        (0..23).forEach { hour ->
            val count = hourlyData.find { it.hour == hour }?.count ?: 0
            val alpha = if (maxCount > 0) count / maxCount else 0f
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .width(10.dp)
                        .height(40.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(primary.copy(alpha = 0.15f + alpha * 0.85f))
                )
                if (hour % 6 == 0) {
                    Text(
                        "$hour",
                        style    = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp
                    )
                }
            }
        }
    }
}
```

---

## 9. Architettura MVVM completa

### SetupViewModel.kt

```kotlin
@HiltViewModel
class SetupViewModel @Inject constructor(
    private val fileExtractor: FileExtractor,
    private val decryptor: Crypt14Decryptor,
    private val rootManager: RootManager,
    private val database: WhatsAppDatabase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(SetupState())
    val state: StateFlow<SetupState> = _state.asStateFlow()

    fun startSetup() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            // Step 1: verifica root
            _state.update { it.copy(rootStatus = StepStatus.LOADING) }
            val rootStatus = rootManager.checkRootAccess()
            if (rootStatus != RootStatus.GRANTED) {
                _state.update {
                    it.copy(
                        isLoading  = false,
                        rootStatus = StepStatus.ERROR,
                        error      = "Root non disponibile. Assicurati che Magisk sia installato e che l'app abbia i permessi root."
                    )
                }
                return@launch
            }
            _state.update { it.copy(rootStatus = StepStatus.SUCCESS) }

            // Step 2: estrai file
            _state.update { it.copy(extractionStatus = StepStatus.LOADING) }
            val extractResult = fileExtractor.extractAll()
            if (extractResult.isFailure) {
                _state.update {
                    it.copy(
                        isLoading       = false,
                        extractionStatus = StepStatus.ERROR,
                        error           = "Errore durante l'estrazione: ${extractResult.exceptionOrNull()?.message}"
                    )
                }
                return@launch
            }
            val files = extractResult.getOrThrow()
            _state.update { it.copy(extractionStatus = StepStatus.SUCCESS) }

            // Step 3: decifra
            _state.update { it.copy(decryptionStatus = StepStatus.LOADING) }
            val outputDb = File(context.filesDir, "msgstore.db")
            val decryptResult = decryptor.decrypt(files.crypt14File, files.keyFile, outputDb)
            if (decryptResult.isFailure) {
                _state.update {
                    it.copy(
                        isLoading        = false,
                        decryptionStatus = StepStatus.ERROR,
                        error            = "Decifratura fallita: ${decryptResult.exceptionOrNull()?.message}"
                    )
                }
                return@launch
            }
            _state.update { it.copy(decryptionStatus = StepStatus.SUCCESS) }

            // Step 4: apri database
            _state.update { it.copy(contactsStatus = StepStatus.LOADING) }
            runCatching { database.open(outputDb, files.waDbFile) }
                .onFailure {
                    _state.update {
                        it.copy(
                            isLoading      = false,
                            contactsStatus = StepStatus.ERROR,
                            error          = "Impossibile aprire il database: ${it.message}"
                        )
                    }
                    return@launch
                }
            _state.update { it.copy(contactsStatus = StepStatus.SUCCESS) }

            // Completato
            _state.update { it.copy(isLoading = false, isComplete = true) }
        }
    }
}

data class SetupState(
    val isLoading        : Boolean    = false,
    val isComplete       : Boolean    = false,
    val error            : String?    = null,
    val rootStatus       : StepStatus = StepStatus.IDLE,
    val extractionStatus : StepStatus = StepStatus.IDLE,
    val decryptionStatus : StepStatus = StepStatus.IDLE,
    val contactsStatus   : StepStatus = StepStatus.IDLE
)

enum class StepStatus { IDLE, LOADING, SUCCESS, ERROR }
```

---

## 10. Gestione degli errori

### Casi d'errore da gestire

| Scenario | Causa probabile | Risposta dell'app |
|---|---|---|
| Root non disponibile | Magisk non installato / permessi negati | Mostra istruzioni chiare |
| File key non trovato | Percorso cambiato con aggiornamento WhatsApp | Messaggio con percorso atteso |
| File crypt14 non trovato | Backup locale disabilitato o crypt15 | Guida per abilitare backup locale |
| Decifratura fallita (tag GCM) | Chiave non corrispondente | Suggerisci di rigenerare il backup |
| Database corrotto | Backup incompleto | Guida per fare nuovo backup da WhatsApp |
| Schema non riconosciuto | Versione WhatsApp troppo nuova | Segnala agli sviluppatori |
| Spazio insufficiente | Database molto grande | Mostra spazio necessario |

### Sealed class per i risultati

```kotlin
sealed class AppError {
    object RootNotAvailable  : AppError()
    object KeyFileNotFound   : AppError()
    object Crypt14NotFound   : AppError()
    object Crypt15Detected   : AppError()   // backup E2E attivo
    data class DecryptionFailed(val reason: String) : AppError()
    data class DatabaseError(val message: String)   : AppError()
    data class UnknownError(val throwable: Throwable) : AppError()
}
```

---

## 11. Sicurezza e privacy

### Principi fondamentali

- **Zero network access:** l'app non deve mai aprire connessioni di rete. Aggiungere la regola in `network_security_config.xml` per bloccare tutto il traffico.
- **Dati in directory privata:** tutti i file estratti e il database decifrato vanno in `context.filesDir`, inaccessibile ad altre app senza root.
- **Nessuna persistenza non necessaria:** il database decifrato può essere eliminato dopo l'analisi e ricostruito al prossimo avvio.
- **Nessun log in produzione:** non loggare mai contenuti di messaggi, nomi o numeri di telefono nei log di sistema.

```xml
<!-- res/xml/network_security_config.xml -->
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <!-- Blocca TUTTO il traffico di rete -->
        </trust-anchors>
    </base-config>
</network-security-config>
```

```xml
<!-- AndroidManifest.xml -->
<application
    android:networkSecurityConfig="@xml/network_security_config"
    ...>
```

### Pulizia sicura dei file temporanei

```kotlin
fun secureDelete(file: File) {
    if (!file.exists()) return
    // Sovrascrive con zeri prima di eliminare
    file.outputStream().use { out ->
        val zeros = ByteArray(8192)
        var remaining = file.length()
        while (remaining > 0) {
            val toWrite = minOf(remaining, zeros.size.toLong()).toInt()
            out.write(zeros, 0, toWrite)
            remaining -= toWrite
        }
        out.flush()
    }
    file.delete()
}
```

---

## 12. Testing

### Unit test — Decryption Engine

```kotlin
class Crypt14DecryptorTest {

    private val keyReader  = KeyReader()
    private val decryptor  = Crypt14Decryptor(keyReader)

    @Test
    fun `il file decifrato inizia con magic bytes SQLite`() = runTest {
        val keyFile    = loadTestAsset("test.key")
        val cryptFile  = loadTestAsset("test.db.crypt14")
        val outputFile = File.createTempFile("test_output", ".db")

        val result = decryptor.decrypt(cryptFile, keyFile, outputFile)

        assertTrue(result.isSuccess)
        val magic = outputFile.readBytes().take(6).toByteArray()
        assertEquals("SQLite", String(magic))
    }

    @Test
    fun `chiave sbagliata genera errore di decifratura`() = runTest {
        val wrongKey  = File.createTempFile("wrong", ".key").also {
            it.writeBytes(ByteArray(32 + 126) { 0xFF.toByte() })
        }
        val cryptFile = loadTestAsset("test.db.crypt14")
        val output    = File.createTempFile("output", ".db")

        val result = decryptor.decrypt(cryptFile, wrongKey, output)
        assertTrue(result.isFailure)
    }
}
```

### Integration test — Analytics

```kotlin
class AnalyticsRepositoryTest {

    // Usa un database SQLite di test pre-popolato
    @Test
    fun `getGlobalStats restituisce valori coerenti`() = runTest {
        val db    = openTestDatabase()
        val repo  = buildTestRepository(db)
        val stats = repo.getGlobalStats()

        assertTrue(stats.totalMessages > 0)
        assertTrue(stats.mostActiveHour in 0..23)
        assertTrue(stats.longestStreak >= 1)
    }
}
```

---

## 13. Roadmap e feature future

### v1.0 — MVP
- [x] Root access e estrazione file
- [x] Decifratura crypt14
- [x] Lettura database SQLite
- [x] Statistiche base per chat (messaggi, orari, giorni)
- [x] Dashboard globale
- [x] Lista chat con ranking

### v1.1 — Analytics avanzati
- [ ] Tempo medio di risposta per chat
- [ ] Rilevamento "ghosting" (messaggi senza risposta)
- [ ] Grafici di evoluzione mensile
- [ ] Word cloud per ogni chat
- [ ] Statistiche emoji usate
- [ ] Analisi link condivisi

### v1.2 — UX migliorata
- [ ] Widget homescreen con stat del giorno
- [ ] Export statistiche in PDF
- [ ] Tema chiaro/scuro automatico
- [ ] Animazioni nelle transizioni
- [ ] Onboarding guidato

### v1.3 — Feature avanzate
- [ ] Confronto tra periodi temporali
- [ ] Rilevamento automatico aggiornamenti WhatsApp (cambio schema db)
- [ ] Supporto WhatsApp Business
- [ ] Modalità "time capsule" (analisi di un anno fa)
- [ ] Supporto crypt15 (con inserimento manuale chiave 64 hex)
- [ ] Backup automatico delle statistiche (non dei messaggi)

### Considerazioni per crypt15 (future)

Se si volesse supportare anche crypt15 (backup E2E attivo), l'utente dovrebbe fornire manualmente la chiave a 64 caratteri esadecimali visibile nelle impostazioni WhatsApp. Il processo di decifratura sarebbe diverso (usa un KDF — Key Derivation Function — aggiuntivo prima di AES-GCM).

---

## 14. Riferimenti e risorse

### Progetti open source di riferimento

- **wa-crypt-tools** (ElDavoo) — https://github.com/ElDavoo/wa-crypt-tools  
  La fonte più aggiornata per la specifica tecnica del formato crypt14/15. Fondamentale.

- **WhatsApp-Crypt14-Crypt15-Decrypter** (NelisMk7) — https://github.com/NelisMk7/WhatsApp-Crypt14-Crypt15-Decrypter  
  Implementazione Python di riferimento per la decifratura.

- **whatsapp-chat-exporter** (KnugiHK) — https://github.com/KnugiHK/Whatsapp-Chat-Exporter  
  Parser completo del database decifrato, utile per capire lo schema.

- **libsu** (topjohnwu) — https://github.com/topjohnwu/libsu  
  Documentazione completa per il root access su Android.

### Documentazione tecnica

- Schema SQLite msgstore.db: https://github.com/andreas-mausch/whatsapp-viewer/blob/master/data/msgstore.db.schema.sql
- Forensic analysis WhatsApp Android: https://belkasoft.com/android-whatsapp-forensics-analysis
- XDA thread recupero database: https://xdaforums.com/t/recovering-whatsapp-messages-from-android-without-root-writeup.4364621/

### Librerie Android consigliate

- **Vico** (grafici Compose-native): https://patrykandpatrick.com/vico/
- **libsu**: https://github.com/topjohnwu/libsu
- **Hilt** (DI): https://dagger.dev/hilt/
- **Accompanist** (utilities Compose): https://google.github.io/accompanist/

---

> Documento generato come guida di sviluppo. Aggiorna l'offset della chiave e la struttura dell'header se WhatsApp rilascia aggiornamenti che modificano il formato del database.
