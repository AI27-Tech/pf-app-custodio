package com.ai27.app.custodio.di

import android.content.Context
import android.provider.Settings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

object IdentidadNombres {
    const val DEVICE_ID = "deviceId"
}

@Module
@InstallIn(SingletonComponent::class)
object IdentidadModule {

    @Provides
    @Singleton
    @Named(IdentidadNombres.DEVICE_ID)
    fun proveeDeviceId(@ApplicationContext context: Context): String {
        val id = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID,
        )
        return id?.takeIf { it.isNotBlank() } ?: "device-desconocido"
    }
}