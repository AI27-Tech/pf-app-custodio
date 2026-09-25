package com.ai27.app.custodio.data.local.db

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Prueba la segunda migración (PLAN §15 C5): añade el diagnóstico persistido de la subida
 * (`ultimoError`, `intentosSubida`) a `medios`, sin tocar el resto de columnas.
 *
 * Mismo patrón que [MigracionEtiquetasTest]: se construye la base con el DDL que Room v2
 * generaba para [MedioEntity], se siembran filas y se aplica [EchoDatabase.MIGRACION_2_3].
 */
class MigracionDiagnosticoSubidaTest {

    private lateinit var archivoDb: File
    private lateinit var connection: SQLiteConnection

    private val ddlV2 = """
        CREATE TABLE IF NOT EXISTS medios (
            id TEXT NOT NULL,
            reporteId TEXT NOT NULL,
            tipo TEXT NOT NULL,
            origen TEXT NOT NULL,
            createdAtDevice INTEGER NOT NULL,
            contentType TEXT NOT NULL,
            estadoSubida TEXT NOT NULL,
            etiquetas TEXT NOT NULL,
            tieneSituacion INTEGER NOT NULL,
            localPath TEXT,
            storagePath TEXT,
            thumbnailPath TEXT,
            gpsLat REAL,
            gpsLng REAL,
            gpsAccuracyM REAL,
            gpsAgeMs INTEGER,
            ubicacionTexto TEXT,
            comentario TEXT,
            sizeBytes INTEGER,
            durationMs INTEGER,
            sha256 TEXT,
            PRIMARY KEY(id)
        )
    """.trimIndent()

    @Before
    fun preparar() {
        archivoDb = File.createTempFile("migracion-diagnostico-", ".db").also { it.delete() }
        connection = BundledSQLiteDriver().open(archivoDb.absolutePath)
        connection.execSQL(ddlV2)
        connection.execSQL("PRAGMA user_version = 2")
    }

    @After
    fun limpiar() {
        connection.close()
        archivoDb.delete()
    }

    private fun insertarMedio(id: String, sha: String?) {
        connection.execSQL(
            "INSERT INTO medios " +
                "(id, reporteId, tipo, origen, createdAtDevice, contentType, estadoSubida, etiquetas, tieneSituacion, sha256) " +
                "VALUES ('$id','r-1','FOTO','CAMARA',0,'image/jpeg','PENDIENTE','[\"sin-novedad\"]',1," +
                if (sha == null) "NULL)" else "'$sha')",
        )
    }

    private fun tenerColumna(nombre: String): Boolean =
        connection.prepare("SELECT name FROM pragma_table_info('medios') WHERE name = ?").use { st ->
            st.bindText(1, nombre)
            st.step() && st.getText(0) == nombre
        }

    private fun leerDiagnostico(id: String): Pair<String?, Long> = connection.prepare(
        "SELECT ultimoError, intentosSubida FROM medios WHERE id = ?",
    ).use { st ->
        st.bindText(1, id)
        assertTrue("Debe existir el medio $id", st.step())
        // Este driver devuelve "" para NULL en getText.
        st.getText(0)?.takeIf { it.isNotEmpty() } to st.getLong(1)
    }

    @Test
    fun `agrega ultimoError e intentosSubida sin perder lo existente`() = runBlocking {
        insertarMedio("m-1", "sha-previo")

        EchoDatabase.MIGRACION_2_3.migrate(connection)

        assertTrue(tenerColumna("ultimoError"))
        assertTrue(tenerColumna("intentosSubida"))
        // El resto de la fila sigue intacta:
        assertEquals(
            "sha-previo",
            connection.prepare("SELECT sha256 FROM medios WHERE id = ?").use { st ->
                st.bindText(1, "m-1")
                assertTrue(st.step())
                st.getText(0)
            },
        )
        assertNull(connection.prepare("SELECT storagePath FROM medios WHERE id = ?").use { st ->
            st.bindText(1, "m-1")
            assertTrue(st.step())
            st.getText(0)?.takeIf { it.isNotEmpty() }
        })
    }

    @Test
    fun `un medio sin sha también migra con sus defaults`() = runBlocking {
        insertarMedio("m-2", null)

        EchoDatabase.MIGRACION_2_3.migrate(connection)

        assertEquals(null to 0L, leerDiagnostico("m-2"))
    }
}