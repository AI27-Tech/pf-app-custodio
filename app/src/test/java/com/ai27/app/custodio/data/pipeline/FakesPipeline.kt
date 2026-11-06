package com.ai27.app.custodio.data.pipeline

import com.ai27.app.custodio.data.local.archivo.AlmacenArchivos
import com.ai27.app.custodio.data.remote.MedioRemoto
import com.ai27.app.custodio.data.remote.ReporteRemoto
import com.ai27.app.custodio.data.remote.RutasRemotas
import com.ai27.app.custodio.data.remote.RutasStorage
import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.error.DominioError
import com.ai27.app.custodio.domain.model.Ciclo
import com.ai27.app.custodio.domain.model.EstadoSubida
import com.ai27.app.custodio.domain.model.Medio
import com.ai27.app.custodio.domain.model.Reporte

class FakeReporteRemoto(
    val ciclos: MutableMap<String, Ciclo?> = mutableMapOf(),
) : ReporteRemoto {
    var debeFallarObtencion = false
    var debeFallarEscritura = false
    var creaciones = 0
    var cierres = 0
    var descartes = 0
    var actualizacionesContadores = 0

    override suspend fun obtenerCiclo(reporteId: String): Result<Ciclo?> =
        if (debeFallarObtencion) {
            Result.Failure(DominioError.SinConexion)
        } else {
            Result.Success(ciclos[reporteId])
        }

    override suspend fun crear(reporte: Reporte): Result<Unit> = escribir {
        ciclos[reporte.id] = Ciclo.ACTIVO
        creaciones++
    }

    override suspend fun cerrar(reporte: Reporte): Result<Unit> = escribir {
        ciclos[reporte.id] = Ciclo.CERRADO
        cierres++
    }

    override suspend fun descartar(reporte: Reporte): Result<Unit> = escribir {
        ciclos[reporte.id] = Ciclo.DESCARTADO
        descartes++
    }

    override suspend fun actualizarContadores(
        reporteId: String,
        estado: EstadoSubida,
        mediosSubidos: Int,
        completo: Boolean,
    ): Result<Unit> {
        // Proxy del invariante real "doc remoto con mediosEsperados=null": en producción un
        // reporte ACTIVO siempre tiene esperados=null (nace así y solo se fija al cerrar), y
        // la regla contadoresValidos.completoCoherente rechaza completo=true mientras esperados
        // sea null. La regla mira esperados, no el ciclo; aquí ACTIVO lo representa.
        if (ciclos[reporteId] == Ciclo.ACTIVO && completo) {
            return Result.Failure(DominioError.PermisoDenegado)
        }
        return escribir { actualizacionesContadores++ }
    }

    private inline fun escribir(bloque: () -> Unit): Result<Unit> =
        if (debeFallarEscritura) {
            Result.Failure(DominioError.SinConexion)
        } else {
            bloque()
            Result.Success(Unit)
        }
}

class FakeMedioRemoto(
    val docsCreados: MutableList<String> = mutableListOf(),
    val subidos: MutableList<String> = mutableListOf(),
    /** El medio tal como llegó a cada llamada: permite afirmar qué se intentó publicar. */
    val mediosSubidos: MutableList<Medio> = mutableListOf(),
    val mediosDeDocumento: MutableList<Medio> = mutableListOf(),
) : MedioRemoto {
    var debeFallarSubida = false
    var debeFallarDocumento = false

    override suspend fun subirArchivo(
        reporte: Reporte,
        medio: Medio,
        contenido: ByteArray,
        miniatura: ByteArray?,
    ): Result<RutasRemotas> = if (debeFallarSubida) {
        Result.Failure(DominioError.SinConexion)
    } else {
        subidos.add(medio.id)
        mediosSubidos.add(medio)
        Result.Success(RutasStorage.de(medio))
    }

    override suspend fun crearDocumentoSiNoExiste(
        reporte: Reporte,
        medio: Medio,
        rutas: RutasRemotas,
    ): Result<Unit> {
        mediosDeDocumento.add(medio)
        return if (debeFallarDocumento) {
            Result.Failure(DominioError.SinConexion)
        } else {
            docsCreados.add(medio.id)
            Result.Success(Unit)
        }
    }
}

class FakeAlmacenArchivos(
    val archivos: MutableMap<String, ByteArray> = mutableMapOf(),
) : AlmacenArchivos {
    /** Cuando es distinto de null, `guardar` falla con ese error (para probar E4). */
    var fallaAlGuardar: DominioError? = null

    override suspend fun guardar(nombre: String, contenido: ByteArray): Result<String> {
        fallaAlGuardar?.let { return Result.Failure(it) }
        archivos[nombre] = contenido
        return Result.Success(nombre)
    }

    override suspend fun leer(ruta: String): ByteArray? = archivos[ruta]

    override suspend fun borrar(ruta: String) {
        archivos.remove(ruta)
    }
}