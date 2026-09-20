package com.ai27.app.custodio.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Escala de formas del sistema: geometría de baja curvatura con 4dp de base.
 *
 * Nocturne define `--ui-radius: 0.25rem` (4px) como radio base, con 2px para elementos
 * pequeños, 8px para contenedores grandes y 12px para hojas. Los documentos de diseño
 * prohíben expresamente las formas de píldora en botones y tarjetas; las únicas
 * excepciones admitidas son el botón redondo de grabación y los contadores de
 * notificación pequeños, que se declaran como `CircleShape` en su propio componente.
 *
 * Es una sola escala para los dos temas: un cambio de tema altera colores, no geometría.
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(12.dp),
)
