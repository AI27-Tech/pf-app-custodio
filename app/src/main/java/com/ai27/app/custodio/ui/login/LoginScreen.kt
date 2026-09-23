package com.ai27.app.custodio.ui.login

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ai27.app.custodio.R
import com.ai27.app.custodio.ui.components.Vitrina
import com.ai27.app.custodio.ui.previews.DatosDemo

@Composable
fun LoginRoute(
    viewModel: LoginViewModel = hiltViewModel(),
) {
    // El context de la composición dentro de una Activity ES esa Activity; se pasa a
    // Phone Auth solo para la auto-lectura del SMS y el fallback de reCAPTCHA.
    val activity = LocalContext.current as? Activity
    val estado by viewModel.uiState.collectAsState()
    LoginScreen(
        estado = estado,
        onTelefonoCambio = viewModel::onTelefonoCambio,
        onCodigoCambio = viewModel::onCodigoCambio,
        onEnviarCodigo = { viewModel.enviarCodigo(activity) },
        onVerificar = viewModel::verificarCodigoTecleado,
        onReenviarCodigo = { viewModel.reenviarCodigo(activity) },
        onRegresar = viewModel::regresarANumero,
    )
}

@Composable
fun LoginScreen(
    estado: LoginUiState,
    onTelefonoCambio: (String) -> Unit,
    onCodigoCambio: (String) -> Unit,
    onEnviarCodigo: () -> Unit,
    onVerificar: () -> Unit,
    onReenviarCodigo: () -> Unit,
    onRegresar: () -> Unit,
) {
    // El atrás del sistema en la fase de OTP vuelve al número en vez de salir de la app.
    if (estado.fase == FaseLogin.ESPERANDO_CODIGO) {
        BackHandler(enabled = !estado.enviandoSms && !estado.verificando) { onRegresar() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.login_subtitulo),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))

        when (estado.fase) {
            FaseLogin.INGRESANDO_NUMERO -> FaseNumero(
                estado = estado,
                onTelefonoCambio = onTelefonoCambio,
                onEnviarCodigo = onEnviarCodigo,
            )

            FaseLogin.ESPERANDO_CODIGO -> FaseCodigo(
                estado = estado,
                onCodigoCambio = onCodigoCambio,
                onVerificar = onVerificar,
                onReenviarCodigo = onReenviarCodigo,
                onRegresar = onRegresar,
            )
        }

        estado.error?.let { error ->
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(error),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun FaseNumero(
    estado: LoginUiState,
    onTelefonoCambio: (String) -> Unit,
    onEnviarCodigo: () -> Unit,
) {
    OutlinedTextField(
        value = estado.telefonoEscrito,
        onValueChange = onTelefonoCambio,
        label = { Text(stringResource(R.string.login_telefono)) },
        prefix = { Text(stringResource(R.string.login_telefono_prefijo)) },
        placeholder = { Text(stringResource(R.string.login_telefono_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Phone,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onEnviarCodigo() }),
        enabled = !estado.enviandoSms,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(24.dp))
    Button(
        onClick = onEnviarCodigo,
        enabled = !estado.enviandoSms && estado.telefonoEscrito.length == 10,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        if (estado.enviandoSms) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
        } else {
            Text(stringResource(R.string.login_enviar_codigo))
        }
    }
}

@Composable
private fun FaseCodigo(
    estado: LoginUiState,
    onCodigoCambio: (String) -> Unit,
    onVerificar: () -> Unit,
    onReenviarCodigo: () -> Unit,
    onRegresar: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        TextButton(
            onClick = onRegresar,
            enabled = !estado.enviandoSms && !estado.verificando,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
            )
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.login_regresar))
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.login_codigo_enviado),
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = estado.codigoEscrito,
        onValueChange = onCodigoCambio,
        label = { Text(stringResource(R.string.login_codigo)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done,
        ),
        enabled = !estado.verificando,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(24.dp))
    Button(
        onClick = onVerificar,
        enabled = estado.codigoEscrito.length == 6,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        if (estado.verificando) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
        } else {
            Text(stringResource(R.string.login_verificar))
        }
    }
    Spacer(Modifier.height(8.dp))
    TextButton(
        onClick = onReenviarCodigo,
        enabled = !estado.enviandoSms && estado.segundosParaReenviar == 0,
    ) {
        Text(
            text = if (estado.segundosParaReenviar > 0) {
                stringResource(R.string.login_reenviar_en, estado.segundosParaReenviar)
            } else {
                stringResource(R.string.login_reenviar)
            },
        )
    }
}

@Preview(name = "Claro")
@Composable
private fun LoginVistaClaro() {
    Vitrina(oscuro = false) {
        LoginScreen(
            estado = LoginUiState(telefonoEscrito = DatosDemo.custodio.telefono),
            onTelefonoCambio = {},
            onCodigoCambio = {},
            onEnviarCodigo = {},
            onVerificar = {},
            onReenviarCodigo = {},
            onRegresar = {},
        )
    }
}

@Preview(name = "Oscuro — código")
@Composable
private fun LoginVistaOscuro() {
    Vitrina(oscuro = true) {
        LoginScreen(
            estado = LoginUiState(
                fase = FaseLogin.ESPERANDO_CODIGO,
                telefonoEscrito = DatosDemo.custodio.telefono,
                codigoEscrito = "123456",
            ),
            onTelefonoCambio = {},
            onCodigoCambio = {},
            onEnviarCodigo = {},
            onVerificar = {},
            onReenviarCodigo = {},
            onRegresar = {},
        )
    }
}