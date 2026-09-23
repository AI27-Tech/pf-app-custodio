package com.ai27.app.custodio.ui.login

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai27.app.custodio.R
import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.model.Custodio
import com.ai27.app.custodio.domain.model.EventoVerificacion
import com.ai27.app.custodio.domain.repository.AllowlistRepositorio
import com.ai27.app.custodio.domain.repository.SesionRepositorio
import com.ai27.app.custodio.ui.error.aRecurso
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Prefijo fijo de México: en la UI se muestra siempre (`login_telefono_prefijo`, debe
 * ir sincronizado) y el usuario solo escribe los 10 dígitos locales. Si el país cambia,
 * se actualizan los dos juntos (más la allowlist/provisión).
 */
internal const val CODIGO_PAIS = "+52"

/** Compone el E.164: exige exactamente 10 dígitos y les antepone el prefijo fijo. */
internal fun componerTelefonoE164(digitos: String): String? =
    if (digitos.length == 10) CODIGO_PAIS + digitos else null

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val sesionRepositorio: SesionRepositorio,
    private val allowlistRepositorio: AllowlistRepositorio,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    /** Datos del SMS en curso; solo viven aquí, las pantallas no los necesitan. */
    private var verificationIdPendiente: String = ""
    private var resendTokenPendiente: Long? = null
    private var telefonoPendiente: String = ""

    fun onTelefonoCambio(telefono: String) {
        var digitos = telefono.filter(Char::isDigit)
        // Si el usuario teclea o pega el +52 (el prefijo ya va fijo en la UI), se descarta:
        // un número real de 10 dígitos nunca empieza con "52" (no existe tal LADA en México),
        // así que cuando SOBRAN dígitos y empiezan con 52 es el código de país metido por error.
        if (digitos.length > 10 && digitos.startsWith("52")) {
            digitos = digitos.drop(2)
        }
        _uiState.update {
            it.copy(
                telefonoEscrito = digitos.take(10),
                error = null,
            )
        }
    }

    fun onCodigoCambio(codigo: String) {
        val depurado = codigo.filter(Char::isDigit).take(6)
        _uiState.update { it.copy(codigoEscrito = depurado, error = null) }
        if (depurado.length == 6 && !_uiState.value.verificando) {
            verificarCodigo(depurado)
        }
    }

    /** El botón "Verificar" (además de la verificación automática al completar 6 dígitos). */
    fun verificarCodigoTecleado() {
        val codigo = _uiState.value.codigoEscrito
        if (codigo.length == 6 && !_uiState.value.verificando) {
            verificarCodigo(codigo)
        }
    }

    /** Pide el SMS. `activity` se usa solo para la auto-lectura / reCAPTCHA de Phone Auth. */
    fun enviarCodigo(activity: Activity?) {
        if (_uiState.value.enviandoSms) return
        val telefono = componerTelefonoE164(_uiState.value.telefonoEscrito)
        if (telefono == null) {
            _uiState.update { it.copy(error = R.string.error_numero_invalido) }
            return
        }
        // Si ya hay una verificación viva para el MISMO número, se continúa como REENVÍO:
        // un envío fresco repetido para el mismo número vuelve a chocar con el broker de
        // GMS ("Failed to get service from broker") y se cuelga; el reenvío (misma sesión
        // de GMS con ForceResendingToken) sí funciona de forma consistente.
        if (telefono == telefonoPendiente && verificationIdPendiente.isNotEmpty() && resendTokenPendiente != null) {
            reenviarCodigo(activity)
            return
        }
        telefonoPendiente = telefono
        viewModelScope.launch {
            _uiState.update { it.copy(enviandoSms = true, error = null) }
            sesionRepositorio.enviarCodigo(activity, telefono).collect { evento ->
                manejarEventoVerificacion(evento)
            }
        }
    }

    fun reenviarCodigo(activity: Activity?) {
        if (verificationIdPendiente.isEmpty() || _uiState.value.enviandoSms) return
        viewModelScope.launch {
            _uiState.update { it.copy(enviandoSms = true, error = null) }
            sesionRepositorio.reenviarCodigo(activity, verificationIdPendiente, resendTokenPendiente ?: 0L)
                .collect { evento ->
                    manejarEventoVerificacion(evento)
                }
        }
    }

    /** Vuelve a la fase del número (p. ej. el usuario se equivocó de dígito). */
    fun regresarANumero() {
        // Se CONSERVA la verificación en curso: si el usuario reenvía el mismo número al
        // volver, se reutiliza la sesión de GMS (reenvío) en vez de estrellarse contra el
        // broker con otro envío fresco.
        cuentaRegresiva?.cancel()
        _uiState.update {
            it.copy(
                fase = FaseLogin.INGRESANDO_NUMERO,
                codigoEscrito = "",
                enviandoSms = false,
                verificando = false,
                segundosParaReenviar = 0,
                error = null,
            )
        }
    }

    private fun limpiarSesionPendiente() {
        verificationIdPendiente = ""
        resendTokenPendiente = null
        telefonoPendiente = ""
    }

    private fun manejarEventoVerificacion(evento: EventoVerificacion) {
        when (evento) {
            is EventoVerificacion.CodigoEnviado -> {
                verificationIdPendiente = evento.verificationId
                resendTokenPendiente = evento.resendToken
                _uiState.update {
                    it.copy(
                        enviandoSms = false,
                        fase = FaseLogin.ESPERANDO_CODIGO,
                        segundosParaReenviar = SEGUNDOS_PARA_REENVIAR,
                    )
                }
                iniciarCuentaRegresiva()
            }

            is EventoVerificacion.VerificacionAutomatica -> validarAllowlist(evento.custodio)

            is EventoVerificacion.Fallo -> _uiState.update {
                it.copy(enviandoSms = false, error = evento.error.aRecurso())
            }
        }
    }

    private fun verificarCodigo(codigo: String) {
        if (_uiState.value.verificando) return
        viewModelScope.launch {
            _uiState.update { it.copy(verificando = true, error = null) }
            when (val resultado = sesionRepositorio.verificarCodigo(verificationIdPendiente, codigo)) {
                is Result.Success -> validarAllowlist(resultado.value)
                is Result.Failure -> _uiState.update {
                    it.copy(verificando = false, error = resultado.error.aRecurso())
                }
            }
        }
    }

    /**
     * Allowlist (opción B del plan): se consulta DESPUÉS de autenticar, para que la regla
     * de lectura de Firestore pueda exigir sesión. Si el número no está autorizado se
     * cierra la sesión recién abierta y se muestra el error.
     */
    private fun validarAllowlist(custodio: Custodio) {
        viewModelScope.launch {
            when (val resultado = allowlistRepositorio.contiene(custodio.telefono)) {
                is Result.Success -> {
                    if (resultado.value) {
                        limpiarSesionPendiente()
                        return@launch
                    }
                    sesionRepositorio.cerrarSesion()
                    _uiState.update {
                        it.copy(
                            verificando = false,
                            enviandoSms = false,
                            error = R.string.error_tel_no_autorizado,
                        )
                    }
                }

                is Result.Failure -> _uiState.update {
                    it.copy(verificando = false, error = resultado.error.aRecurso())
                }
            }
        }
    }

    private var cuentaRegresiva: Job? = null

    private fun iniciarCuentaRegresiva() {
        cuentaRegresiva?.cancel()
        cuentaRegresiva = viewModelScope.launch {
            var restante = _uiState.value.segundosParaReenviar
            while (restante >= 0) {
                _uiState.update { it.copy(segundosParaReenviar = restante) }
                delay(1_000)
                restante--
            }
        }
    }

    private companion object {
        const val SEGUNDOS_PARA_REENVIAR = 60
    }
}