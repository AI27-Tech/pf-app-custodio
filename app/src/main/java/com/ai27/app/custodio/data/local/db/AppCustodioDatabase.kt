package com.ai27.app.custodio.data.local.db

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL

@Database(
    entities = [ReporteEntity::class, MedioEntity::class],
    version = 1,
)
abstract class AppCustodioDatabase : RoomDatabase() {

    abstract fun reporteDao(): ReporteDao
    abstract fun medioDao(): MedioDao

    companion object {
        const val NOMBRE = "app_custodio.db"

        // Room 3 no soporta índices parciales en @Entity/@Index; el invariante
        // "un solo reporte ACTIVO por custodio" se garantiza con un índice único parcial.
        const val INDICE_UNICO_ACTIVO =
            "CREATE UNIQUE INDEX IF NOT EXISTS ux_reportes_activo_por_custodio " +
                "ON reportes(ciclo, custodioUid) WHERE ciclo = 'ACTIVO'"

        val callback = object : RoomDatabase.Callback() {
            override suspend fun onCreate(connection: SQLiteConnection) {
                connection.execSQL(INDICE_UNICO_ACTIVO)
            }

            override suspend fun onOpen(connection: SQLiteConnection) {
                connection.execSQL(INDICE_UNICO_ACTIVO)
            }
        }

        fun crearBaseDatos(context: Context): AppCustodioDatabase =
            crearBaseDatos(ruta = context.getDatabasePath(NOMBRE).absolutePath)

        // Ruta explícita para poder usar la misma configuración en pruebas JVM de host.
        fun crearBaseDatos(ruta: String): AppCustodioDatabase =
            Room.databaseBuilder(name = ruta) { AppCustodioDatabase_Impl() }
                .setDriver(BundledSQLiteDriver())
                .addCallback(callback)
                .build()
    }
}