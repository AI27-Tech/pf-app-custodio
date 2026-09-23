package com.ai27.app.custodio.di

import android.content.Context
import com.ai27.app.custodio.data.local.archivo.AlmacenArchivos
import com.ai27.app.custodio.data.local.archivo.FilesDirAlmacenArchivos
import com.ai27.app.custodio.data.local.db.AppCustodioDatabase
import com.ai27.app.custodio.data.remote.FirestoreCatalogoEtiquetasRepositorio
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
    fun proveeBaseDatos(@ApplicationContext context: Context): AppCustodioDatabase =
        AppCustodioDatabase.crearBaseDatos(context)

    @Provides
    @Singleton
    fun proveeReporteRepository(database: AppCustodioDatabase): ReporteRepository =
        RoomReporteRepository(database)

    @Provides
    @Singleton
    fun proveeMedioRepository(
        database: AppCustodioDatabase,
        almacenArchivos: AlmacenArchivos,
        etiquetaRepository: EtiquetaRepository,
    ): MedioRepository = RoomMedioRepository(database, almacenArchivos, etiquetaRepository)

    @Provides
    @Singleton
    fun proveeEtiquetaRepository(firestore: FirebaseFirestore): EtiquetaRepository =
        FirestoreCatalogoEtiquetasRepositorio(firestore)

    @Provides
    @Singleton
    fun proveeAlmacenArchivos(@ApplicationContext context: Context): AlmacenArchivos =
        FilesDirAlmacenArchivos(File(context.filesDir, "medios"))
}