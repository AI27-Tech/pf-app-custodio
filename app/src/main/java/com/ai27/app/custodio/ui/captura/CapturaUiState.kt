package com.ai27.app.custodio.ui.captura

import androidx.annotation.StringRes
import com.ai27.app.custodio.domain.model.CategoriaEtiqueta
import com.ai27.app.custodio.domain.model.Custodio
import com.ai27.app.custodio.domain.model.Etiqueta
import com.ai27.app.custodio.domain.model.Medio
import com.ai27.app.custodio.domain.model.Reporte

data class CapturaUiState(
    val custodio: Custodio? = null,
    // R2 del Grupo 3: mientras la raíz resuelve el primer valor real de reporteYMedios, el
    // hub no debe saltar a "Sin reporte activo"; `cargando` separa ese caso de "no hay viaje".
    val cargando: Boolean = true,
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
    val audioPausado: Boolean = false,
    val segundosAudio: Long = 0L,
    val medioPendienteDeEtiquetar: Medio? = null,
    val ubicacionTextoEscrita: String = "",
    val avisoEncolado: Int = 0,
    /**
     * Última Situación confirmada del viaje (D-B1/U2): la hoja la hereda de arranque para
     * que con suerte baste un toque. Solo Situación —la Evidencia es opcional y no se presuma.
     */
    val ultimaSituacion: List<String> = emptyList(),
    /** Se encontró una nota a medias de un proceso que murió: hay que decirlo (R3 de §13). */
    val avisoNotaPerdida: Boolean = false,
    @StringRes val error: Int? = null,
) {
    val limiteAlcanzado: Boolean
        get() = medios.size >= LIMITE_MEDIOS_REJILLA

    /**
     * Evidencia capturada que quedó **sin situación** (hallazgo E3).
     *
     * Ocurre cuando el custodio cierra la hoja de clasificación con la X, o cuando el
     * proceso muere con la hoja abierta: el medio ya está persistido pero sin etiqueta de
     * Situación. Desde §13 la cola de subida excluye esos medios —subirlos daría al
     * analista evidencia que no puede filtrar—, así que la app tiene que enseñarlos y
     * dejar clasificarlos después. Se deriva de [medios] en lugar de consultar Room aparte:
     * la lista ya está aquí y está acotada a 50.
     */
    val mediosSinEtiquetar: List<Medio>
        get() {
            val idsSituacion = etiquetas
                .filter { it.activo && it.categoria == CategoriaEtiqueta.SITUACION }
                .map { it.id }
                .toSet()
            return medios.filter { medio -> medio.etiquetas.none { it in idsSituacion } }
        }

    companion object {
        const val LIMITE_MEDIOS_REJILLA = 50
    }
}