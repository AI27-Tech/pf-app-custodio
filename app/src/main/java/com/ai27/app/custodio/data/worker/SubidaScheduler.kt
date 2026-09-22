package com.ai27.app.custodio.data.worker

// Encola el trabajo de subida. Cada medio y cada reporte tienen un nombre único de
// cola: un reintento reemplaza la tarea anterior en vez de duplicarla (PLAN §5.4).
//
// Es una interfaz para que las ViewModels puedan probarse con un doble en pruebas de
// JVM (la implementación real habla con WorkManager y necesita un Context de Android).
interface SubidaScheduler {
    fun encolarSubidaMedio(reporteId: String, medioId: String)
    fun sincronizarReporte(reporteId: String)

    // Repone la cola al arrancar la app: re-encola reportes y medios que quedaron
    // pendientes de la sesión anterior (el worker espera a que haya red).
    //
    // Recibe el uid porque la cola es **por custodio**: en un equipo que rotan 37
    // personas, reencolar lo del anterior con el token del nuevo lo dejaba en FALLIDA
    // permanente e invisible (hallazgo E6 de docs/AUDIT.md).
    suspend fun reconciliar(custodioUid: String)

    /**
     * Cancela todo el trabajo de subida en vuelo y en cola. Se llama **antes** del
     * `signOut` (hallazgo E6): un worker arrancado con el token del custodio saliente que
     * sigue corriendo mientras entra el siguiente ve sus escrituras denegadas por las
     * reglas y deja la fila en `FALLIDA`.
     *
     * No hay pérdida de evidencia: un medio que quede en `EN_PROGRESO` por la cancelación
     * lo rescata [reconciliar] cuando ese custodio vuelva a entrar.
     */
    fun cancelarTodo()

    companion object {
        const val REPORTE_ID = "reporteId"
        const val MEDIO_ID = "medioId"
        const val NOMBRE_SUBIDA = "subida_medio_"
        const val NOMBRE_SYNC = "sync_reporte_"

        /** Etiqueta común de todo el trabajo de subida, para poder cancelarlo en bloque. */
        const val TAG_SUBIDA = "subida_custodio"
    }
}
