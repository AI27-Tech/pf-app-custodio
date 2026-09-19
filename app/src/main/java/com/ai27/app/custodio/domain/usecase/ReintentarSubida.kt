package com.ai27.app.custodio.domain.usecase

import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.error.DominioError
import com.ai27.app.custodio.domain.model.EstadoSubida
import com.ai27.app.custodio.domain.model.Medio
import com.ai27.app.custodio.domain.repository.MedioRepository
import javax.inject.Inject

class ReintentarSubida @Inject constructor(
    private val medioRepository: MedioRepository,
) {
    suspend operator fun invoke(medioId: String): Result<Medio> {
        val medio = medioRepository.obtenerPorId(medioId)
            ?: return Result.Failure(DominioError.MedioNoEncontrado)
        return when (medio.estadoSubida) {
            EstadoSubida.FALLIDA -> medioRepository.actualizarEstadoSubida(medioId, EstadoSubida.PENDIENTE)
            EstadoSubida.PENDIENTE -> Result.Success(medio)
            EstadoSubida.EN_PROGRESO -> Result.Failure(DominioError.MedioEnSubida)
            EstadoSubida.COMPLETA -> Result.Failure(DominioError.MedioYaSubido)
        }
    }
}