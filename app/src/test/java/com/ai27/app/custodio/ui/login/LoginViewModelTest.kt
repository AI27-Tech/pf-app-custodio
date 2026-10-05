package com.ai27.app.custodio.ui.login

import com.ai27.app.custodio.FakeAllowlistRepositorio
import com.ai27.app.custodio.FakeRegistroDeEventos
import com.ai27.app.custodio.FakeSesionRepositorio
import com.ai27.app.custodio.MainDispatcherRule
import com.ai27.app.custodio.R
import com.ai27.app.custodio.data.diagnostico.MetodoAuth
import com.ai27.app.custodio.data.diagnostico.ResultadoLogin
import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.error.DominioError
import com.ai27.app.custodio.domain.model.Custodio
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Login de una sola fase por correo + contraseña (plan de correo, Task 7) + allowlist
 * (opción B).
 *
 * No hay `Activity` en la frontera ya no: el ViewModel no la recibe, y con ella
 * desaparecieron las fases, el OTP y la cuenta regresiva de reenvío.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var sesion: FakeSesionRepositorio
    private lateinit var allowlist: FakeAllowlistRepositorio
    private lateinit var eventos: FakeRegistroDeEventos

    @Before
    fun setUp() {
        sesion = FakeSesionRepositorio(custodio = null)
        allowlist = FakeAllowlistRepositorio()
        eventos = FakeRegistroDeEventos()
    }

    private fun crearViewModel() = LoginViewModel(
        sesionRepositorio = sesion,
        allowlistRepositorio = allowlist,
        registroDeEventos = eventos,
    )

    private fun LoginViewModel.escribirCredenciales(
        correo: String = "custodio@ai27.com",
        contrasena: String = "Bravo-1-ai27",
    ) {
        onCorreoCambio(correo)
        onContrasenaCambio(contrasena)
    }

    @Test
    fun `el par tecleado se manda tal cual, sin normalizar`() = runTest(mainDispatcherRule.dispatcher) {
        val vm = crearViewModel()
        backgroundScope.launch { vm.uiState.collect {} }

        vm.escribirCredenciales()
        vm.iniciarSesion()
        runCurrent()

        assertEquals(
            listOf("custodio@ai27.com" to "Bravo-1-ai27"),
            sesion.intentos,
        )
    }

    @Test
    fun `correo mal formado muestra error sin llamar al backend`() = runTest(mainDispatcherRule.dispatcher) {
        val vm = crearViewModel()
        backgroundScope.launch { vm.uiState.collect {} }

        vm.escribirCredenciales(correo = "custodio.ai27.com")
        vm.iniciarSesion()

        assertEquals(R.string.error_correo_invalido, vm.uiState.value.error)
        assertTrue("no se intenta autenticar", sesion.intentos.isEmpty())
    }

    @Test
    fun `la allowlist rechaza al custodio y cierra la sesion recien abierta`() =
        runTest(mainDispatcherRule.dispatcher) {
            sesion.custodioAlAutenticar = Custodio("u-1", "huerfano@ai27.com")
            allowlist.respuesta = Result.Success(false)
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }

            vm.escribirCredenciales()
            vm.iniciarSesion()
            runCurrent()

            assertEquals(
                "el correo que quedó autenticado es el que se consulta",
                listOf("huerfano@ai27.com"),
                allowlist.consultados,
            )
            assertEquals("se cierra la sesión no autorizada", 1, sesion.cierres.size)
            assertEquals(R.string.error_correo_no_autorizado, vm.uiState.value.error)
            assertTrue("se libera el bloqueo de autenticación", !vm.uiState.value.autenticando)
        }

    @Test
    fun `la allowlist autoriza al custodio y el login queda en cero errores`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }

            vm.escribirCredenciales()
            vm.iniciarSesion()
            runCurrent()

            assertTrue("ningún cierre", sesion.cierres.isEmpty())
            assertNull(vm.uiState.value.error)
        }

    @Test
    fun `falla la consulta de allowlist y se muestra el error de red`() =
        runTest(mainDispatcherRule.dispatcher) {
            allowlist.respuesta = Result.Failure(DominioError.SinConexion)
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }

            vm.escribirCredenciales()
            vm.iniciarSesion()
            runCurrent()

            assertEquals(R.string.error_sin_conexion, vm.uiState.value.error)
            assertTrue("sin red no se cierra la sesión", sesion.cierres.isEmpty())
        }

    @Test
    fun `credenciales incorrectas se muestran como error de credenciales`() =
        runTest(mainDispatcherRule.dispatcher) {
            sesion.errorAlAutenticar = DominioError.CredencialesInvalidas
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }

            vm.escribirCredenciales()
            vm.iniciarSesion()
            runCurrent()

            assertEquals(R.string.error_credenciales_invalidas, vm.uiState.value.error)
            assertTrue("no se consulta la allowlist de uno que no autenticó", allowlist.consultados.isEmpty())
        }

    // --- Analítica del login (docs/PLAN_ANALYTICS_LOGIN.md, Tarea 5) ---
    // Lo que se afirma aquí es que el ViewModel llama al registro con el par correcto.
    // Que el evento llegue a la consola NO lo cubre ninguna prueba: Analytics no tiene
    // emulador.

    @Test
    fun `el login exitoso registra un unico login con method EMAIL`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }

            vm.escribirCredenciales()
            vm.iniciarSesion()
            runCurrent()

            assertEquals(
                listOf(MetodoAuth.EMAIL to ResultadoLogin.OK),
                eventos.registrados,
            )
        }

    @Test
    fun `la allowlist rechazada registra correo_no_autorizado y ningun ok`() =
        runTest(mainDispatcherRule.dispatcher) {
            allowlist.respuesta = Result.Success(false)
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }

            vm.escribirCredenciales()
            vm.iniciarSesion()
            runCurrent()

            assertEquals(
                listOf(MetodoAuth.EMAIL to ResultadoLogin.CORREO_NO_AUTORIZADO),
                eventos.registrados,
            )
            assertEquals(
                "el fallo más caro no puede venir acompañado de un ok",
                0,
                eventos.registrados.count { it.second == ResultadoLogin.OK },
            )
        }

    @Test
    fun `el correo invalido se registra y no se intenta autenticar`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }

            vm.escribirCredenciales(correo = "custodio@ai27")
            vm.iniciarSesion()

            assertEquals(
                listOf(MetodoAuth.EMAIL to ResultadoLogin.CORREO_INVALIDO),
                eventos.registrados,
            )
            assertTrue("no se intenta autenticar", sesion.intentos.isEmpty())
        }

    @Test
    fun `las credenciales incorrectas se registran como credenciales_invalidas`() =
        runTest(mainDispatcherRule.dispatcher) {
            sesion.errorAlAutenticar = DominioError.CredencialesInvalidas
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }

            vm.escribirCredenciales()
            vm.iniciarSesion()
            runCurrent()

            assertEquals(
                listOf(MetodoAuth.EMAIL to ResultadoLogin.CREDENCIALES_INVALIDAS),
                eventos.registrados,
            )
        }

    @Test
    fun `la consulta a la allowlist sin red se registra como sin_conexion`() =
        runTest(mainDispatcherRule.dispatcher) {
            allowlist.respuesta = Result.Failure(DominioError.SinConexion)
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }

            vm.escribirCredenciales()
            vm.iniciarSesion()
            runCurrent()

            assertEquals(
                listOf(MetodoAuth.EMAIL to ResultadoLogin.SIN_CONEXION),
                eventos.registrados,
            )
        }

    // --- El predicado de formato, que decide si se gasta una llamada al backend ---

    @Test
    fun `el validador de correo acepta direcciones normales y rechaza los typos obvios`() {
        assertTrue(esCorreoValido("custodio@ai27.com"))
        assertTrue(esCorreoValido("bravo.01@sub.ai27.com.mx"))
        assertTrue("sin arroba", !esCorreoValido("custodio.ai27.com"))
        assertTrue("sin dominio", !esCorreoValido("custodio@ai27"))
        assertTrue("sin parte local", !esCorreoValido("@ai27.com"))
        assertTrue("con espacios", !esCorreoValido("custodio @ai27.com"))
        assertTrue("vacío", !esCorreoValido(""))
    }
}
