package com.ai27.app.custodio.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ai27.app.custodio.R
import com.ai27.app.custodio.ui.components.MarcaEcho
import com.ai27.app.custodio.ui.components.Vitrina
import com.ai27.app.custodio.ui.previews.DatosDemo

@Composable
fun LoginRoute(
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val estado by viewModel.uiState.collectAsState()
    LoginScreen(
        estado = estado,
        onCorreoCambio = viewModel::onCorreoCambio,
        onContrasenaCambio = viewModel::onContrasenaCambio,
        onIniciarSesion = viewModel::iniciarSesion,
    )
}

/**
 * Login de una sola fase (plan de correo, Task 7): correo, contraseña y un botón. No hay
 * `Activity` en el camino (el login por correo no la necesita) ni `BackHandler` de fase OTP.
 */
@Composable
fun LoginScreen(
    estado: LoginUiState,
    onCorreoCambio: (String) -> Unit,
    onContrasenaCambio: (String) -> Unit,
    onIniciarSesion: () -> Unit,
) {
    val listoParaEntrar = estado.correoEscrito.isNotBlank() &&
        estado.contrasenaEscrita.isNotBlank() &&
        !estado.autenticando
    // El "Done" del teclado consulta la MISMA condición que el botón: con la contraseña
    // vacía no se envía, porque `signInWithEmailAndPassword` la rechazaría en local con
    // `ERROR_MISSING_PASSWORD` y el custodio vería un "Algo salió mal" por no haber
    // tecleado nada.

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MarcaEcho()
        Spacer(Modifier.height(24.dp))
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

        OutlinedTextField(
            value = estado.correoEscrito,
            onValueChange = onCorreoCambio,
            label = { Text(stringResource(R.string.login_correo)) },
            placeholder = { Text(stringResource(R.string.login_correo_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            enabled = !estado.autenticando,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = estado.contrasenaEscrita,
            onValueChange = onContrasenaCambio,
            label = { Text(stringResource(R.string.login_contrasena)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { if (listoParaEntrar) onIniciarSesion() }),
            enabled = !estado.autenticando,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onIniciarSesion,
            enabled = listoParaEntrar,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            if (estado.autenticando) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Text(stringResource(R.string.login_iniciar_sesion))
            }
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

@Preview(name = "Claro")
@Composable
private fun LoginVistaClaro() {
    Vitrina(oscuro = false) {
        LoginScreen(
            estado = LoginUiState(
                correoEscrito = DatosDemo.custodio.correo,
                contrasenaEscrita = "••••••••",
            ),
            onCorreoCambio = {},
            onContrasenaCambio = {},
            onIniciarSesion = {},
        )
    }
}

@Preview(name = "Oscuro — autenticando")
@Composable
private fun LoginVistaOscuro() {
    Vitrina(oscuro = true) {
        LoginScreen(
            estado = LoginUiState(
                correoEscrito = DatosDemo.custodio.correo,
                contrasenaEscrita = "••••••••",
                autenticando = true,
            ),
            onCorreoCambio = {},
            onContrasenaCambio = {},
            onIniciarSesion = {},
        )
    }
}
