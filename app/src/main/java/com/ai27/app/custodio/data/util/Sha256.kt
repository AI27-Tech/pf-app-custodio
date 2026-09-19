package com.ai27.app.custodio.data.util

import java.security.MessageDigest

// SHA-256 del contenido capturado; se guarda en el documento del medio (PLAN §5.1).
object Sha256 {
    fun digestHex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}