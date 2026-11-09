package com.ai27.app.custodio.data.remote

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ai27.app.custodio.di.ModoEmulador
import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.model.Ciclo
import com.ai27.app.custodio.domain.model.EstadoSubida
import com.ai27.app.custodio.domain.model.Medio
import com.ai27.app.custodio.domain.model.OrigenMedio
import com.ai27.app.custodio.domain.model.Reporte
import com.ai27.app.custodio.domain.model.ResumenMedios
import com.ai27.app.custodio.domain.model.TipoMedio
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prueba de integración de `data/remote` contra el **Emulator Suite** de Firebase.
 *
 * Es la red de regresión del hallazgo **B1** de `docs/AUDIT.md`: el helper propio
 * `Task<T>.esperar()` lanzaba excepción ante una `Task<Void>` exitosa, y como `set()`
 * y `update()` de Firestore devuelven `Task<Void>`, ninguna escritura funcionaba. Toda
 * la suite unitaria estaba en verde porque nadie ejercitaba la capa remota: `data/remote`
 * solo tenía la prueba del mapeo de códigos de Auth.
 *
 * ## Requisitos para correrla
 *
 * 1. Levantar los emuladores en el Mac CON EL PROYECTO DEL APP (lo elige el SDK desde
 *    `google-services.json`). Sin `--project`, el CLI no autenticado cae a
 *    `demo-custodio` y Firestore/Auth se ven bien pero la regla de Storage falla: su
 *    `firestore.get()` cruza a `projects/demo-custodio/...` y no encuentra el reporte:
 *    ```
 *    cd firebase && npx firebase emulators:start --only firestore,storage,auth --project custodio-906e7268
 *    ```
 * 2. Abrir los túneles al dispositivo, porque `ModoEmulador.HOST` es `127.0.0.1` y en un
 *    teléfono real eso apunta al propio teléfono (en un emulador de Android sería
 *    `10.0.2.2`, pero `adb reverse` funciona en los dos casos):
 *    ```
 *    adb reverse tcp:8080 tcp:8080
 *    adb reverse tcp:9199 tcp:9199
 *    adb reverse tcp:9099 tcp:9099
 *    ```
 * 3. `./gradlew connectedDebugAndroidTest`
 *
 * Si los túneles no están abiertos, las llamadas fallan por red y las pruebas fallan con
 * `SinConexion`. Está documentado en el `README.md`.
 *
 * ## Restricciones del emulador respetadas aquí (ver `AGENTS.md`)
 *
 * - No se usa `list`/`runQuery`: solo `get` por llave, porque el emulador evalúa mal las
 *   reglas de lectura de colección.
 * - El documento del reporte se crea **antes** que el del medio, porque la regla de
 *   Storage hace un `get()` cruzado a `reportes/{id}` y en el emulador un `get()` a un
 *   documento inexistente revienta la regla completa en lugar de devolver `null`.
 * - Cada corrida usa UUID nuevos, así que no hay estado compartido entre pruebas.
 */
@RunWith(AndroidJUnit4::class)
class RemotoEmuladorTest {

    private lateinit var reporteRemoto: FirebaseReporteRemoto
    private lateinit var medioRemoto: FirebaseMedioRemoto
    private lateinit var custodioUid: String

    companion object {
        private const val HOST = "127.0.0.1"
        private const val PUERTO_FIRESTORE = 8080
        private const val PUERTO_STORAGE = 9199
        private const val PUERTO_AUTH = 9099

        // useEmulator() debe llamarse una sola vez por instancia de Firebase y antes de
        // cualquier uso; las instancias son singletons de proceso.
        @JvmStatic
        @BeforeClass
        fun apuntarAlEmulador() {
            // Desde G3-E, DI (`AuthModule`/`PipelineModule`) apunta las instancias al
            // emulador cuando `ModoEmulador.activo` es true (debug + meta-data). PERO
            // `AuthModule.proveeFirebaseAuth` es LAZY: solo corre si alguien pide la
            // instancia de sesión (login), cosa que el proceso instrumentado no hace
            // siempre → Auth podía quedar en Producción ("This operation is restricted
            // to administrators only", los 10 fallos de esta tanda). Así que aseguramos
            // el cableado AQUÍ de forma idempotente: si DI ya lo hizo, `useEmulator()`
            // lanza `IllegalStateException` y la ignoramos (ya está cableado); si no,
            // lo cableamos nosotros. Cubre ambos órdenes.
            val contexto = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
            if (com.ai27.app.custodio.di.ModoEmulador.activo(contexto)) {
                cablearSiNoInicializado { FirebaseAuth.getInstance().useEmulator(HOST, PUERTO_AUTH) }
                cablearSiNoInicializado { FirebaseFirestore.getInstance().useEmulator(HOST, PUERTO_FIRESTORE) }
                cablearSiNoInicializado { FirebaseStorage.getInstance().useEmulator(HOST, PUERTO_STORAGE) }
            }
        }

        // Idempotente: `useEmulator()` solo puede llamarse una vez por instancia de
        // Firebase y la segunda llamada lanza `IllegalStateException`. Como el orden
        // DI-vs-test varía por proceso, si DI ya cableó la instancia ignoramos la
        // excepción (ya está apuntando al emulador); si nadie la cableó, la cableamos
        // nosotros. Un `runCatching` genérico no basta: escondería fallos reales
        // (p. ej. problemas de conexión que sí deberían reventar la suite).
        private fun cablearSiNoInicializado(bloque: () -> Unit) {
            try {
                bloque()
            } catch (ignorable: IllegalStateException) {
                // "FirebaseApp already initialized" / "already set" → DI ya lo cableó.
            }
        }
    }

    @Before
    fun preparar() = runBlocking {
        val auth = FirebaseAuth.getInstance()
        val firestore = FirebaseFirestore.getInstance()
        val storage = FirebaseStorage.getInstance()

        // Las reglas exigen request.auth.uid == custodioUid, así que hay que estar
        // autenticado de verdad. En el emulador de Auth se puede crear la cuenta al vuelo.
        val correo = "custodio-${UUID.randomUUID()}@ai27.test"
        val credenciales = auth.createUserWithEmailAndPassword(correo, "secreta-123").await()
        custodioUid = requireNotNull(credenciales.user).uid

        reporteRemoto = FirebaseReporteRemoto(firestore)
        medioRemoto = FirebaseMedioRemoto(storage, firestore)
    }

    @Test
    fun crear_escribe_el_documento_del_reporte_y_obtenerCiclo_lo_lee() = runBlocking {
        val reporte = reporteDePrueba()

        // Con el helper viejo esto devolvía Failure(Inesperado) SIEMPRE: es el corazón de B1.
        val creado = reporteRemoto.crear(reporte)
        assertTrue("crear() debe devolver Success; era el fallo de B1. Obtuve: $creado", creado is Result.Success)

        val ciclo = reporteRemoto.obtenerCiclo(reporte.id)
        assertEquals(Result.Success(Ciclo.ACTIVO), ciclo)
    }

    @Test
    fun actualizarContadores_avanza_la_subida_del_reporte() = runBlocking {
        val reporte = reporteDePrueba()
        assertTrue(reporteRemoto.crear(reporte) is Result.Success)

        // S6 (PLAN §16): sin `mediosEsperados` (solo se fija al cerrar) `completo` no
        // puede ser true; la app nunca lo escribe antes del cierre. La regresión B1 de
        // este test es que `actualizarContadores()` usa update() → Task<Void> y debe
        // tener éxito con los valores que el contrato admite durante la captura.
        val actualizado = reporteRemoto.actualizarContadores(
            reporteId = reporte.id,
            estado = EstadoSubida.EN_PROGRESO,
            mediosSubidos = 1,
            completo = false,
        )

        assertTrue("actualizarContadores() usa update() → Task<Void>: era parte de B1.", actualizado is Result.Success)

        val doc = FirebaseFirestore.getInstance().collection("reportes").document(reporte.id).get().await()
        assertEquals("subida debe avanzar a EN_PROGRESO", "EN_PROGRESO", doc.getString("subida"))
        assertEquals("mediosSubidos avanza", 1L, doc.getLong("mediosSubidos"))
        assertEquals("completo sigue false sin haber cerrado", false, doc.getBoolean("completo"))
    }

    @Test
    fun actualizarContadores_es_monotono_ante_escrituras_intercaladas() = runBlocking {
        // C3 (docs/AUDIT.md): dos writers intercalados sin transacción hacían regresar
        // `mediosSubidos` y dejaban `completo = false` para siempre. La escritura es un
        // merge monótono en una transacción (subida no retrocede de rango, mediosSubidos
        // es el máximo, completo es un OR): una llamada "vieja" (calculada con una
        // fotografía menor) nunca pisa a una más reciente.
        val reporte = reporteDePrueba()
        assertTrue(reporteRemoto.crear(reporte) is Result.Success)

        assertTrue(
            reporteRemoto.actualizarContadores(reporte.id, EstadoSubida.EN_PROGRESO, mediosSubidos = 1, completo = false)
                is Result.Success,
        )
        // La "escritura vieja" del intercalado: llega DESPUÉS con valores menores. El
        // merge del cliente la absorbe (sigue siendo Success) y S6 (PLAN §16) es la
        // segunda línea: si algún día una escritura directa intentara regresar los
        // contadores, las reglas la rechazarían.
        assertTrue(
            reporteRemoto.actualizarContadores(reporte.id, EstadoSubida.PENDIENTE, mediosSubidos = 0, completo = false)
                is Result.Success,
        )

        val doc = FirebaseFirestore.getInstance().collection("reportes").document(reporte.id).get().await()
        assertTrue("El reporte debe existir tras crear()", doc.exists())
        assertEquals("subida no retrocede del rango alcanzado", "EN_PROGRESO", doc.getString("subida"))
        assertEquals("mediosSubidos nunca retrocede", 1L, doc.getLong("mediosSubidos"))
        assertEquals("completo no retrocede a false", false, doc.getBoolean("completo"))
    }

    @Test
    fun cerrar_transiciona_el_reporte_a_CERRADO() = runBlocking {
        val reporte = reporteDePrueba()
        assertTrue(reporteRemoto.crear(reporte) is Result.Success)

        val cerrado = reporteRemoto.cerrar(
            reporte.copy(
                ciclo = Ciclo.CERRADO,
                closedAt = Instant.now(),
                mediosEsperados = 1,
                comentario = "viaje de prueba",
                hayNovedad = true,
            ),
        )
        assertTrue("cerrar() usa update() → Task<Void>: era parte de B1.", cerrado is Result.Success)

        assertEquals(Result.Success(Ciclo.CERRADO), reporteRemoto.obtenerCiclo(reporte.id))
    }

    @Test
    fun descartar_transiciona_el_reporte_a_DESCARTADO() = runBlocking {
        val reporte = reporteDePrueba()
        assertTrue(reporteRemoto.crear(reporte) is Result.Success)

        val descartado = reporteRemoto.descartar(
            reporte.copy(
                ciclo = Ciclo.DESCARTADO,
                closedAt = Instant.now(),
                motivoDescarte = "placa equivocada",
            ),
        )
        assertTrue(descartado is Result.Success)

        assertEquals(Result.Success(Ciclo.DESCARTADO), reporteRemoto.obtenerCiclo(reporte.id))
    }

    @Test
    fun obtenerCiclo_devuelve_null_cuando_el_reporte_no_existe() = runBlocking {
        val ciclo = reporteRemoto.obtenerCiclo("reporte-que-no-existe-${UUID.randomUUID()}")

        assertEquals(Result.Success(null), ciclo)
    }

    @Test
    fun subirArchivo_y_crearDocumento_completan_el_viaje_redondo_del_medio() = runBlocking {
        val reporte = reporteDePrueba()
        // El reporte va primero: la regla de Storage lo consulta con un get() cruzado.
        assertTrue(reporteRemoto.crear(reporte) is Result.Success)

        val medio = medioDePrueba(reporte.id)
        val contenido = ByteArray(128) { it.toByte() }
        val miniatura = ByteArray(32) { it.toByte() }

        val subido = medioRemoto.subirArchivo(reporte, medio, contenido, miniatura)
        assertTrue("subirArchivo() debe devolver las rutas. Obtuve: $subido", subido is Result.Success)
        val rutas = (subido as Result.Success).value
        assertEquals("medios/${reporte.id}/${medio.id}.jpg", rutas.media)
        assertEquals("medios/${reporte.id}/${medio.id}-min.jpg", rutas.miniatura)

        val documento = medioRemoto.crearDocumentoSiNoExiste(reporte, medio, rutas)
        assertTrue("crearDocumentoSiNoExiste() usa set() → Task<Void>: era parte de B1.", documento is Result.Success)

        // El documento existe de verdad, leído por llave (no por query).
        val leido = FirebaseFirestore.getInstance().collection("medios").document(medio.id).get().await()
        assertTrue("El documento de medios debe existir tras crearDocumentoSiNoExiste()", leido.exists())
        assertEquals(reporte.id, leido.getString("reporteId"))
        assertEquals(custodioUid, leido.getString("custodioUid"))
        assertEquals(rutas.media, leido.getString("storagePath"))
    }

    @Test
    fun audio_graba_subida_y_documento_sin_miniatura() = runBlocking {
        // Red de regresión S5 (G2-9): el AUDIO no lleva miniatura (thumbnailPath = null),
        // así que las reglas tenían que tolerar null ahí o el create del documento se
        // negaba con PERMISSION_DENIED aunque el blob de Storage ya había subido.
        val reporte = reporteDePrueba()
        assertTrue(reporteRemoto.crear(reporte) is Result.Success)

        val medio = AudioDePrueba(reporte.id)
        val contenido = ByteArray(128) { it.toByte() }

        val subido = medioRemoto.subirArchivo(reporte, medio, contenido, null)
        assertTrue("subirArchivo() para audio debe devolver rutas. Obtuve: $subido", subido is Result.Success)
        val rutas = (subido as Result.Success).value
        assertEquals("medios/${reporte.id}/${medio.id}.m4a", rutas.media)
        assertTrue("El audio no genera miniatura", rutas.miniatura == null)

        val documento = medioRemoto.crearDocumentoSiNoExiste(reporte, medio, rutas)
        assertTrue("crearDocumentoSiNoExiste() para audio debe pasar las reglas. Obtuve: $documento", documento is Result.Success)

        val leido = FirebaseFirestore.getInstance().collection("medios").document(medio.id).get().await()
        assertTrue(leido.exists())
        assertEquals(medio.reporteId, leido.getString("reporteId"))
        assertEquals(rutas.media, leido.getString("storagePath"))
        assertEquals("audio/mp4", leido.getString("contentType"))
    }

    @Test
    fun crearDocumento_es_idempotente_en_el_reintento() = runBlocking {
        val reporte = reporteDePrueba()
        assertTrue(reporteRemoto.crear(reporte) is Result.Success)

        val medio = medioDePrueba(reporte.id)
        val rutas = RutasStorage.de(medio, null)

        assertTrue(medioRemoto.subirArchivo(reporte, medio, ByteArray(64), null) is Result.Success)
        assertTrue(medioRemoto.crearDocumentoSiNoExiste(reporte, medio, rutas) is Result.Success)

        // Segunda llamada: las reglas niegan update en `medios`, así que si no detectara
        // que ya existe, esto fallaría con PERMISSION_DENIED.
        val reintento = medioRemoto.crearDocumentoSiNoExiste(reporte, medio, rutas)
        assertTrue("El reintento debe ser idempotente y no reescribir. Obtuve: $reintento", reintento is Result.Success)
    }

    @Test
    fun el_sha256_viaja_en_el_documento_del_medio() = runBlocking {
        // Red de regresión del hallazgo C2 (docs/AUDIT.md): el documento de `medios` se
        // escribía con sha256 = null porque se construía con el medio leído de Room ANTES
        // de subir, y como las reglas prohíben `update` en `medios`, el hash quedaba nulo
        // e irrellenable. Ahora `SubidaMedioProceso` pasa una copia con `sha256` calculado.
        val reporte = reporteDePrueba()
        assertTrue(reporteRemoto.crear(reporte) is Result.Success)

        val medio = medioDePrueba(reporte.id).copy(sha256 = "hash-de-prueba-c2")

        val rutas = RutasStorage.de(medio, null)
        assertTrue(medioRemoto.subirArchivo(reporte, medio, ByteArray(64), null) is Result.Success)
        assertTrue(medioRemoto.crearDocumentoSiNoExiste(reporte, medio, rutas) is Result.Success)

        val leido = FirebaseFirestore.getInstance().collection("medios").document(medio.id).get().await()
        assertTrue("El documento de medios debe existir", leido.exists())
        assertEquals(
            "C2: el sha debe quedar persistido en el documento remoto",
            "hash-de-prueba-c2",
            leido.getString("sha256"),
        )
    }

    private fun reporteDePrueba(): Reporte = Reporte(
        id = "reporte-${UUID.randomUUID()}",
        placas = "ABC123",
        placasCapturadas = "abc-123",
        custodioUid = custodioUid,
        custodioCorreo = "custodio@ai27.test",
        clienteId = "cliente-provisional",
        deviceId = "dispositivo-de-prueba",
        fechaLocal = "2026-09-21",
        createdAtDevice = Instant.now(),
        ciclo = Ciclo.ACTIVO,
        subida = EstadoSubida.PENDIENTE,
        completo = false,
        mediosEsperados = null,
        mediosSubidos = 0,
        resumenMedios = ResumenMedios(),
    )

    private fun medioDePrueba(reporteId: String): Medio = Medio(
        id = "medio-${UUID.randomUUID()}",
        reporteId = reporteId,
        tipo = TipoMedio.FOTO,
        origen = OrigenMedio.CAMARA,
        createdAtDevice = Instant.now(),
        contentType = "image/jpeg",
        estadoSubida = EstadoSubida.PENDIENTE,
        etiquetas = listOf("sin-novedad"),
        sizeBytes = 128L,
    )

    private fun AudioDePrueba(reporteId: String): Medio = Medio(
        id = "audio-${UUID.randomUUID()}",
        reporteId = reporteId,
        tipo = TipoMedio.AUDIO,
        origen = OrigenMedio.GRABADORA,
        createdAtDevice = Instant.now(),
        contentType = "audio/mp4",
        estadoSubida = EstadoSubida.PENDIENTE,
        etiquetas = listOf("sin-novedad"),
        sizeBytes = 128L,
        durationMs = 5210L,
    )
}
