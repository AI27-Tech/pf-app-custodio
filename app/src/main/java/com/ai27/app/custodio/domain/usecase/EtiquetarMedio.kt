package com.ai27.app.custodio.domain.usecase

import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.error.DominioError
import com.ai27.app.custodio.domain.model.CategoriaEtiqueta
import com.ai27.app.custodio.domain.model.Medio
import com.ai27.app.custodio.domain.repository.EtiquetaRepository
import com.ai27.app.custodio.domain.repository.MedioRepository

class EtiquetarMedio(
    private val medioRepository: MedioRepository,
    private val etiquetaRepository: EtiquetaRepository,
) {
    /**
     * Valida la lista de etiquetas contra el catálogo y la regla de subida del PLAN §14:
     * con dos categorías y selección múltiple, "etiquetado" es **tener al menos una
     * etiqueta de Situación** (decisión A) — la Evidencia es opcional.
     */
    suspend operator fun invoke(
        medioId: String,
        etiquetas: List<String>,
        ubicacionTexto: String? = null,
    ): Result<Medio> {
        val catalogo = etiquetaRepository.obtener()
        val invalida = etiquetas.firstOrNull { id ->
            catalogo.none { it.id == id && it.activo }
        }
        if (invalida != null) {
            return Result.Failure(DominioError.EtiquetaInvalida(invalida))
        }
        if (etiquetas.none { id ->
                catalogo.any { it.id == id && it.categoria == CategoriaEtiqueta.SITUACION }
            }
        ) {
            return Result.Failure(DominioError.FaltaSituacion)
        }
        return medioRepository.etiquetar(medioId, etiquetas.distinct(), ubicacionTexto)
    }
}