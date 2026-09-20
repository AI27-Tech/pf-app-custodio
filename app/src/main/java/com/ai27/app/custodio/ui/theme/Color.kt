package com.ai27.app.custodio.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Tokens de color del sistema de diseño de AI27 ("Nocturne Operations Interface").
 *
 * Fuente única: `repos/pf-library-frontend-components/src/styles/tokens.css`, el mismo
 * sistema que consumen el panel interno y el portal del cliente. Mantenerlos alineados
 * es lo que hace que los tres productos se vean como el mismo producto.
 *
 * El sistema es dark-first: el tema oscuro es el aspecto por defecto y el claro es la
 * variante derivada para uso diurno en exteriores.
 *
 * Aquí viven sólo los tokens que el tema consume; `tokens.css` tiene el juego completo y
 * es el lugar de donde se promueve uno nuevo cuando se necesita.
 *
 * Los nombres van en inglés porque son términos técnicos genéricos, no sustantivos de
 * dominio, y porque así conservan la correspondencia uno a uno con `tokens.css`.
 */

/** Tema oscuro — el aspecto por defecto de la app. */
internal object DarkTokens {
    val Primary = Color(0xFF7C9DF0)
    val OnPrimary = Color(0xFF0E1117)
    val Secondary = Color(0xFF2C3A58)
    val OnSecondary = Color(0xFFE2E8F0)

    /** Nivel 0 — lienzo global. Nunca negro absoluto. */
    val Canvas = Color(0xFF0E1117)

    /** Por debajo del lienzo: campos de texto hundidos. */
    val SurfaceLowest = Color(0xFF0B0E16)

    /** Nivel 1 — módulos hundidos. */
    val SurfaceLow = Color(0xFF161B24)

    /** Nivel 2 — paneles y tarjetas. */
    val Surface = Color(0xFF1C222E)

    /** Nivel 3 — superficies elevadas: hojas, overlays. */
    val SurfaceRaised = Color(0xFF242B3A)

    /** Nivel 4 — diálogos y cabeceras de control. */
    val SurfaceHighest = Color(0xFF2E3748)

    /** Trazo de nivel 2: divisores. */
    val Border = Color(0xFF2D3748)

    /** Trazo de nivel 3: contornos de controles. */
    val BorderStrong = Color(0xFF374151)

    val Text = Color(0xFFE2E8F0)
    val TextMuted = Color(0xFF94A3B8)

    val Success = Color(0xFF6EE7B7)
    val Warning = Color(0xFFFCD34D)
    val Danger = Color(0xFFFCA5A5)
    val OnDanger = Color(0xFF381A20)
}

/** Tema claro — variante derivada para uso diurno a pleno sol. */
internal object LightTokens {
    val Primary = Color(0xFF4F6FC4)
    val OnPrimary = Color(0xFFFFFFFF)
    val Secondary = Color(0xFFDFE4F0)
    val OnSecondary = Color(0xFF1B2230)

    val Canvas = Color(0xFFEEF1F7)
    val SurfaceLowest = Color(0xFFFFFFFF)
    val SurfaceLow = Color(0xFFF4F6FB)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceRaised = Color(0xFFFFFFFF)
    val SurfaceHighest = Color(0xFFDBE1EE)

    val Border = Color(0xFFCCD3E2)
    val BorderStrong = Color(0xFFB3BCCF)

    val Text = Color(0xFF1A2130)
    val TextMuted = Color(0xFF56607A)

    val Success = Color(0xFF0F9D63)
    val Warning = Color(0xFFB7791F)
    val Danger = Color(0xFFC0392F)
    val OnDanger = Color(0xFFFFFFFF)
}
