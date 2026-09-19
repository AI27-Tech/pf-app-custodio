package com.ai27.app.custodio.domain.repository

import com.ai27.app.custodio.domain.model.Etiqueta

interface EtiquetaRepository {
    suspend fun obtener(): List<Etiqueta>
}