package com.ai27.app.custodio.domain.repository

import com.ai27.app.custodio.domain.Result

/**
 * Allowlist de correos autorizados (plan de correo, opción B): se consulta
 * DESPUÉS de autenticar, para que la regla de lectura de Firestore pueda exigir
 * `request.auth != null` sin debilitar nada.
 */
interface AllowlistRepositorio {

    /** Si `correo` tiene documento en `correos_autorizados` (id == correo en minúsculas). */
    suspend fun contiene(correo: String): Result<Boolean>
}
