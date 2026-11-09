package com.ai27.app.custodio.data.local.db

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.ai27.app.custodio.data.mapper.aDominio
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `MIGRACION_5_6` (`docs/PLAN_REVELAR_FOTO.md`, Task 2): una base v5 con una foto ya procesada por
 * el flujo viejo debe abrir en v6 con esa foto **revelada** y sin rotación pendiente. Si naciera
 * como cruda, el revelado la volvería a marcar.
 *
 * Mismo enfoque que [MigracionTelefonoACorreoTest]: se siembra el DDL que Room generaba en la v5 y se
 * abre con la configuración de producción.
 */
class MigracionReveladoTest {

    private lateinit var archivoDb: File

    private val ddlReportesV5 = """
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

    private val ddlMediosV5 = """
        CREATE TABLE `medios` (`id` TEXT NOT NULL, `reporteId` TEXT NOT NULL, `tipo` TEXT NOT NULL,
        `origen` TEXT NOT NULL, `createdAtDevice` INTEGER NOT NULL, `contentType` TEXT NOT NULL,
        `estadoSubida` TEXT NOT NULL, `etiquetas` TEXT NOT NULL, `tieneSituacion` INTEGER NOT NULL,
        `localPath` TEXT, `storagePath` TEXT, `thumbnailPath` TEXT, `gpsLat` REAL, `gpsLng` REAL,
        `gpsAccuracyM` REAL, `gpsAgeMs` INTEGER, `ubicacionTexto` TEXT, `comentario` TEXT,
        `sizeBytes` INTEGER, `durationMs` INTEGER, `sha256` TEXT, `ultimoError` TEXT,
        `intentosSubida` INTEGER NOT NULL, PRIMARY KEY(`id`))
    """.trimIndent()

    private fun crearBaseV5() {
        archivoDb = File.createTempFile("migracion-revelado-", ".db").also { it.delete() }
        val connection = BundledSQLiteDriver().open(archivoDb.absolutePath)
        connection.execSQL(ddlReportesV5)
        connection.execSQL(ddlMediosV5)
        connection.execSQL(
            "INSERT INTO reportes (id, placas, placasCapturadas, custodioUid, custodioCorreo, " +
                "clienteId, deviceId, fechaLocal, createdAtDevice, ciclo, subida, completo, " +
                "mediosEsperados, mediosSubidos, fotos, audios, durationMsTotal, schemaVersion, hayNovedad) " +
                "VALUES ('r-1','736-DBK','736-DBK','u-1','custodio@ai27.com','cl-1','d-1','2026-11-06',0,'ACTIVO','PENDIENTE',0,0,0,0,0,0,1,0)",
        )
        // Una foto del 1.0.3 pendiente de subir: JPEG ya marcado por el flujo viejo.
        connection.execSQL(
            "INSERT INTO medios (id, reporteId, tipo, origen, createdAtDevice, contentType, " +
                "estadoSubida, etiquetas, tieneSituacion, localPath, intentosSubida) " +
                "VALUES ('m-1','r-1','FOTO','CAMARA',0,'image/jpeg','PENDIENTE','[]',0,'/medios/m-1',0)",
        )
        connection.execSQL("PRAGMA user_version = 5")
        connection.close()
    }

    @After
    fun limpiar() {
        if (::archivoDb.isInitialized) archivoDb.delete()
    }

    @Test
    fun `una foto de la v5 queda revelada y sin rotacion pendiente`() = runBlocking {
        crearBaseV5()

        val db = EchoDatabase.crearBaseDatos(archivoDb.absolutePath)
        try {
            val medio = db.medioDao().obtenerPorId("m-1")?.aDominio()
            assertNotNull("La migración 5→6 debe conservar el medio", medio)
            assertTrue("Una foto del flujo viejo ya está revelada", medio!!.revelada)
            assertEquals(0, medio.rotacionGrados)
            assertEquals("image/jpeg", medio.contentType)
        } finally {
            db.close()
        }
    }

    @Test
    fun `las columnas nuevas existen con su default`() = runBlocking {
        crearBaseV5()

        val db = EchoDatabase.crearBaseDatos(archivoDb.absolutePath)
        db.medioDao().obtenerPorId("m-1")
        db.close()

        assertEquals("1", defaultDe("revelada"))
        assertEquals("0", defaultDe("rotacionGrados"))
    }

    private fun defaultDe(columna: String): String? =
        BundledSQLiteDriver().open(archivoDb.absolutePath).use { connection ->
            connection.prepare("SELECT dflt_value FROM pragma_table_info('medios') WHERE name = ?").use { st ->
                st.bindText(1, columna)
                if (st.step()) st.getText(0) else null
            }
        }
}
