package com.ai27.app.custodio.domain.usecase

import com.ai27.app.custodio.domain.model.Etiqueta
import com.ai27.app.custodio.domain.repository.EtiquetaRepository

class ObtenerEtiquetas(
    private val etiquetaRepository: EtiquetaRepository,
) {
    suspend operator fun invoke(): List<Etiqueta> = etiquetaRepository.obtener()
}