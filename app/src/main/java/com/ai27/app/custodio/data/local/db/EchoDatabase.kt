package com.ai27.app.custodio.data.local.db

import android.content.Context
import androidx.room3.ColumnTypeConverters
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.ai27.app.custodio.data.repository.ETIQUETAS_SITUACION_IDS

@Database(
    entities = [ReporteEntity::class, MedioEntity::class],
    version = 5,
)
@ColumnTypeConverters(EtiquetasConverter::class)
abstract class EchoDatabase : RoomDatabase() {

    abstract fun reporteDao(): ReporteDao
    abstract fun medioDao(): MedioDao

    companion object {
        /**
         * Nombre del archivo en disco. **Legacy a propósito**: la app se renombró a Echo, pero
         * Room no tiene forma de renombrar un archivo ya creado, así que cambiarlo haría que
         * cada instalación existente abriera una base vacía y quedaran huérfanos los reportes sin
         * subir y los archivos de `filesDir/medios` que aún no se han enviado. Este nombre va
         * también en `res/xml/backup_rules.xml` y `res/xml/data_extraction_rules.xml`.
         *
         * La razón original era preservar instalaciones ya en campo. A 2026-09-25 no hay ninguna
         * (todas las instalaciones son frescas), así que cambiar el nombre hoy ya no
         * rompe nada. Se conserva solo por coherencia: el nombre no lo ve nadie y cambiarlo
         * obligaría a tocar también las reglas de backup y de extracción de datos.
         */
        const val NOMBRE = "app_custodio.db"

        /**
         * Room 3 no soporta índices parciales en @Entity/@Index (`androidx.room3.Index` no tiene
         * `where`), así que el invariante "un solo reporte ACTIVO por custodio" vive en SQL manual.
         *
         * Pero la validación post-migración compara `indices` contra lo que declaran las entidades
         * (vacío para `reportes`), y un índice sobrante la hace fallar con
         * "Migration didn't properly handle: reportes". Por eso cada migración **dropea** el índice
         * al empezar —así la validación ve `indices = {}`— y `onOpen` lo recrea después de la
         * validación (los callbacks corren posterior al migration+validation). Regresión cubierta en
         * `MigracionIndiceActivoTest`.
         */
        const val INDICE_UNICO_ACTIVO =
            "CREATE UNIQUE INDEX IF NOT EXISTS ux_reportes_activo_por_custodio " +
                "ON reportes(ciclo, custodioUid) WHERE ciclo = 'ACTIVO'"

        private const val DROP_INDICE_UNICO_ACTIVO =
            "DROP INDEX IF EXISTS ux_reportes_activo_por_custodio"

        val callback = object : RoomDatabase.Callback() {
            override suspend fun onCreate(connection: SQLiteConnection) {
                connection.execSQL(INDICE_UNICO_ACTIVO)
            }

            override suspend fun onOpen(connection: SQLiteConnection) {
                connection.execSQL(INDICE_UNICO_ACTIVO)
            }
        }

        /**
         * Primera migración de la app (PLAN §14 D2). Convierte la columna `etiqueta TEXT`
         * en `etiquetas TEXT` (arreglo JSON de un elemento cuando había valor, arreglo
         * vacío cuando era null) y añade el flag denormalizado `tieneSituacion`, calculado
         * según si el valor viejo pertenece a la categoría Situación del catálogo.
         *
         * No hay equipos en campo (decisión B de §14.2), pero se hace bien igual: es la
         * primera vez que la base cambia de versión y la premisa de la app es no perder
         * evidencia.
         */
        val MIGRACION_1_2 = object : Migration(1, 2) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL(DROP_INDICE_UNICO_ACTIVO)
                connection.execSQL(
                    "ALTER TABLE medios ADD COLUMN etiquetas TEXT NOT NULL DEFAULT '[]'",
                )
                connection.execSQL(
                    "ALTER TABLE medios ADD COLUMN tieneSituacion INTEGER NOT NULL DEFAULT 0",
                )
                connection.execSQL(
                    """
                    UPDATE medios
                    SET etiquetas = '["' || etiqueta || '"]'
                    WHERE etiqueta IS NOT NULL
                    """.trimIndent(),
                )
                if (ETIQUETAS_SITUACION_IDS.isNotEmpty()) {
                    val ids = ETIQUETAS_SITUACION_IDS.joinToString("','") { it }
                    connection.execSQL(
                        "UPDATE medios SET tieneSituacion = 1 WHERE etiqueta IN ('$ids')",
                    )
                }
                connection.execSQL("ALTER TABLE medios DROP COLUMN etiqueta")
            }
        }

        /**
         * Segunda migración (PLAN §15 C5): añade el diagnóstico persistido de la subida.
         * `ultimoError` (texto corto del último fallo permanente) e `intentosSubida`
         * (cuántos intentos llevaba). Sin valor anterior, ambas quedan en su default.
         */
        val MIGRACION_2_3 = object : Migration(2, 3) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL(DROP_INDICE_UNICO_ACTIVO)
                connection.execSQL("ALTER TABLE medios ADD COLUMN ultimoError TEXT")
                connection.execSQL("ALTER TABLE medios ADD COLUMN intentosSubida INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Cuarta migración (PHONE_AUTH_PLAN, Task 1): la identidad del custodio pasa de
         * `correo` a `telefono`, renombrando la columna `custodioCorreo` → `custodioTelefono`
         * sin perder el valor (migración no destructiva). `RENAME COLUMN` requiere SQLite
         * ≥ 3.25; el `BundledSQLiteDriver` lo soporta. La columna sigue `NOT NULL`.
         */
        val MIGRACION_3_4 = object : Migration(3, 4) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL(DROP_INDICE_UNICO_ACTIVO)
                connection.execSQL(
                    "ALTER TABLE reportes RENAME COLUMN custodioCorreo TO custodioTelefono",
                )
            }
        }

        /**
         * Quinta migración (plan de correo, Task 8): el corte a correo + contraseña devuelve
         * la identidad al correo. Es el **espejo exacto** de la [MIGRACION_3_4] — mismas
         * palabras, columnas invertidas — y por la misma razón: los reportes ya en cola
         * local guardan quién es el custodio y ese dato no se puede perder. `RENAME COLUMN`
         * conserva el valor, así que no hace falta tocar ni una fila.
         */
        val MIGRACION_4_5 = object : Migration(4, 5) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL(DROP_INDICE_UNICO_ACTIVO)
                connection.execSQL(
                    "ALTER TABLE reportes RENAME COLUMN custodioTelefono TO custodioCorreo",
                )
            }
        }

        fun crearBaseDatos(context: Context): EchoDatabase =
            crearBaseDatos(ruta = context.getDatabasePath(NOMBRE).absolutePath)

        // Ruta explícita para poder usar la misma configuración en pruebas JVM de host.
        fun crearBaseDatos(ruta: String): EchoDatabase =
            Room.databaseBuilder(name = ruta) { EchoDatabase_Impl() }
                .setDriver(BundledSQLiteDriver())
                .addCallback(callback)
                .addMigrations(MIGRACION_1_2, MIGRACION_2_3, MIGRACION_3_4, MIGRACION_4_5)
                .build()
    }
}