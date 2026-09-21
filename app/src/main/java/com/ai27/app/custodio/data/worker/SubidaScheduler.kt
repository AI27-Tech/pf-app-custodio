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
    suspend fun reconciliar()

    companion object {
        const val REPORTE_ID = "reporteId"
        const val MEDIO_ID = "medioId"
        const val NOMBRE_SUBIDA = "subida_medio_"
        const val NOMBRE_SYNC = "sync_reporte_"
    }
}
