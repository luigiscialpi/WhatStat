package com.whatstat.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.whatstat.BuildConfig
import java.security.MessageDigest

/**
 * Gestisce la verifica dell'integrità dell'APK.
 * Rileva se l'app è stata ripacchettizzata (es. da Lucky Patcher).
 */
object IntegrityChecker {
    private const val RELEASE_SIGNATURE_HASH = "20cb7c9554d44409740f4f15abd700f4bf17f1f74e467f6e745201cc9eb05ca6"

    private var _isTampered = false

    /**
     * Restituisce true se l'app sembra essere stata manomessa o ri-firmata.
     */
    val isTampered: Boolean
        get() = _isTampered

    /**
     * Esegue il controllo della firma.
     * NON blocca l'esecuzione ma imposta il flag [isTampered].
     */
    @SuppressLint("PackageManagerGetSignatures")
    fun verifySignature(context: Context) {
        if (BuildConfig.DEBUG) {
            _isTampered = false
            return
        }

        try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                )
            }

            val signingInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                packageInfo.signatures
            }

            if (signingInfo.isNullOrEmpty()) {
                _isTampered = true
                return
            }

            val cert = signingInfo[0].toByteArray()
            val md = MessageDigest.getInstance("SHA-256")
            val currentHash = md.digest(cert).joinToString("") { "%02x".format(it) }

            if (currentHash != RELEASE_SIGNATURE_HASH) {
                DebugLogManager.log("Integrity check failed! Signature mismatch.")
                _isTampered = true
            } else {
                _isTampered = false
            }

        } catch (e: Exception) {
            DebugLogManager.log("Integrity check error", e)
            // In caso di errore nel controllo, per prudenza non blocchiamo ma non fidiamoci al 100%
            // In una implementazione reale si potrebbe decidere di marcare come tampered
        }
    }

    /**
     * Verifica l'installatore dell'app.
     */
    fun isFromGooglePlay(context: Context): Boolean {
        val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getInstallerPackageName(context.packageName)
        }
        return installer == "com.android.vending"
    }
}
