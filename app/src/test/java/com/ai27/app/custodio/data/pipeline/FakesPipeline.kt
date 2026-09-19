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
    ): Result<Unit> = escribir { actualizacionesContadores++ }

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
        Result.Success(RutasStorage.de(medio))
    }

    override suspend fun crearDocumentoSiNoExiste(
        reporte: Reporte,
        medio: Medio,
        rutas: RutasRemotas,
    ): Result<Unit> = if (debeFallarDocumento) {
        Result.Failure(DominioError.SinConexion)
    } else {
        docsCreados.add(medio.id)
        Result.Success(Unit)
    }
}

class FakeAlmacenArchivos(
    val archivos: MutableMap<String, ByteArray> = mutableMapOf(),
) : AlmacenArchivos {
    override suspend fun guardar(nombre: String, contenido: ByteArray): String {
        archivos[nombre] = contenido
        return nombre
    }

    override suspend fun leer(ruta: String): ByteArray? = archivos[ruta]

    override suspend fun borrar(ruta: String) {
        archivos.remove(ruta)
    }
}