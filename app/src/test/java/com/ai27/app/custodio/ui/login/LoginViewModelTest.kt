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
import com.ai27.app.custodio.domain.model.EventoVerificacion
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
 * Flujo de login por SMS (PHONE_AUTH_PLAN Task 5) + allowlist (Task 4, opción B).
 *
 * El ViewModel recibe un `Activity?` que en estas pruebas de JVM es null: el fake de
 * sesión lo ignora y el dominio lo documenta.
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

    @Test
    fun `diez digitos se normalizan con prefijo 52 y pasan a la fase de codigo`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }

            vm.onTelefonoCambio("55 4033 8899")
            vm.enviarCodigo(null)
            runCurrent()

            assertEquals(FaseLogin.ESPERANDO_CODIGO, vm.uiState.value.fase)
            assertEquals("inicia la cuenta regresiva en 60", 60, vm.uiState.value.segundosParaReenviar)
            assertNull(vm.uiState.value.error)
        }

    @Test
    fun `el SMS se pide con el prefijo mas 52 ya incluido`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }

            vm.onTelefonoCambio("5540338899")
            vm.enviarCodigo(null)
            runCurrent()

            assertEquals(listOf("+525540338899"), sesion.telefonosSolicitados)
        }

    @Test
    fun `un 52 sobrando tecleado por el usuario se descarta sin duplicarlo`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }

            // El usuario alcanza a teclear +52 por su cuenta y luego los 10 dígitos.
            vm.onTelefonoCambio("+525540338899")
            vm.enviarCodigo(null)
            runCurrent()

            assertEquals("se descarta el país repetido", "+525540338899", sesion.telefonosSolicitados.single())
            assertEquals("el campo solo conserva los 10 dígitos", "5540338899", vm.uiState.value.telefonoEscrito)
        }

    @Test
    fun `numero invalido muestra error sin pedir SMS`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }

            vm.onTelefonoCambio("123")
            vm.enviarCodigo(null)

            assertEquals(R.string.error_numero_invalido, vm.uiState.value.error)
            assertEquals(FaseLogin.INGRESANDO_NUMERO, vm.uiState.value.fase)
            assertTrue("no se pide SMS", sesion.telefonosSolicitados.isEmpty())
        }

    @Test
    fun `la allowlist rechaza al custodio y cierra la sesion recien abierta`() =
        runTest(mainDispatcherRule.dispatcher) {
            sesion.alEnviarSms = {
                EventoVerificacion.VerificacionAutomatica(Custodio("u-1", "+525544880512"))
            }
            allowlist.respuesta = Result.Success(false)
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }

            vm.onTelefonoCambio("5540338899")
            vm.enviarCodigo(null)
            runCurrent()

            assertEquals("el número que quedó autenticado es el que se consulta", listOf("+525544880512"), allowlist.consultados)
            assertEquals("se cierra la sesión no autorizada", 1, sesion.cierres.size)
            assertEquals(R.string.error_tel_no_autorizado, vm.uiState.value.error)
            assertEquals(FaseLogin.INGRESANDO_NUMERO, vm.uiState.value.fase)
        }

    @Test
    fun `la allowlist autoriza al custodio y el login queda en cero errores`() =
        runTest(mainDispatcherRule.dispatcher) {
            sesion.alEnviarSms = {
                EventoVerificacion.VerificacionAutomatica(Custodio("u-1", "+525544880512"))
            }
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }

            vm.onTelefonoCambio("5540338899")
            vm.enviarCodigo(null)
            runCurrent()

            assertTrue("ningún cierre", sesion.cierres.isEmpty())
            assertNull(vm.uiState.value.error)
        }

    @Test
    fun `falla la consulta de allowlist y se muestra el error de red`() =
        runTest(mainDispatcherRule.dispatcher) {
            sesion.alEnviarSms = {
                EventoVerificacion.VerificacionAutomatica(Custodio("u-1", "+525544880512"))
            }
            allowlist.respuesta = Result.Failure(DominioError.SinConexion)
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }

            vm.onTelefonoCambio("5540338899")
            vm.enviarCodigo(null)
            runCurrent()

            assertEquals(R.string.error_sin_conexion, vm.uiState.value.error)
            assertTrue("sin red no se cierra la sesión", sesion.cierres.isEmpty())
        }

    @Test
    fun `regresar y reintentar el mismo numero usa reenvio en lugar de envio fresco`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }

            vm.onTelefonoCambio("5540338899")
            vm.enviarCodigo(null)
            runCurrent()
            assertEquals(FaseLogin.ESPERANDO_CODIGO, vm.uiState.value.fase)

            vm.regresarANumero()
            vm.onTelefonoCambio("5540338899")
            vm.enviarCodigo(null)
            runCurrent()

            assertEquals("solo un envío fresco", 1, sesion.telefonosSolicitados.size)
            assertEquals("el reintento es un reenvío de la misma sesión", 1, sesion.reenviosSolicitados.size)
            assertEquals("se conserva la verificación", "vid-1", sesion.reenviosSolicitados.single())
            assertEquals(FaseLogin.ESPERANDO_CODIGO, vm.uiState.value.fase)
        }

    @Test
    fun `regresar y cambiar el numero hace un envio fresco con el prefijo nuevo`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }

            vm.onTelefonoCambio("5540338899")
            vm.enviarCodigo(null)
            runCurrent()

            vm.regresarANumero()
            vm.onTelefonoCambio("5548162705")
            vm.enviarCodigo(null)
            runCurrent()

            assertEquals(
                listOf("+525540338899", "+525548162705"),
                sesion.telefonosSolicitados,
            )
            assertTrue("nada que reenviar", sesion.reenviosSolicitados.isEmpty())
        }

    @Test
    fun `regresar al numero conserva el telefono y limpia la fase de codigo`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }

            vm.onTelefonoCambio("5540338899")
            vm.enviarCodigo(null)
            runCurrent()
            assertEquals(FaseLogin.ESPERANDO_CODIGO, vm.uiState.value.fase)

            vm.regresarANumero()

            assertEquals(FaseLogin.INGRESANDO_NUMERO, vm.uiState.value.fase)
            assertEquals("se conserva el número para corregirlo", "5540338899", vm.uiState.value.telefonoEscrito)
            assertEquals("", vm.uiState.value.codigoEscrito)
            assertEquals(0, vm.uiState.value.segundosParaReenviar)
            assertNull(vm.uiState.value.error)
        }

    @Test
    fun `seis digitos verifican el codigo y autentican`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }

            vm.onTelefonoCambio("5540338899")
            vm.enviarCodigo(null)
            runCurrent()

            vm.onCodigoCambio("123456")
            runCurrent()

            assertEquals("el custodio autenticado coincide", "+525544880512", sesion.custodioAlVerificar.telefono)
            assertNull(vm.uiState.value.error)
            assertEquals(FaseLogin.ESPERANDO_CODIGO, vm.uiState.value.fase)
        }

    // --- Analítica del login (docs/PLAN_ANALYTICS_LOGIN.md, Tarea 5) ---
    // Lo que se afirma aquí es que el ViewModel llama al registro con el par correcto.
    // Que el evento llegue a la consola NO lo cubre ninguna prueba: Analytics no tiene
    // emulador.

    @Test
    fun `el login exitoso registra un unico login con method PHONE`() =
        runTest(mainDispatcherRule.dispatcher) {
            sesion.alEnviarSms = {
                EventoVerificacion.VerificacionAutomatica(Custodio("u-1", "+525544880512"))
            }
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }

            vm.onTelefonoCambio("5540338899")
            vm.enviarCodigo(null)
            runCurrent()

            assertEquals(
                listOf(MetodoAuth.PHONE to ResultadoLogin.OK),
                eventos.registrados,
            )
        }

    @Test
    fun `la allowlist rechazada registra telefono_no_autorizado y ningun ok`() =
        runTest(mainDispatcherRule.dispatcher) {
            sesion.alEnviarSms = {
                EventoVerificacion.VerificacionAutomatica(Custodio("u-1", "+525544880512"))
            }
            allowlist.respuesta = Result.Success(false)
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }

            vm.onTelefonoCambio("5540338899")
            vm.enviarCodigo(null)
            runCurrent()

            assertEquals(
                listOf(MetodoAuth.PHONE to ResultadoLogin.TELEFONO_NO_AUTORIZADO),
                eventos.registrados,
            )
            assertEquals(
                "el fallo más caro no puede venir acompañado de un ok",
                0,
                eventos.registrados.count { it.second == ResultadoLogin.OK },
            )
        }

    @Test
    fun `el numero invalido se registra y no se pide el SMS`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }

            vm.onTelefonoCambio("123")
            vm.enviarCodigo(null)

            assertEquals(
                listOf(MetodoAuth.PHONE to ResultadoLogin.NUMERO_INVALIDO),
                eventos.registrados,
            )
            assertTrue("no se pide SMS", sesion.telefonosSolicitados.isEmpty())
        }

    @Test
    fun `el broker de GMS mudo se registra como sin_respuesta_sms`() =
        runTest(mainDispatcherRule.dispatcher) {
            sesion.alEnviarSms = { EventoVerificacion.Fallo(DominioError.SinRespuestaSms) }
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }

            vm.onTelefonoCambio("5540338899")
            vm.enviarCodigo(null)
            runCurrent()

            assertEquals(
                listOf(MetodoAuth.PHONE to ResultadoLogin.SIN_RESPUESTA_SMS),
                eventos.registrados,
            )
        }

    @Test
    fun `el codigo incorrecto se registra como codigo_invalido`() =
        runTest(mainDispatcherRule.dispatcher) {
            sesion.errorAlVerificar = DominioError.CodigoInvalido
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }

            vm.onTelefonoCambio("5540338899")
            vm.enviarCodigo(null)
            runCurrent()

            vm.onCodigoCambio("123456")
            runCurrent()

            assertEquals(
                listOf(MetodoAuth.PHONE to ResultadoLogin.CODIGO_INVALIDO),
                eventos.registrados,
            )
        }

    @Test
    fun `la consulta a la allowlist sin red se registra como sin_conexion`() =
        runTest(mainDispatcherRule.dispatcher) {
            sesion.alEnviarSms = {
                EventoVerificacion.VerificacionAutomatica(Custodio("u-1", "+525544880512"))
            }
            allowlist.respuesta = Result.Failure(DominioError.SinConexion)
            val vm = crearViewModel()
            backgroundScope.launch { vm.uiState.collect {} }

            vm.onTelefonoCambio("5540338899")
            vm.enviarCodigo(null)
            runCurrent()

            assertEquals(
                listOf(MetodoAuth.PHONE to ResultadoLogin.SIN_CONEXION),
                eventos.registrados,
            )
        }
}