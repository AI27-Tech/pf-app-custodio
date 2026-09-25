package com.ai27.app.custodio.di

import android.content.Context
import com.ai27.app.custodio.data.local.CatalogoEtiquetasOffline
import com.ai27.app.custodio.data.local.archivo.AlmacenArchivos
import com.ai27.app.custodio.data.local.archivo.FilesDirAlmacenArchivos
import com.ai27.app.custodio.data.local.db.EchoDatabase
import com.ai27.app.custodio.data.remote.FirestoreCatalogoEtiquetasRepositorio
import com.ai27.app.custodio.data.repository.CacheEtiquetasRepositorio
import com.ai27.app.custodio.data.repository.RoomMedioRepository
import com.ai27.app.custodio.data.repository.RoomReporteRepository
import com.ai27.app.custodio.domain.repository.EtiquetaRepository
import com.ai27.app.custodio.domain.repository.MedioRepository
import com.ai27.app.custodio.domain.repository.ReporteRepository
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun proveeBaseDatos(@ApplicationContext context: Context): EchoDatabase =
        EchoDatabase.crearBaseDatos(context)

    @Provides
    @Singleton
    fun proveeReporteRepository(database: EchoDatabase): ReporteRepository =
        RoomReporteRepository(database)

    @Provides
    @Singleton
    fun proveeMedioRepository(
        database: EchoDatabase,
        almacenArchivos: AlmacenArchivos,
        etiquetaRepository: EtiquetaRepository,
    ): MedioRepository = RoomMedioRepository(database, almacenArchivos, etiquetaRepository)

    @Provides
    @Singleton
    fun proveeEtiquetaRepository(
        @ApplicationContext context: Context,
        firestore: FirebaseFirestore,
    ): EtiquetaRepository = CacheEtiquetasRepositorio(
        remoto = FirestoreCatalogoEtiquetasRepositorio(firestore),
        almacen = CatalogoEtiquetasOffline(
            context.getSharedPreferences("catalogo_etiquetas", Context.MODE_PRIVATE),
        ),
    )

    @Provides
    @Singleton
    fun proveeAlmacenArchivos(@ApplicationContext context: Context): AlmacenArchivos =
        FilesDirAlmacenArchivos(File(context.filesDir, "medios"))
}