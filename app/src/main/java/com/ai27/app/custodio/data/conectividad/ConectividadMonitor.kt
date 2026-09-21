package com.ai27.app.custodio.data.conectividad

import kotlinx.coroutines.flow.Flow

/**
 * Fuente de verdad de la conectividad de red que ve la UI.
 *
 * Es un `Flow<Boolean>`: `true` = en línea, `false` = sin red (la franja offline del mockup
 * `reporte_abierto_sin_conexi_n_reparado`). La impl concreta observa `ConnectivityManager`;
 * la app siempre parte en línea con `SHARING_STARTED` (WhileSubscribed) y no consulta el
 * estado una sola vez: reacciona a los saltos en caliente.
 */
interface ConectividadMonitor {
    /** `true` cuando hay red disponible, `false` en cuanto se cae. */
    fun observadorConectividad(): Flow<Boolean>
}
