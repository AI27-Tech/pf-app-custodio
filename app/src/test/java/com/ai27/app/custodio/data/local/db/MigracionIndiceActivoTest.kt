package com.ai27.app.custodio.data.local.db

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regresión del hallazgo de dispositivo (2026-09-22, pendientes S1/S2): abrir una base v1 real
 * (con el índice único parcial `ux_reportes_activo_por_custodio` sobre `reportes`) crasheaba con
 * "Migration didn't properly handle: reportes", porque la validación de Room compara `indices`
 * contra lo que declaran las entidades (vacío para `reportes`) y el índice manual —creado en el
 * `onOpen` de la versión anterior— aparecía en la base.
 *
 * Es el mismo patrón que [MigracionDiagnosticoSubidaTest], pero con el DDL v1 real del dispositivo:
 * `reportes` con el índice parcial y `medios` con la columna vieja `etiqueta`.
 */
class MigracionIndiceActivoTest {

    private lateinit var archivoDb: File

    private val ddlReportesV1 = """
        CREATE TABLE `reportes` (`id` TEXT NOT NULL, `placas` TEXT NOT NULL,
        `placasCapturadas` TEXT NOT NULL, `custodioUid` TEXT NOT NULL,
        `custodioCorreo` TEXT NOT NULL, `clienteId` TEXT NOT NULL, `deviceId` TEXT NOT NULL,
        `fechaLocal` TEXT NOT NULL, `createdAtDevice` INTEGER NOT NULL, `ciclo` TEXT NOT NULL,
        `subida` TEXT NOT NULL, `completo` INTEGER NOT NULL, `mediosEsperados` INTEGER,
        `mediosSubidos` INTEGER NOT NULL, `fotos` INTEGER NOT NULL, `audios` INTEGER NOT NULL,
        `durationMsTotal` INTEGER NOT NULL, `schemaVersion` INTEGER NOT NULL, `closedAt` INTEGER,
        `comentario` TEXT, `hayNovedad` INTEGER NOT NULL, `motivoDescarte` TEXT,
        PRIMARY KEY(`id`))
    """.trimIndent()

    private val ddlMediosV1 = """
        CREATE TABLE `medios` (`id` TEXT NOT NULL, `reporteId` TEXT NOT NULL, `tipo` TEXT NOT NULL,
        `origen` TEXT NOT NULL, `createdAtDevice` INTEGER NOT NULL, `contentType` TEXT NOT NULL,
        `estadoSubida` TEXT NOT NULL, `etiqueta` TEXT, `localPath` TEXT, `storagePath` TEXT,
        `thumbnailPath` TEXT, `gpsLat` REAL, `gpsLng` REAL, `gpsAccuracyM` REAL, `gpsAgeMs` INTEGER,
        `ubicacionTexto` TEXT, `comentario` TEXT, `sizeBytes` INTEGER, `durationMs` INTEGER,
        `sha256` TEXT, PRIMARY KEY(`id`))
    """.trimIndent()

    private fun crearBaseV1() {
        archivoDb = File.createTempFile("migracion-indice-", ".db").also { it.delete() }
        val connection = BundledSQLiteDriver().open(archivoDb.absolutePath)
        connection.execSQL(ddlReportesV1)
        connection.execSQL(ddlMediosV1)
        // El índice parcial que crea el onOpen de la versión anterior quedaba persistido en disco:
        connection.execSQL(AppCustodioDatabase.INDICE_UNICO_ACTIVO)
        connection.execSQL(
            "INSERT INTO reportes (id, placas, placasCapturadas, custodioUid, custodioCorreo, " +
                "clienteId, deviceId, fechaLocal, createdAtDevice, ciclo, subida, completo, " +
                "mediosEsperados, mediosSubidos, fotos, audios, durationMsTotal, schemaVersion, hayNovedad) " +
                "VALUES ('r-1','','','u-1','a@b.c','cl-1','d-1','2026-09-22',0,'ACTIVO','SIN_SUBIR',0,0,0,0,0,0,0,0)",
        )
        connection.execSQL(
            "INSERT INTO medios (id, reporteId, tipo, origen, createdAtDevice, contentType, estadoSubida, etiqueta) " +
                "VALUES ('m-1','r-1','FOTO','CAMARA',0,'image/jpeg','PENDIENTE','sin-novedad')",
        )
        connection.execSQL("PRAGMA user_version = 1")
        connection.close()
    }

    @After
    fun limpiar() {
        if (::archivoDb.isInitialized) archivoDb.delete()
    }

    private fun tenerIndice(): Boolean {
        val connection = BundledSQLiteDriver().open(archivoDb.absolutePath)
        return connection.prepare(
            "SELECT COUNT(*) FROM sqlite_master WHERE type='index' " +
                "AND name='ux_reportes_activo_por_custodio'",
        ).use { st ->
            st.step()
            st.getLong(0) == 1L
        }.also { connection.close() }
    }

    @Test
    fun `una base v1 real con el indice parcial abre y migra`() = runBlocking {
        crearBaseV1()

        val db = AppCustodioDatabase.crearBaseDatos(archivoDb.absolutePath)
        try {
            val reporte = db.reporteDao().obtenerPorId("r-1")
            assertNotNull("La migración debe recuperar el reporte ACTIVO", reporte)
            assertEquals("ACTIVO", reporte!!.ciclo)

            val medio = db.medioDao().obtenerPorId("m-1")
            assertNotNull(medio)
            assertEquals(listOf("sin-novedad"), medio!!.etiquetas)
            assertEquals(true, medio.tieneSituacion)
        } finally {
            db.close()
        }
    }

    @Test
    fun `tras migrar el indice parcial vuelve a existir`() = runBlocking {
        crearBaseV1()

        val db = AppCustodioDatabase.crearBaseDatos(archivoDb.absolutePath)
        db.close()

        assertTrue("onOpen debe recrear ux_reportes_activo_por_custodio", tenerIndice())
    }
}