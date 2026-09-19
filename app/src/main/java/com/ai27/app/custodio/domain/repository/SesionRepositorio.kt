package com.ai27.app.custodio.domain.repository

import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.model.Custodio
import kotlinx.coroutines.flow.Flow

interface SesionRepositorio {
    fun observarCustodio(): Flow<Custodio?>
    suspend fun iniciarSesion(correo: String, contraseña: String): Result<Custodio>
    suspend fun cerrarSesion(): Result<Unit>
}