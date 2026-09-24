package com.ai27.app.custodio.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ai27.app.custodio.ui.ajustes.AjustesRoute
import com.ai27.app.custodio.ui.captura.CapturaViewModel
import com.ai27.app.custodio.ui.components.BarraNavegacion
import com.ai27.app.custodio.ui.components.DestinoInferior
import com.ai27.app.custodio.ui.historial.DetalleReporteRoute
import com.ai27.app.custodio.ui.historial.HistorialRoute
import com.ai27.app.custodio.ui.login.LoginRoute
import com.ai27.app.custodio.ui.pendientes.PendientesScreen
import com.ai27.app.custodio.ui.pendientes.PendientesViewModel
import com.ai27.app.custodio.ui.viaje.ArchivosScreen
import com.ai27.app.custodio.ui.viaje.AudioScreen
import com.ai27.app.custodio.ui.viaje.CapturaEvidenciaScreen
import com.ai27.app.custodio.ui.viaje.CierreScreen
import com.ai27.app.custodio.ui.viaje.ReporteScreen

/**
 * Rutas de la app. El flujo de captura vive en el grafo anidado [VIAJE]: sus pantallas
 * comparten el [CapturaViewModel] de la entrada del grafo. La barra inferior tiene tres
 * pestañas ([DestinoInferior]); las rutas del grafo de viaje y el detalle se apilan encima
 * sin barra.
 */
object Rutas {
    const val LOGIN = "login"
    const val VIAJE = "viaje"
    const val INICIO = "inicio" // pestaña Reporte
    const val CAPTURA = "captura"
    const val AUDIO = "audio"
    const val ARCHIVOS = "archivos"
    const val CIERRE = "cierre"
    const val PENDIENTES = "pendientes"
    const val HISTORIAL = "historial"
    const val DETALLE = "detalle/{reporteId}"
    const val AJUSTES = "ajustes"

    fun detalle(reporteId: String): String = "detalle/$reporteId"
}

private val rutasAutenticadas = setOf(
    Rutas.INICIO,
    Rutas.CAPTURA,
    Rutas.AUDIO,
    Rutas.ARCHIVOS,
    Rutas.CIERRE,
    Rutas.PENDIENTES,
    Rutas.HISTORIAL,
    Rutas.DETALLE,
    Rutas.AJUSTES,
)

private fun destinoDe(ruta: String?): DestinoInferior? = when (ruta) {
    Rutas.INICIO -> DestinoInferior.REPORTE
    Rutas.HISTORIAL -> DestinoInferior.HISTORIAL
    Rutas.AJUSTES -> DestinoInferior.AJUSTES
    else -> null
}

/**
 * Resuelve la entrada del grafo [Rutas.VIAJE] desde el destino actual. Como todas las
 * pantallas del flujo de viaje son hijas de ese grafo, cuando estamos dentro de él su
 * entrada está siempre en la pila; si el sitio actual es otra pestaña, devuelve null.
 *
 * A diferencia de `getBackStackEntry(Rutas.VIAJE)` en crudo — que recorre la pila viva y
 * revienta con `IllegalArgumentException` durante las transiciones de [NavHost] (destino
 * actual null en recomposiciones de salida) — esta versión vuelve null y las pantallas
 * simplemente no se componen ese frame.
 */
private fun NavController.entradaDelGrafoViaje(entradaActual: NavBackStackEntry?): NavBackStackEntry? {
    val grafo = entradaActual?.destination?.hierarchy
        ?.firstOrNull { it.route == Rutas.VIAJE }
        ?: return null
    return try {
        getBackStackEntry(grafo.id)
    } catch (e: IllegalArgumentException) {
        null
    }
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val rootViewModel: RootViewModel = hiltViewModel()
    val pendientesViewModel: PendientesViewModel = hiltViewModel()
    val estadoRoot by rootViewModel.estado.collectAsState()

    // Tras el primer inicio de sesión se piden TODOS los permisos de captura de una sola
    // vez (ya no al abrir el visor).
    SolicitarPermisosTrasInicioDeSesion(sesionActiva = estadoRoot.estado == EstadoSesion.CON_SESION)
    val pendientes by pendientesViewModel.pendientes.collectAsState()
    val pendientesError by pendientesViewModel.error.collectAsState()
    val pendientesRefrescando by pendientesViewModel.refrescando.collectAsState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val rutaActual = backStackEntry?.destination?.route
    val destinoSeleccionado = destinoDe(rutaActual)
    val entradaViaje = navController.entradaDelGrafoViaje(backStackEntry)

    Scaffold(
        bottomBar = {
            val destino = destinoSeleccionado ?: return@Scaffold
            BarraNavegacion(
                seleccionado = destino,
                pendientesSinSubir = pendientes.size,
                onSeleccionar = { destino ->
                    navegarADestino(
                        navController = navController,
                        destino = destino,
                        rutaActual = rutaActual,
                        alReTocar = { if (it == DestinoInferior.REPORTE) pendientesViewModel.recargar() },
                    )
                },
                modifier = Modifier.navigationBarsPadding(),
            )
        },
        contentWindowInsets = WindowInsets(0.dp),
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Rutas.LOGIN,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            composable(Rutas.LOGIN) { LoginRoute() }

            navigation(startDestination = Rutas.INICIO, route = Rutas.VIAJE) {
                composable(Rutas.INICIO) {
                    val viewModel: CapturaViewModel? = entradaViaje?.let { hiltViewModel(it) }
                    if (viewModel == null) return@composable
                    val estado by viewModel.uiState.collectAsState()
                    ReporteScreen(
                        estado = estado,
                        conectado = rootViewModel.conectado.collectAsState().value,
                        onPlacaCambio = viewModel::onPlacaCambio,
                        onIniciarViaje = viewModel::iniciarViaje,
                        onIrACaptura = { navController.navigate(Rutas.CAPTURA) },
                        onIrAAudio = { navController.navigate(Rutas.AUDIO) },
                        onIrAArchivos = { navController.navigate(Rutas.ARCHIVOS) },
                        onIrACierre = { navController.navigate(Rutas.CIERRE) },
                        onIrAPendientes = { navController.navigate(Rutas.PENDIENTES) },
                        onConsumirError = viewModel::consumirError,
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding(),
                    )
                }

                composable(Rutas.CAPTURA) {
                    val viewModel: CapturaViewModel? = entradaViaje?.let { hiltViewModel(it) }
                    if (viewModel == null) return@composable
                    val estado by viewModel.uiState.collectAsState()
                    CapturaEvidenciaScreen(
                        estado = estado,
                        onAtras = { navController.popBackStack() },
                        onTomarFoto = viewModel::capturarFoto,
                        onFallaDeCaptura = viewModel::fallaDeCaptura,
                        onGuardarEtiqueta = viewModel::guardarEtiqueta,
                        onUbicacionTextoCambio = viewModel::onUbicacionTextoCambio,
                        onCerrarHoja = viewModel::descartarEtiquetado,
                        onConsumirError = viewModel::consumirError,
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding(),
                    )
                }

                composable(Rutas.AUDIO) {
                    val viewModel: CapturaViewModel? = entradaViaje?.let { hiltViewModel(it) }
                    if (viewModel == null) return@composable
                    val estado by viewModel.uiState.collectAsState()
                    AudioScreen(
                        estado = estado,
                        onAtras = { navController.popBackStack() },
                        onIniciarAudio = viewModel::iniciarGrabacionDeAudio,
                        onDetenerAudio = viewModel::detenerGrabacionDeAudio,
                        onPausarAudio = viewModel::pausarGrabacionDeAudio,
                        onReanudarAudio = viewModel::reanudarGrabacionDeAudio,
                        onDescartarAudio = viewModel::descartarAudio,
                        onGuardarEtiqueta = viewModel::guardarEtiqueta,
                        onUbicacionTextoCambio = viewModel::onUbicacionTextoCambio,
                        onCerrarHoja = viewModel::descartarEtiquetado,
                        onConsumirError = viewModel::consumirError,
                        onConsumirAvisoNotaPerdida = viewModel::consumirAvisoNotaPerdida,
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding(),
                    )
                }

                composable(Rutas.ARCHIVOS) {
                    val viewModel: CapturaViewModel? = entradaViaje?.let { hiltViewModel(it) }
                    if (viewModel == null) return@composable
                    val estado by viewModel.uiState.collectAsState()
                    ArchivosScreen(
                        estado = estado,
                        onAtras = { navController.popBackStack() },
                        onBorrarPendiente = viewModel::borrarPendiente,
                        onReintentar = viewModel::reintentar,
                        onRetomarEtiquetado = viewModel::retomarEtiquetado,
                        onGuardarEtiqueta = viewModel::guardarEtiqueta,
                        onUbicacionTextoCambio = viewModel::onUbicacionTextoCambio,
                        onCerrarHoja = viewModel::descartarEtiquetado,
                        onConsumirError = viewModel::consumirError,
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding(),
                    )
                }

                composable(Rutas.CIERRE) {
                    val viewModel: CapturaViewModel? = entradaViaje?.let { hiltViewModel(it) }
                    if (viewModel == null) return@composable
                    val estado by viewModel.uiState.collectAsState()
                    CierreScreen(
                        estado = estado,
                        onAtras = { navController.popBackStack() },
                        onCerrarViaje = viewModel::cerrarViaje,
                        onDescartarViaje = viewModel::descartarViaje,
                        onConsumirError = viewModel::consumirError,
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding(),
                    )
                }
            }

            composable(Rutas.PENDIENTES) {
                PendientesScreen(
                    pendientes = pendientes,
                    error = pendientesError,
                    refrescando = pendientesRefrescando,
                    onRecargar = pendientesViewModel::recargar,
                    onAtras = { navController.popBackStack() },
                    onReintentar = pendientesViewModel::reintentar,
                    onConsumirError = pendientesViewModel::consumirError,
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding(),
                )
            }

            composable(Rutas.HISTORIAL) {
                HistorialRoute(
                    onVerDetalle = { reporteId -> navController.navigate(Rutas.detalle(reporteId)) },
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding(),
                )
            }

            composable(
                route = Rutas.DETALLE,
                arguments = listOf(navArgument("reporteId") { type = NavType.StringType }),
            ) {
                DetalleReporteRoute(
                    onAtras = { navController.popBackStack() },
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding(),
                )
            }

            composable(Rutas.AJUSTES) {
                AjustesRoute(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding(),
                )
            }
        }
    }

    LaunchedEffect(estadoRoot.estado) {
        val rutaActual = navController.currentDestination?.route
        when (estadoRoot.estado) {
            EstadoSesion.CON_SESION -> if (rutaActual == null || rutaActual == Rutas.LOGIN) {
                navController.navigate(Rutas.INICIO) {
                    popUpTo(Rutas.LOGIN) { inclusive = true }
                }
                // G3-A/R1: al arrancar con un viaje activo, la app abre de una en la cámara
                // en lugar de quedarse en el hub. INICIO queda debajo en la pila, así que
                // back vuelve al hub Reporte. La sonda es una consulta única de Room; solo
                // se decide el destino inicial, no se pinta nada con ella.
                val custodio = estadoRoot.custodio
                if (custodio != null && rootViewModel.hayReporteActivo(custodio.uid)) {
                    navController.navigate(Rutas.CAPTURA) {
                        launchSingleTop = true
                    }
                }
            }

            EstadoSesion.NO_SESION -> if (rutaActual != Rutas.LOGIN) {
                navController.navigate(Rutas.LOGIN) {
                    popUpTo(Rutas.VIAJE) { inclusive = true }
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

private fun navegarADestino(
    navController: NavHostController,
    destino: DestinoInferior,
    rutaActual: String?,
    alReTocar: (DestinoInferior) -> Unit = {},
) {
    val ruta = when (destino) {
        DestinoInferior.REPORTE -> Rutas.INICIO
        DestinoInferior.HISTORIAL -> Rutas.HISTORIAL
        DestinoInferior.AJUSTES -> Rutas.AJUSTES
    }
    if (rutaActual == ruta) {
        // Pestaña ya seleccionada: se refresca en vez de re-navegar (Reporte re-alimenta la cola).
        alReTocar(destino)
        return
    }
    // Cada cambio de pestaña se expresa como un navigate atómico (con su popUpTo), no como un
    // popBackStack suelto: así no dependemos de que la ruta del grafo anidado esté presente en
    // la pila en un momento concreto de una transición en curso.
    navController.navigate(ruta) {
        if (destino == DestinoInferior.REPORTE) {
            // Reporte es la raíz del flujo: se vuelve a la entrada del grafo de viaje,
            // descartando las pestañas apiladas encima y conservando el flujo abierto.
            popUpTo(Rutas.VIAJE) { inclusive = false }
        }
        launchSingleTop = true
    }
}