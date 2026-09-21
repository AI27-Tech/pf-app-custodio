package com.ai27.app.custodio.ui.captura

import androidx.annotation.StringRes
import com.ai27.app.custodio.domain.model.Custodio
import com.ai27.app.custodio.domain.model.Etiqueta
import com.ai27.app.custodio.domain.model.Medio
import com.ai27.app.custodio.domain.model.Reporte

data class CapturaUiState(
    val custodio: Custodio? = null,
    val reporte: Reporte? = null,
    val etiquetas: List<Etiqueta> = emptyList(),
    val medios: List<Medio> = emptyList(),
    val placaEscrita: String = "",
    val creandoViaje: Boolean = false,
    val capturando: Boolean = false,
    val cerrandoViaje: Boolean = false,
    val descartandoViaje: Boolean = false,
    val pendientesSinSubir: Int = 0,
    val grabandoAudio: Boolean = false,
    val segundosAudio: Long = 0L,
    val medioPendienteDeEtiquetar: Medio? = null,
    val ubicacionTextoEscrita: String = "",
    @StringRes val error: Int? = null,
) {
    val limiteAlcanzado: Boolean
        get() = medios.size >= LIMITE_MEDIOS_REJILLA

    companion object {
        const val LIMITE_MEDIOS_REJILLA = 50
    }
}