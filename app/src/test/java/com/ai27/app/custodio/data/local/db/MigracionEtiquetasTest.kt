package com.ai27.app.custodio.data.local.db

import androidx.sqlite.execSQL
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Prueba la primera migración de la app (PLAN §14 D2): de `etiqueta TEXT` a
 * `etiquetas TEXT` (arreglo JSON) más el flag denormalizado `tieneSituacion`.
 *
 * Sin exportar esquema de Room (decisión de §14.6), el camino es directo: se construye
 * una base con el DDL que Room v1 generaba para [MedioEntity], se siembran filas y se le
 * aplica [AppCustodioDatabase.MIGRACION_1_2] por la API pública. Así la migración se
 * prueba contra datos reales sin depender de la validación interna de Room.
 */
class MigracionEtiquetasTest {

    private lateinit var archivoDb: File
    private lateinit var connection: SQLiteConnection

    private val ddlV1 = """
        CREATE TABLE IF NOT EXISTS medios (
            id TEXT NOT NULL,
            reporteId TEXT NOT NULL,
            tipo TEXT NOT NULL,
            origen TEXT NOT NULL,
            createdAtDevice INTEGER NOT NULL,
            contentType TEXT NOT NULL,
            estadoSubida TEXT NOT NULL,
            etiqueta TEXT,
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
        archivoDb = File.createTempFile("migracion-etiquetas-", ".db").also { it.delete() }
        connection = BundledSQLiteDriver().open(archivoDb.absolutePath)
        connection.execSQL(ddlV1)
        connection.execSQL("PRAGMA user_version = 1")
    }

    @After
    fun limpiar() {
        connection.close()
        archivoDb.delete()
    }

    private fun insertarMedio(id: String, etiqueta: String?) {
        val valores = if (etiqueta == null) {
            "('$id','r-1','FOTO','CAMARA',0,'image/jpeg','PENDIENTE')"
        } else {
            "('$id','r-1','FOTO','CAMARA',0,'image/jpeg','PENDIENTE','$etiqueta')"
        }
        val columnas = if (etiqueta == null) {
            "(id, reporteId, tipo, origen, createdAtDevice, contentType, estadoSubida)"
        } else {
            "(id, reporteId, tipo, origen, createdAtDevice, contentType, estadoSubida, etiqueta)"
        }
        connection.execSQL("INSERT INTO medios $columnas VALUES $valores")
    }

    private fun tenerColumna(nombre: String): Boolean {
        connection.execSQL("PRAGMA table_info(medios)")
        return connection.prepare("SELECT name FROM pragma_table_info('medios') WHERE name = ?").use { st ->
            st.bindText(1, nombre)
            st.step() && st.getText(0) == nombre
        }
    }

    private fun leerValores(id: String): Pair<String?, Long> = connection.prepare(
        "SELECT etiquetas, tieneSituacion FROM medios WHERE id = ?",
    ).use { st ->
        st.bindText(1, id)
        assertTrue("Debe existir el medio $id", st.step())
        st.getText(0) to st.getLong(1)
    }

    @Test
    fun `agrega etiquetas y tieneSituacion y elimina etiqueta`() = runBlocking {
        AppCustodioDatabase.MIGRACION_1_2.migrate(connection)

        assertTrue(tenerColumna("etiquetas"))
        assertTrue(tenerColumna("tieneSituacion"))
        assertFalse(tenerColumna("etiqueta"))
    }

    @Test
    fun `convierte el valor viejo a un arreglo JSON de un elemento`() = runBlocking {
        insertarMedio("m-1", "sin-novedad")
        insertarMedio("m-2", "dano")

        AppCustodioDatabase.MIGRACION_1_2.migrate(connection)

        assertEquals("[\"sin-novedad\"]" to 1L, leerValores("m-1"))
        assertEquals("[\"dano\"]" to 0L, leerValores("m-2"))
    }

    @Test
    fun `sin valor la lista queda vacia y sin situacion`() = runBlocking {
        insertarMedio("m-3", null)

        AppCustodioDatabase.MIGRACION_1_2.migrate(connection)

        assertEquals("[]" to 0L, leerValores("m-3"))
    }

    @Test
    fun `una evidencia vieja no se marca como situacion`() = runBlocking {
        insertarMedio("m-4", "estado-transporte")

        AppCustodioDatabase.MIGRACION_1_2.migrate(connection)

        assertEquals("[\"estado-transporte\"]" to 0L, leerValores("m-4"))
    }
}