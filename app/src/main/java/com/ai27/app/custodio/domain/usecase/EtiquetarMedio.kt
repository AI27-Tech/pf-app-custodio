package com.ai27.app.custodio.domain.usecase

import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.error.DominioError
import com.ai27.app.custodio.domain.model.Medio
import com.ai27.app.custodio.domain.repository.EtiquetaRepository
import com.ai27.app.custodio.domain.repository.MedioRepository

class EtiquetarMedio(
    private val medioRepository: MedioRepository,
    private val etiquetaRepository: EtiquetaRepository,
) {
    suspend operator fun invoke(
        medioId: String,
        etiquetaId: String,
        ubicacionTexto: String? = null,
    ): Result<Medio> {
        val valida = etiquetaRepository.obtener().any { it.id == etiquetaId && it.activo }
        if (!valida) {
            return Result.Failure(DominioError.EtiquetaInvalida(etiquetaId))
        }
        return medioRepository.etiquetar(medioId, etiquetaId, ubicacionTexto)
    }
}