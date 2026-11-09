package com.ai27.app.custodio.data.pipeline

import com.ai27.app.custodio.data.diagnostico.OrigenRevelado
import com.ai27.app.custodio.data.diagnostico.RegistroDeEventos
import com.ai27.app.custodio.data.diagnostico.RegistroDeFallos
import com.ai27.app.custodio.data.foto.RevelarFoto
import com.ai27.app.custodio.data.foto.aEvento
import com.ai27.app.custodio.data.local.archivo.AlmacenArchivos
import com.ai27.app.custodio.data.remote.MedioRemoto
import com.ai27.app.custodio.data.remote.ReporteRemoto
import com.ai27.app.custodio.data.remote.RutasStorage
import com.ai27.app.custodio.data.util.Sha256
import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.error.DominioError
// RETIRO_ETIQUETADO (Task 2): CategoriaEtiqueta solo lo usaba el candado de subida, hoy
// comentado. Se deja comentado —no borrado— para reactivarlo con el etiquetado.
// import com.ai27.app.custodio.domain.model.CategoriaEtiqueta
import com.ai27.app.custodio.domain.model.Ciclo
import com.ai27.app.custodio.domain.model.EstadoSubida
import com.ai27.app.custodio.domain.model.Medio
import com.ai27.app.custodio.domain.model.Reporte
import com.ai27.app.custodio.domain.model.SubidaReporte
import com.ai27.app.custodio.domain.model.TipoMedio
import com.ai27.app.custodio.domain.repository.EtiquetaRepository
import com.ai27.app.custodio.domain.repository.MedioRepository
import com.ai27.app.custodio.domain.repository.ReporteRepository
import java.time.Duration
import java.time.Instant

// Pasos de la subida de un medio (PLAN §6.2): asegura el documento del reporte,
// sube miniatura y archivo a Storage, escribe el documento en medios, marca COMPLETA,
// borra el archivo local y avanza los contadores. Idempotente por diseño.
//
// Red de seguridad de PLAN_REVELAR_FOTO (D6): nunca sube una foto cruda. Si encuentra una (el
// proceso murió antes de que la cámara la revelara), la revela aquí sin mapa y sube el resultado.
class SubidaMedioProceso(
    private val reporteRepository: ReporteRepository,
    private val medioRepository: MedioRepository,
    // RETIRO_ETIQUETADO (Task 2): etiquetaRepository queda inyectado pero SIN uso mientras
    // el candado de subida está retirado. Se conserva en el constructor para reactivar el
    // candado descomentando, sin tocar la composición ni los call sites.
    @Suppress("unused") private val etiquetaRepository: EtiquetaRepository,
    private val reporteRemoto: ReporteRemoto,
    private val medioRemoto: MedioRemoto,
    private val almacenArchivos: AlmacenArchivos,
    // El mismo @Singleton que usa la cámara (FotoModule): su candado evita el doble revelado.
    private val revelarFoto: RevelarFoto,
    private val registroDeEventos: RegistroDeEventos,
    private val registroDeFallos: RegistroDeFallos,
    private val ahora: () -> Instant = Instant::now,
) {
    suspend fun ejecutar(reporteId: String, medioId: String): Result<Unit> {
        val encontrado = medioRepository.obtenerPorId(medioId)
            ?: return Result.Success(Unit) // se borró antes de subir
        // RETIRO_ETIQUETADO (Task 2): se retira el candado de subida. Antes un medio sin
        // etiqueta de Situación no salía nunca del dispositivo. Hoy la captura sube de
        // inmediato sin clasificar. El bloque queda comentado (no borrado) para reactivarlo
        // junto con el etiquetado; el contrato de datos (etiquetas, catalogos/etiquetas) no
        // cambia, así que no hay nada que rehacer al descomentar.
        // val catalogo = etiquetaRepository.obtener()
        // val esSituacion = medio.etiquetas.any { id ->
        //     catalogo.any { it.id == id && it.categoria == CategoriaEtiqueta.SITUACION }
        // }
        // if (!esSituacion) return Result.Success(Unit)
        val reporte = reporteRepository.obtenerPorId(reporteId)
            ?: return Result.Success(Unit)
        if (reporte.ciclo == Ciclo.DESCARTADO) return Result.Success(Unit) // sin evidencia futura a un viaje descartado
        if (encontrado.estadoSubida == EstadoSubida.COMPLETA) {
            // C1: un reintento encuentra el medio ya COMPLETA (la subida se hizo pero el
            // avance de contadores quedó fallido/descartado). No se vuelve a subir: solo
            // se recalculan los contadores del reporte para sanar la divergencia.
            return propagarContadores(reporteId)
        }

        // Antes de asegurarReporte y de marcarEnProgreso: guardarRevelado solo escribe en PENDIENTE.
        val medio = if (encontrado.tipo == TipoMedio.FOTO && !encontrado.revelada) {
            // Reintento y no Success: si la pantalla se cerró, el revelado de la cámara se canceló
            // con su viewModelScope y nadie más volvería a encolar esta foto. El backoff la trae de
            // vuelta ya vieja; si la cámara sí terminó, el reintento la encuentra revelada.
            if (laCamaraLaEstaRevelando(encontrado)) return Result.Failure(DominioError.FotoSinRevelar)
            when (val revelada = revelarAntesDeSubir(medioId)) {
                null -> return Result.Success(Unit) // se borró mientras se revelaba
                is Result.Failure -> return revelada
                is Result.Success -> revelada.value
            }
        } else {
            encontrado
        }

        val aseguro = asegurarReporte(reporte)
        when (aseguro) {
            null -> return Result.Success(Unit) // reporte descartado en el servidor: no se sube
            is Result.Failure -> {
                marcarFallida(medioId)
                return aseguro
            }
            is Result.Success -> marcarEnProgreso(medioId)
        }

        val contenido = leerContenido(medioId)
            ?: return fracasoSinContenido(medioId)
        val miniatura = medio.thumbnailPath?.let { almacenArchivos.leer(it) }?.let(::miniaturaSubible)
        val sha = Sha256.digestHex(contenido)
        // El documento remoto se construye con el sha ya calculado: sin esto, el medio que
        // se escribió en Firestore lleva `sha256 = null` y, como `medios` es create-only
        // en las reglas, el valor queda irrellenable para siempre (hallazgo C2).
        val conSha = medio.copy(sha256 = sha)

        val rutas = when (val r = medioRemoto.subirArchivo(reporte, conSha, contenido, miniatura)) {
            is Result.Success -> r.value
            is Result.Failure -> {
                marcarFallida(medioId)
                return r
            }
        }
        when (val r = medioRemoto.crearDocumentoSiNoExiste(reporte, conSha, rutas)) {
            is Result.Success -> Unit
            is Result.Failure -> {
                marcarFallida(medioId)
                return r
            }
        }

        when (val r = medioRepository.marcarSubidaCompleta(medioId, rutas.media, sha)) {
            is Result.Success -> Unit
            is Result.Failure -> {
                marcarFallida(medioId)
                return r
            }
        }

        // El resultado se PROPAGA: si la escritura remota de contadores falla, el worker
        // reintenta y el reporte no queda marcado COMPLETA localmente sin el remoto (C1).
        return propagarContadores(reporteId)
    }

    // La cámara revela tras el GPS (≤ 8 s) y el mapa (≤ 6 s) y encola su propia subida. Si el
    // worker llega antes (p. ej. una reconciliación), no se adelanta: revelaría sin mapa una foto
    // que iba a tener mapa. Si el proceso murió en la ventana, la siguiente reconciliación la
    // encuentra vieja. Una edad negativa (el reloj se atrasó) no cuenta como joven, para que la
    // foto no espere horas; el candado de RevelarFoto evita el doble revelado igual.
    private fun laCamaraLaEstaRevelando(medio: Medio): Boolean {
        val edad = Duration.between(medio.createdAtDevice, ahora())
        return !edad.isNegative && edad < VENTANA_REVELADO_CAMARA
    }

    // D6: revela con fecha y coordenadas, sin mapa. Devuelve el medio releído (con el contentType,
    // sizeBytes y archivo revelados), `null` si se borró, o Failure si sigue crudo: nunca se sube
    // una foto sin revelar. No marca nada: el medio sigue PENDIENTE para el siguiente intento.
    private suspend fun revelarAntesDeSubir(medioId: String): Result<Medio>? {
        revelarFoto.revelar(medioId, conMapa = false)
            .aEvento(OrigenRevelado.SUBIDA, msDisparo = null)
            ?.let(registroDeEventos::fotoRevelada)
        val releido = medioRepository.obtenerPorId(medioId) ?: return null
        if (releido.tipo != TipoMedio.FOTO || releido.revelada) return Result.Success(releido)
        // Sin archivo no hay nada que revelar ni que subir: es el fallo permanente de siempre (C5).
        if (leerContenido(medioId) == null) return fracasoSinContenido(medioId)
        registroDeFallos.registrar("subida_foto_sin_revelar", IllegalStateException("La foto sigue cruda tras revelar"))
        return Result.Failure(DominioError.FotoSinRevelar)
    }

    // Una miniatura que no es WebP ni JPEG no se sube (thumbnailPath = null): la evidencia no
    // se bloquea por la miniatura, pero el fallo queda registrado.
    private fun miniaturaSubible(bytes: ByteArray): ByteArray? {
        if (RutasStorage.contentTypeMiniatura(bytes) != null) return bytes
        registroDeFallos.registrar("subida_miniatura_formato", IllegalStateException("Miniatura que no es WebP ni JPEG"))
        return null
    }

    // Devuelve false cuando el ciclo remoto impide subir (reporte descartado).
    private suspend fun asegurarReporte(reporte: Reporte): Result<Unit>? {
        val cicloRemoto = when (val r = reporteRemoto.obtenerCiclo(reporte.id)) {
            is Result.Success -> r.value
            is Result.Failure -> return r
        }
        return when (cicloRemoto) {
            null -> reporteRemoto.crear(reporte.copy(ciclo = Ciclo.ACTIVO))
            Ciclo.ACTIVO, Ciclo.CERRADO -> Result.Success(Unit)
            Ciclo.DESCARTADO -> null // no se sube evidencia a un viaje descartado
        }
    }

    private suspend fun marcarEnProgreso(medioId: String) {
        if (medioRepository.obtenerPorId(medioId)?.estadoSubida != EstadoSubida.EN_PROGRESO) {
            medioRepository.actualizarEstadoSubida(medioId, EstadoSubida.EN_PROGRESO)
        }
    }

    private suspend fun marcarFallida(medioId: String) {
        medioRepository.actualizarEstadoSubida(medioId, EstadoSubida.FALLIDA)
    }

    private suspend fun leerContenido(medioId: String): ByteArray? {
        val ruta = medioRepository.obtenerPorId(medioId)?.localPath ?: return null
        return almacenArchivos.leer(ruta)
    }

    private suspend fun fracasoSinContenido(medioId: String): Result.Failure {
        marcarFallida(medioId)
        // Permanente (C5): el archivo no va a reaparecer, reintentar no tiene sentido.
        return Result.Failure(DominioError.ArchivoLocalAusente)
    }

    // Recalcula los contadores del reporte y los propaga remoto-primero. Firestore hace un
    // merge monótono (C3), así que las escrituras concurrentes de otros medios no se pisan.
    // Solo si la escritura remota gana se refleja el estado en Room: Room nunca queda
    // adelantado respecto al servidor (C1) — si falla, el worker reintenta en la rama
    // COMPLETA de `ejecutar`.
    private suspend fun propagarContadores(reporteId: String): Result<Unit> {
        val reporte = reporteRepository.obtenerPorId(reporteId) ?: return Result.Success(Unit)
        if (reporte.ciclo == Ciclo.DESCARTADO) return Result.Success(Unit)
        val medios = medioRepository.obtenerDelReporte(reporteId)
        val estado = SubidaReporte.computarEstado(medios)
        val subidos = SubidaReporte.mediosSubidos(medios)
        val completo = SubidaReporte.completo(reporte, medios)
        val remoto = reporteRemoto.actualizarContadores(reporteId, estado, subidos, completo)
        if (remoto is Result.Failure) return remoto
        reporteRepository.actualizarSubida(reporteId, estado, subidos, completo)
        return Result.Success(Unit)
    }

    companion object {
        /** GPS (8 s) + mapa (6 s) de la cámara, más margen para el decode y la escritura. */
        val VENTANA_REVELADO_CAMARA: Duration = Duration.ofSeconds(20)
    }
}
