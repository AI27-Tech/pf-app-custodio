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
 * Regresión del PHONE_AUTH_PLAN Task 1: la identidad del custodio pasa de `correo` a
 * `telefono`. `MIGRACION_3_4` renombra la columna `custodioCorreo` → `custodioTelefono`
 * sin perder el valor viejo (migración no destructiva).
 *
 * Se siembra una base con el DDL que Room v3 generaba para `reportes`/`medios`, se abre
 * con la configuración de producción (migraciones 1→2, 2→3, 3→4) y se verifica que el
 * dato quedó en la columna nueva y que la vieja ya no existe.
 */
class MigracionCorreoATelefonoTest {

    private lateinit var archivoDb: File

    private val ddlReportesV3 = """
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

    private val ddlMediosV3 = """
        CREATE TABLE `medios` (`id` TEXT NOT NULL, `reporteId` TEXT NOT NULL, `tipo` TEXT NOT NULL,
        `origen` TEXT NOT NULL, `createdAtDevice` INTEGER NOT NULL, `contentType` TEXT NOT NULL,
        `estadoSubida` TEXT NOT NULL, `etiquetas` TEXT NOT NULL, `tieneSituacion` INTEGER NOT NULL,
        `localPath` TEXT, `storagePath` TEXT, `thumbnailPath` TEXT, `gpsLat` REAL, `gpsLng` REAL,
        `gpsAccuracyM` REAL, `gpsAgeMs` INTEGER, `ubicacionTexto` TEXT, `comentario` TEXT,
        `sizeBytes` INTEGER, `durationMs` INTEGER, `sha256` TEXT, `ultimoError` TEXT,
        `intentosSubida` INTEGER NOT NULL, PRIMARY KEY(`id`))
    """.trimIndent()

    private fun crearBaseV3() {
        archivoDb = File.createTempFile("migracion-telefono-", ".db").also { it.delete() }
        val connection = BundledSQLiteDriver().open(archivoDb.absolutePath)
        connection.execSQL(ddlReportesV3)
        connection.execSQL(ddlMediosV3)
        connection.execSQL(
            "INSERT INTO reportes (id, placas, placasCapturadas, custodioUid, custodioCorreo, " +
                "clienteId, deviceId, fechaLocal, createdAtDevice, ciclo, subida, completo, " +
                "mediosEsperados, mediosSubidos, fotos, audios, durationMsTotal, schemaVersion, hayNovedad) " +
                "VALUES ('r-1','736-DBK','736-DBK','u-1','+525544880512','cl-1','d-1','lun 21 sep',0,'ACTIVO','PENDIENTE',0,0,0,0,0,0,0,0)",
        )
        connection.execSQL("PRAGMA user_version = 3")
        connection.close()
    }

    @After
    fun limpiar() {
        if (::archivoDb.isInitialized) archivoDb.delete()
    }

    private fun leerTelefono(): String? = BundledSQLiteDriver().open(archivoDb.absolutePath).use { connection ->
        connection.prepare("SELECT custodioTelefono FROM reportes WHERE id = 'r-1'").use { st ->
            st.step()
            st.getText(0)
        }
    }

    @Test
    fun `una base v3 migra a la columna custodioTelefono conservando el valor`() = runBlocking {
        crearBaseV3()

        val db = AppCustodioDatabase.crearBaseDatos(archivoDb.absolutePath)
        try {
            val reporte = db.reporteDao().obtenerPorId("r-1")
            assertNotNull("La migración 3→4 debe recuperar el reporte", reporte)
            assertEquals("El teléfono viejo (correo) queda en custodioTelefono", "+525544880512", reporte!!.custodioTelefono)

            assertFalse("Ya no existe la columna custodioCorreo", tieneColumna("custodioCorreo"))
            assertTrue("Existe la columna custodioTelefono", tieneColumna("custodioTelefono"))
        } finally {
            db.close()
        }
    }

    @Test
    fun `tras migrar la lectura por SQL devuelve el valor renombrado`() = runBlocking {
        crearBaseV3()

        // Room abre BAJO DEMANDA: la migración corre al tocar la base, no al construirla.
        val db = AppCustodioDatabase.crearBaseDatos(archivoDb.absolutePath)
        db.reporteDao().obtenerPorId("r-1")
        db.close()

        assertEquals("+525544880512", leerTelefono())
    }

    private fun tieneColumna(nombre: String): Boolean {
        val connection = BundledSQLiteDriver().open(archivoDb.absolutePath)
        return connection.prepare("SELECT name FROM pragma_table_info('reportes') WHERE name = ?").use { st ->
            st.bindText(1, nombre)
            st.step() && st.getText(0) == nombre
        }.also { connection.close() }
    }
}