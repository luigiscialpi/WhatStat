package com.whatstat.reborn.data.root

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gestisce l'accesso root tramite libsu.
 * Mantiene una shell root persistente per tutta la sessione dell'app.
 */
@Singleton
class RootManager
    @Inject
    constructor() {
        /**
         * Verifica se il dispositivo ha root disponibile e accessibile.
         * Da chiamare prima di qualsiasi operazione privilegiata.
         */
        suspend fun checkRootAccess(): RootStatus =
            withContext(Dispatchers.IO) {
                try {
                    val shell = Shell.getShell()
                    when {
                        !shell.isAlive -> RootStatus.NOT_AVAILABLE
                        !shell.isRoot -> RootStatus.NOT_GRANTED
                        else -> RootStatus.GRANTED
                    }
                } catch (e: Exception) {
                    RootStatus.NOT_AVAILABLE
                }
            }

        /**
         * Esegue un comando root e restituisce l'output.
         * Il comando viene eseguito nella shell root persistente.
         */
        suspend fun execute(command: String): ShellResult =
            withContext(Dispatchers.IO) {
                val result = Shell.cmd(command).exec()
                ShellResult(
                    success = result.isSuccess,
                    output = result.out,
                    error = result.err,
                )
            }

        /**
         * Verifica che un file esista sul filesystem (con accesso root).
         */
        suspend fun fileExists(path: String): Boolean =
            withContext(Dispatchers.IO) {
                val result = Shell.cmd("[ -f '$path' ] && echo 'yes' || echo 'no'").exec()
                result.out.firstOrNull()?.trim() == "yes"
            }
    }

enum class RootStatus {
    GRANTED,
    NOT_GRANTED,
    NOT_AVAILABLE,
}

data class ShellResult(
    val success: Boolean,
    val output: List<String>,
    val error: List<String>,
)
