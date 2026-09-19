package com.ai27.app.custodio.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.hilt.navigation.compose.hiltViewModel
import com.ai27.app.custodio.ui.captura.CapturaRoute
import com.ai27.app.custodio.ui.login.LoginRoute

object Rutas {
    const val LOGIN = "login"
    const val INICIO = "inicio"
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val rootViewModel: RootViewModel = hiltViewModel()
    val estadoRoot by rootViewModel.estado.collectAsState()

    NavHost(
        navController = navController,
        startDestination = Rutas.LOGIN,
    ) {
        composable(Rutas.LOGIN) { LoginRoute() }
        composable(Rutas.INICIO) { CapturaRoute() }
    }

    LaunchedEffect(estadoRoot.estado) {
        val rutaActual = navController.currentDestination?.route
        when (estadoRoot.estado) {
            EstadoSesion.CON_SESION -> if (rutaActual != Rutas.INICIO) {
                navController.navigate(Rutas.INICIO) {
                    popUpTo(Rutas.LOGIN) { inclusive = true }
                }
            }

            EstadoSesion.NO_SESION -> if (rutaActual != Rutas.LOGIN) {
                navController.navigate(Rutas.LOGIN) {
                    popUpTo(Rutas.INICIO) { inclusive = true }
                }
            }

            EstadoSesion.CARGANDO -> Unit
        }
    }

    if (estadoRoot.estado == EstadoSesion.CARGANDO) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}