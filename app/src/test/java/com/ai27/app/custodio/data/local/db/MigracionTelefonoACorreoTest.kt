package com.ai27.app.custodio.data.local.db

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regresión del plan de correo, Task 8: la identidad del custodio vuelve de `telefono` a
 * `correo`. `MIGRACION_4_5` renombra la columna `custodioTelefono` → `custodioCorreo` sin
 * perder el valor viejo (migración no destructiva), y es el espejo exacto de la 3→4.
 *
 * Se siembra una base con el DDL que Room generaba para `reportes`/`medios` en la versión 4,
 * se abre con la configuración de producción (migraciones 1→2, 2→3, 3→4, 4→5) y se verifica
 * que el dato quedó en la columna nueva y que la vieja ya no existe.
 */
class MigracionTelefonoACorreoTest {

    private lateinit var archivoDb: File

    private val ddlReportesV4 = """
        CREATE TABLE `reportes` (`id` TEXT NOT NULL, `placas` TEXT NOT NULL,
        `placasCapturadas` TEXT NOT NULL, `custodioUid` TEXT NOT NULL,
        `custodioTelefono` TEXT NOT NULL, `clienteId` TEXT NOT NULL, `deviceId` TEXT NOT NULL,
        `fechaLocal` TEXT NOT NULL, `createdAtDevice` INTEGER NOT NULL, `ciclo` TEXT NOT NULL,
        `subida` TEXT NOT NULL, `completo` INTEGER NOT NULL, `mediosEsperados` INTEGER,
        `mediosSubidos` INTEGER NOT NULL, `fotos` INTEGER NOT NULL, `audios` INTEGER NOT NULL,
        `durationMsTotal` INTEGER NOT NULL, `schemaVersion` INTEGER NOT NULL, `closedAt` INTEGER,
        `comentario` TEXT, `hayNovedad` INTEGER NOT NULL, `motivoDescarte` TEXT,
        PRIMARY KEY(`id`))
    """.trimIndent()

    private val ddlMedios = """
        CREATE TABLE `medios` (`id` TEXT NOT NULL, `reporteId` TEXT NOT NULL, `tipo` TEXT NOT NULL,
        `origen` TEXT NOT NULL, `createdAtDevice` INTEGER NOT NULL, `contentType` TEXT NOT NULL,
        `estadoSubida` TEXT NOT NULL, `etiquetas` TEXT NOT NULL, `tieneSituacion` INTEGER NOT NULL,
        `localPath` TEXT, `storagePath` TEXT, `thumbnailPath` TEXT, `gpsLat` REAL, `gpsLng` REAL,
        `gpsAccuracyM` REAL, `gpsAgeMs` INTEGER, `ubicacionTexto` TEXT, `comentario` TEXT,
        `sizeBytes` INTEGER, `durationMs` INTEGER, `sha256` TEXT, `ultimoError` TEXT,
        `intentosSubida` INTEGER NOT NULL, PRIMARY KEY(`id`))
    """.trimIndent()

    private fun crearBaseV4() {
        archivoDb = File.createTempFile("migracion-correo-", ".db").also { it.delete() }
        val connection = BundledSQLiteDriver().open(archivoDb.absolutePath)
        connection.execSQL(ddlReportesV4)
        connection.execSQL(ddlMedios)
        connection.execSQL(
            "INSERT INTO reportes (id, placas, placasCapturadas, custodioUid, custodioTelefono, " +
                "clienteId, deviceId, fechaLocal, createdAtDevice, ciclo, subida, completo, " +
                "mediosEsperados, mediosSubidos, fotos, audios, durationMsTotal, schemaVersion, hayNovedad) " +
                "VALUES ('r-1','736-DBK','736-DBK','u-1','custodio@ai27.com','cl-1','d-1','lun 21 sep',0,'ACTIVO','PENDIENTE',0,0,0,0,0,0,0,0)",
        )
        connection.execSQL("PRAGMA user_version = 4")
        connection.close()
    }

    @After
    fun limpiar() {
        if (::archivoDb.isInitialized) archivoDb.delete()
    }

    private fun leerCorreo(): String? = BundledSQLiteDriver().open(archivoDb.absolutePath).use { connection ->
        connection.prepare("SELECT custodioCorreo FROM reportes WHERE id = 'r-1'").use { st ->
            st.step()
            st.getText(0)
        }
    }

    @Test
    fun `una base v4 migra a la columna custodioCorreo conservando el valor`() = runBlocking {
        crearBaseV4()

        val db = EchoDatabase.crearBaseDatos(archivoDb.absolutePath)
        try {
            val reporte = db.reporteDao().obtenerPorId("r-1")
            assertNotNull("La migración 4→5 debe recuperar el reporte", reporte)
            assertEquals(
                "El correo (que era teléfono en la v4) queda en custodioCorreo",
                "custodio@ai27.com",
                reporte!!.custodioCorreo,
            )

            assertFalse("Ya no existe la columna custodioTelefono", tieneColumna("custodioTelefono"))
            assertTrue("Existe la columna custodioCorreo", tieneColumna("custodioCorreo"))
        } finally {
            db.close()
        }
    }

    @Test
    fun `tras migrar la lectura por SQL devuelve el valor renombrado`() = runBlocking {
        crearBaseV4()

        // Room abre BAJO DEMANDA: la migración corre al tocar la base, no al construirla.
        val db = EchoDatabase.crearBaseDatos(archivoDb.absolutePath)
        db.reporteDao().obtenerPorId("r-1")
        db.close()

        assertEquals("custodio@ai27.com", leerCorreo())
    }

    private fun tieneColumna(nombre: String): Boolean {
        val connection = BundledSQLiteDriver().open(archivoDb.absolutePath)
        return connection.prepare("SELECT name FROM pragma_table_info('reportes') WHERE name = ?").use { st ->
            st.bindText(1, nombre)
            st.step() && st.getText(0) == nombre
        }.also { connection.close() }
    }
}
