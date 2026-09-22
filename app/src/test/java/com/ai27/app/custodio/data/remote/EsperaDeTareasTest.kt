package com.ai27.app.custodio.data.remote

import com.ai27.app.custodio.domain.Result
import com.ai27.app.custodio.domain.error.DominioError
import com.google.android.gms.tasks.Tasks
import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Red de regresión del hallazgo **B1** de `docs/AUDIT.md`.
 *
 * El helper propio `Task<T>.esperar()` solo resolvía la corrutina cuando
 * `resultado.isSuccessful && resultado.result != null`. Una `Task<Void>` **exitosa**
 * devuelve `getResult() == null`, así que `set()` y `update()` de Firestore —que
 * devuelven `Task<Void>`— caían siempre al `else` y lanzaban `IllegalStateException`.
 * El efecto era que el pipeline no subía nada: cada medio terminaba en `FALLIDA`.
 *
 * Estas pruebas corren en la JVM, sin dispositivo ni emulador, porque la API de
 * `Tasks` de Play Services es Java puro. Eso las hace aptas para CI, que es donde
 * hacía falta una red: `data/remote` solo tenía una prueba (el mapeo de códigos de
 * Auth) y por eso B1 pudo llegar hasta la auditoría sin detectarse.
 *
 * La prueba de integración contra el Emulator Suite vive en
 * `androidTest/.../RemotoEmuladorTest.kt` y cubre el viaje redondo real; esta cubre
 * la semántica exacta que se rompió.
 */
class EsperaDeTareasTest {

    @Test
    fun `una Task Void exitosa se trata como exito y no como error`() {
        // Es la forma exacta que devuelven DocumentReference.set() y .update().
        val resultado: Result<Unit> = runBlocking {
            ejecutarRemoto {
                Tasks.forResult<Void>(null).await()
                Unit
            }
        }

        assertTrue(
            "Una Task<Void> exitosa debe producir Success; con el helper esperar() " +
                "producía Failure(Inesperado) y eso bloqueaba todo el pipeline (B1).",
            resultado is Result.Success,
        )
    }

    @Test
    fun `una Task con resultado no nulo sigue devolviendo su valor`() {
        val resultado: Result<String> = runBlocking {
            ejecutarRemoto { Tasks.forResult("ciclo-activo").await() }
        }

        assertEquals(Result.Success("ciclo-activo"), resultado)
    }

    @Test
    fun `una Task fallida propaga la excepcion y se mapea a error de dominio`() {
        val resultado: Result<Unit> = runBlocking {
            ejecutarRemoto {
                Tasks.forException<Void>(IOException("sin red")).await()
                Unit
            }
        }

        assertEquals(Result.Failure(DominioError.SinConexion), resultado)
    }

    @Test
    fun `un fallo no clasificado se mapea a Inesperado`() {
        val resultado: Result<Unit> = runBlocking {
            ejecutarRemoto {
                Tasks.forException<Void>(IllegalStateException("reglas")).await()
                Unit
            }
        }

        assertTrue(resultado is Result.Failure)
        assertTrue((resultado as Result.Failure).error is DominioError.Inesperado)
    }

    // La otra rama de `excepcionRemotoAError`, la de FirebaseNetworkException, no se puede
    // cubrir aquí: el constructor de esa excepción llama a android.text.TextUtils.isEmpty,
    // que no existe en una prueba JVM de host, así que instanciarla falla antes de llegar
    // al mapeo. Se cubre con IOException, que entra por la misma rama del `when`, y la
    // variante de Firebase queda para la prueba instrumentada, donde Android es real.
}
