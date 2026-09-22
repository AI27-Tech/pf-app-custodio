package com.ai27.app.custodio.ui.captura

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import com.ai27.app.custodio.data.local.media.Miniaturas
import java.util.concurrent.Executor

@Composable
fun VistaCamara(
    alTenerController: (LifecycleCameraController) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var controller by remember { mutableStateOf<LifecycleCameraController?>(null) }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            PreviewView(ctx).also { preview ->
                val nuevo = LifecycleCameraController(ctx).apply {
                    cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                    imageCaptureMode = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                    setImageCaptureTargetSize(
                        CameraController.OutputSize(android.util.Size(1920, 1080)),
                    )
                }
                preview.implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                preview.controller = nuevo
                nuevo.bindToLifecycle(lifecycleOwner)
                controller = nuevo
                alTenerController(nuevo)
            }
        },
    )

    DisposableEffect(Unit) {
        onDispose {
            controller?.unbind()
            controller = null
        }
    }
}

/**
 * Dispara una foto. [alTenerFoto] recibe el JPEG y su miniatura; [alFallar] recibe
 * cualquier fallo, incluidos los de CameraX y los del procesamiento de imagen.
 *
 * Los dos callbacks son excluyentes y **siempre se invoca exactamente uno**: de eso
 * depende que el cerrojo del obturador se libere (hallazgos E1 y E2 de `docs/AUDIT.md`,
 * donde el `catch` y el `onError` estaban vacíos y un fallo no producía ninguna señal).
 */
fun tomarFoto(
    controller: LifecycleCameraController,
    ejecutor: Executor,
    alTenerFoto: (jpeg: ByteArray, miniatura: ByteArray) -> Unit,
    alFallar: (causa: Throwable) -> Unit,
) {
    try {
        controller.takePicture(
            ejecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val bitmap = image.aBitmap()
                        val (jpeg, miniatura) = Miniaturas.fotoOriginalYJpeg(bitmap)
                        alTenerFoto(jpeg, miniatura)
                    } catch (causa: Throwable) {
                        // Throwable y no Exception: aquí es donde revienta un
                        // OutOfMemoryError al decodificar y recomprimir el bitmap.
                        alFallar(causa)
                    } finally {
                        image.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    alFallar(exception)
                }
            },
        )
    } catch (causa: Throwable) {
        // takePicture puede lanzar de forma sincrónica si la cámara no está lista.
        alFallar(causa)
    }
}

private fun ImageProxy.aBitmap(): Bitmap {
    val bitmap = toBitmap()
    val grados = imageInfo.rotationDegrees
    if (grados == 0) return bitmap
    val matriz = Matrix().apply { postRotate(grados.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matriz, true)
}