package com.ai27.app.custodio.di

import com.ai27.app.custodio.data.conectividad.ConectividadAndroidMonitor
import com.ai27.app.custodio.data.conectividad.ConectividadMonitor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Enlaza la abstracción [ConectividadMonitor] con su implementación Android basada en
 * [android.net.ConnectivityManager]. La interfaz vive en `data/conectividad` para que los
 * ViewModels y los tests no dependan de Android.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ConectividadModule {

    @Binds
    abstract fun enlazaMonitor(
        android: ConectividadAndroidMonitor,
    ): ConectividadMonitor
}
