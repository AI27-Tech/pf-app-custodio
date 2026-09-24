package com.ai27.app.custodio.data.repository

import com.ai27.app.custodio.data.local.CatalogoEtiquetasAlmacen
import com.ai27.app.custodio.domain.model.Etiqueta
import com.ai27.app.custodio.domain.repository.EtiquetaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CacheEtiquetasRepositorioTest {

    private class AlmacenEnMemoria(
        var contenido: List<Etiqueta>? = null,
    ) : CatalogoEtiquetasAlmacen {
        var escrituras = 0
        override fun leer(): List<Etiqueta>? = contenido
        override fun guardar(etiquetas: List<Etiqueta>) {
            contenido = etiquetas
            escrituras++
        }
    }

    private class RemotoContador(
        private val catalogo: List<Etiqueta>,
    ) : EtiquetaRepository {
        var llamadas = 0
        override suspend fun obtener(): List<Etiqueta> {
            llamadas++
            return catalogo
        }
    }

    @Test
    fun `en frio, busca el remoto, persiste y despues sirve de memoria`() = runTest {
        val remoto = RemotoContador(CATALOGO_ETIQUETAS)
        val almacen = AlmacenEnMemoria()
        val repo = construirRepo(remoto, almacen, testScheduler)

        val primera = repo.obtener()

        assertEquals(CATALOGO_ETIQUETAS, primera)
        assertEquals(1, remoto.llamadas)
        assertEquals(1, almacen.escrituras)
        assertEquals(CATALOGO_ETIQUETAS, almacen.contenido)

        val segunda = repo.obtener()

        assertEquals(CATALOGO_ETIQUETAS, segunda)
        assertEquals(1, remoto.llamadas)
        runCurrent()
        assertEquals(1, remoto.llamadas)
    }

    @Test
    fun `con copia guardada responde sin red y refresca una sola vez por proceso`() = runTest {
        val remoto = RemotoContador(CATALOGO_ETIQUETAS)
        val almacen = AlmacenEnMemoria(CATALOGO_ETIQUETAS)
        val repo = construirRepo(remoto, almacen, testScheduler)

        val primera = repo.obtener()

        // El guardado local se sirve sin esperar la red.
        assertEquals(CATALOGO_ETIQUETAS, primera)
        assertEquals(0, remoto.llamadas)

        // El refresco de fondo toca el remoto una vez…
        runCurrent()
        assertEquals(1, remoto.llamadas)
        assertEquals(1, almacen.escrituras)

        // …y las llamadas siguientes salen de memoria, sin repetir la red.
        assertEquals(CATALOGO_ETIQUETAS, repo.obtener())
        runCurrent()
        assertEquals(1, remoto.llamadas)
    }

    @Test
    fun `las lecturas concurrentes en frio se coalescen en una sola llamada remota`() = runTest {
        val remoto = RemotoContador(CATALOGO_ETIQUETAS)
        val repo = construirRepo(remoto, AlmacenEnMemoria(), testScheduler)

        val lote1 = async { repo.obtener() }
        val lote2 = async { repo.obtener() }

        assertEquals(CATALOGO_ETIQUETAS, lote1.await())
        assertEquals(CATALOGO_ETIQUETAS, lote2.await())
        assertEquals(1, remoto.llamadas)
    }

    private fun construirRepo(
        remoto: RemotoContador,
        almacen: AlmacenEnMemoria,
        scheduler: TestCoroutineScheduler,
    ): CacheEtiquetasRepositorio = CacheEtiquetasRepositorio(
        remoto = remoto,
        almacen = almacen,
        scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler)),
    )
}