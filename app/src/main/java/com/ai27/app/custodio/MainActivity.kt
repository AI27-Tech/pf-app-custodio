package com.ai27.app.custodio

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ai27.app.custodio.ui.navigation.AppNavHost
import com.ai27.app.custodio.ui.theme.EchoTheme
import com.ai27.app.custodio.ui.theme.LocalCambiarTemaOscuro
import com.ai27.app.custodio.ui.theme.LocalTemaOscuro
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val prefs = remember { getSharedPreferences(NOMBRE_PREFS, Context.MODE_PRIVATE) }
            var oscuro by remember {
                mutableStateOf(prefs.getBoolean(CLAVE_TEMA_OSCURO, OSCURO_POR_DEFECTO))
            }
            CompositionLocalProvider(
                LocalTemaOscuro provides oscuro,
                LocalCambiarTemaOscuro provides { nuevoOscuro ->
                    oscuro = nuevoOscuro
                    prefs.edit().putBoolean(CLAVE_TEMA_OSCURO, nuevoOscuro).apply()
                },
            ) {
                EchoTheme(oscuro = oscuro) {
                    AppNavHost()
                }
            }
        }
    }

    private companion object {
        const val NOMBRE_PREFS = "preferencias"
        const val CLAVE_TEMA_OSCURO = "tema_oscuro"
        const val OSCURO_POR_DEFECTO = true
    }
}