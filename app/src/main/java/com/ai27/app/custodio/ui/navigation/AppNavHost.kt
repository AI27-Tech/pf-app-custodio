package com.ai27.app.custodio.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ai27.app.custodio.ui.captura.CapturaRoute
import com.ai27.app.custodio.ui.theme.AppCustodioTheme

object Rutas {
    const val INICIO = "inicio"
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Rutas.INICIO
    ) {
        composable(Rutas.INICIO) { CapturaRoute() }
    }
}

@Preview(name = "Navegación — tema claro", showBackground = true)
@Composable
private fun AppNavHostPreviewClaro() {
    AppCustodioTheme(darkTheme = false) {
        AppNavHost()
    }
}

@Preview(name = "Navegación — tema oscuro", showBackground = true)
@Composable
private fun AppNavHostPreviewOscuro() {
    AppCustodioTheme(darkTheme = true) {
        AppNavHost()
    }
}