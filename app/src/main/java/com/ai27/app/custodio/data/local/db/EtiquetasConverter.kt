package com.ai27.app.custodio.data.local.db

import androidx.room3.ColumnTypeConverter

/**
 * Serializa la lista de etiquetas en la columna TEXT `etiquetas` (PLAN §14, decisión C).
 *
 * El formato es un arreglo JSON de strings — el mismo que describe la migración 1→2
 * (decisión D2) — aunque las ids del catálogo no contienen caracteres que exijan escape,
 * el codificador las escapa igual para que un valor arbitrario nunca corrompa la columna.
 */
object EtiquetasConverter {

    @ColumnTypeConverter
    fun aLista(texto: String?): List<String> =
        if (texto.isNullOrBlank()) emptyList() else decodificar(texto)

    @ColumnTypeConverter
    fun aTexto(etiquetas: List<String>): String = etiquetas.joinToString(
        prefix = "[",
        separator = ",",
        postfix = "]",
    ) { valor -> "\"${escapar(valor)}\"" }

    private fun escapar(valor: String): String =
        valor.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun decodificar(texto: String): List<String> {
        val cuerpo = texto.trim().removePrefix("[").removeSuffix("]")
        if (cuerpo.isBlank()) return emptyList()
        val resultado = mutableListOf<String>()
        val parte = StringBuilder()
        var enCadena = false
        var escapando = false
        cuerpo.forEach { c ->
            when {
                escapando -> {
                    parte.append(c)
                    escapando = false
                }
                c == '\\' -> escapando = true
                c == '"' -> enCadena = !enCadena
                c == ',' && !enCadena -> {
                    resultado.add(parte.toString())
                    parte.clear()
                }
                enCadena -> parte.append(c)
            }
        }
        resultado.add(parte.toString())
        return resultado
    }
}