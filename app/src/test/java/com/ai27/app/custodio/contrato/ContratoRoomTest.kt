package com.ai27.app.custodio.contrato

import com.ai27.app.custodio.data.local.archivo.FilesDirAlmacenArchivos
import com.ai27.app.custodio.data.local.db.AppCustodioDatabase
import com.ai27.app.custodio.data.repository.RoomMedioRepository
import com.ai27.app.custodio.data.repository.RoomReporteRepository
import com.ai27.app.custodio.domain.repository.MedioRepository
import com.ai27.app.custodio.domain.repository.ReporteRepository
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Before

/**
 * El contrato contra las implementaciones **reales** de Room, sobre SQLite nativo
 * (`BundledSQLiteDriver`) y un directorio temporal de archivos. Es la definición de
 * verdad: si esta clase y [ContratoFakesTest] discrepan, el fake es el que está mal.
 */
class ContratoRoomTest : ContratoRepositoriosTest() {

    private lateinit var database: AppCustodioDatabase
    private lateinit var archivoDb: File
    private lateinit var directorioMedios: File

    override lateinit var medios: MedioRepository
    override lateinit var reportes: ReporteRepository

    @Before
    fun preparar() {
        archivoDb = File.createTempFile("contrato-", ".db").also { it.delete() }
        database = AppCustodioDatabase.crearBaseDatos(archivoDb.absolutePath)
        directorioMedios = Files.createTempDirectory("contrato-medios").toFile()
        reportes = RoomReporteRepository(database)
        medios = RoomMedioRepository(database, FilesDirAlmacenArchivos(directorioMedios), com.ai27.app.custodio.FakeEtiquetaRepository())
    }

    @After
    fun limpiar() {
        database.close()
        archivoDb.delete()
        directorioMedios.deleteRecursively()
    }
}
