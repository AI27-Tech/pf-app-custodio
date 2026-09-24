package com.ai27.app.custodio.data.local

import com.ai27.app.custodio.domain.model.CategoriaEtiqueta
import com.ai27.app.custodio.domain.model.Etiqueta

/**
 * Codificador del catálogo de etiquetas para su copia local (A4). Sin dependencia de
 * serialización: una fila por etiqueta y cinco campos por fila, todos escapados.
 *
 * Formato: `id|nombre|CATEGORIA|orden|activo\n…`. Los carácteres especiales se escapan
 * con barra invertida (`\\` → barra, `\n` → salto de línea, `\|` → tubería); el decodificador
 * interpreta `\n` como salto real, así un valor nunca puede colarse de fila.
 */
object CatalogoEtiquetasCodificador {

    private const val SEPARADOR_FILA = '\n'
    private const val SEPARADOR_CAMPO = '|'
    private const val CAMPOS_POR_ETIQUETA = 5

    fun codificar(etiquetas: List<Etiqueta>): String =
        etiquetas.joinToString(SEPARADOR_FILA.toString()) { etiqueta ->
            listOf(
                etiqueta.id,
                etiqueta.nombre,
                etiqueta.categoria.name,
                etiqueta.orden.toString(),
                etiqueta.activo.toString(),
            ).joinToString(SEPARADOR_CAMPO.toString()) { campo -> escapar(campo) }
        }

    /** `null` si el texto está vacío o no es un catálogo descifrable. */
    fun descodificar(texto: String): List<Etiqueta>? {
        if (texto.isBlank()) return null
        return try {
            dividir(texto, SEPARADOR_FILA).map { fila ->
                val campos = dividir(fila, SEPARADOR_CAMPO).map { desescapar(it) }
                if (campos.size != CAMPOS_POR_ETIQUETA) {
                    throw IllegalArgumentException("se esperaban $CAMPOS_POR_ETIQUETA campos")
                }
                Etiqueta(
                    id = campos[0],
                    nombre = campos[1],
                    categoria = CategoriaEtiqueta.valueOf(campos[2]),
                    orden = campos[3].toInt(),
                    activo = campos[4].toBoolean(),
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun escapar(valor: String): String =
        valor.replace("\\", "\\\\")
            .replace("\n", "\\n")
            .replace("|", "\\|")

    // Parte SOLO por el separador sin escapar: devuelve segmentos con los escapes intactos;
    // el desescape se hace después, por segmento, para que `\|` no se confunda con un
    // separador de campo (ver desescapar).
    private fun dividir(texto: String, separador: Char): List<String> {
        val partes = mutableListOf<String>()
        val parte = StringBuilder()
        var escapando = false
        texto.forEach { c ->
            when {
                escapando -> {
                    parte.append('\\').append(c)
                    escapando = false
                }
                c == '\\' -> escapando = true
                c == separador -> {
                    partes.add(parte.toString())
                    parte.clear()
                }
                else -> parte.append(c)
            }
        }
        if (escapando) parte.append('\\')
        partes.add(parte.toString())
        return partes
    }

    private fun desescapar(parte: String): String {
        val result = StringBuilder()
        var escapando = false
        parte.forEach { c ->
            when {
                escapando -> {
                    result.append(if (c == 'n') '\n' else c)
                    escapando = false
                }
                c == '\\' -> escapando = true
                else -> result.append(c)
            }
        }
        if (escapando) result.append('\\')
        return result.toString()
    }
}