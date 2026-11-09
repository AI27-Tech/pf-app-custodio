package com.ai27.app.custodio.ui.captura

import android.graphics.ImageFormat
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
import com.ai27.app.custodio.data.foto.CodecFoto
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
                    // La política de tamaño vive en CodecFoto: el target es solo una pista
                    // para CameraX (el HAL puede entregar otro) y el revelado fija el final.
                    setImageCaptureTargetSize(
                        CameraController.OutputSize(
                            android.util.Size(CodecFoto.LADO_MAX_PX, CodecFoto.LADO_MAX_PX * 3 / 4),
                        ),
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
 * Dispara una foto. [alTenerFoto] recibe la **foto cruda** (los bytes JPEG del HAL, sin
 * decodificar, rotar ni marcar) y los grados que hay que rotarla; [alFallar] recibe
 * cualquier fallo, incluidos los de CameraX y un formato que no sea JPEG.
 *
 * No se decodifica aquí: en el SC9863A de los ZTE el decode completo era lo que hacía
 * lento el obturador. La miniatura y el revelado los hace `RevelarFoto`
 * (`docs/PLAN_REVELAR_FOTO.md`, D4). CameraX entrega el JPEG sin rotación aplicada, por
 * eso los grados viajan aparte.
 *
 * Los dos callbacks son excluyentes y **siempre se invoca exactamente uno**: de eso
 * depende que el cerrojo del obturador se libere (hallazgos E1 y E2 de `docs/AUDIT.md`,
 * donde el `catch` y el `onError` estaban vacíos y un fallo no producía ninguna señal).
 */
fun tomarFoto(
    controller: LifecycleCameraController,
    ejecutor: Executor,
    alTenerFoto: (crudo: ByteArray, rotacionGrados: Int) -> Unit,
    alFallar: (causa: Throwable) -> Unit,
) {
    try {
        controller.takePicture(
            ejecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val crudo: ByteArray
                    val rotacionGrados: Int
                    try {
                        check(image.format == ImageFormat.JPEG) {
                            "La cámara entregó el formato ${image.format}; se esperaba JPEG"
                        }
                        val buffer = image.planes[0].buffer
                        crudo = ByteArray(buffer.remaining()).also { buffer.get(it) }
                        rotacionGrados = image.imageInfo.rotationDegrees
                    } catch (causa: Throwable) {
                        // Throwable y no Exception: copiar el búfer puede lanzar un
                        // OutOfMemoryError en un equipo con poca memoria.
                        alFallar(causa)
                        return
                    } finally {
                        image.close()
                    }
                    // Fuera del try: un fallo de quien recibe la foto no debe disparar
                    // también alFallar. La copia ya no depende del ImageProxy cerrado.
                    alTenerFoto(crudo, rotacionGrados)
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
