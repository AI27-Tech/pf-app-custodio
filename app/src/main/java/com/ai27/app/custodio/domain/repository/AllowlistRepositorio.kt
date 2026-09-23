package com.ai27.app.custodio.domain.repository

import com.ai27.app.custodio.domain.Result

/**
 * Allowlist de teléfonos autorizados (PHONE_AUTH_PLAN Task 4, opción B): se consulta
 * DESPUÉS de autenticar, para que la regla de lectura de Firestore pueda exigir
 * `request.auth != null` sin debilitar nada.
 */
interface AllowlistRepositorio {

    /** Si `telefono` (E.164, p. ej. +525544880512) está en `config/telefonos.telefonos`. */
    suspend fun contiene(telefono: String): Result<Boolean>
}