package com.ai27.app.custodio.data.marcaDeAgua

import android.graphics.Bitmap

/**
 * Implementación Android de [ImagenMapa]: envuelve el `Bitmap` del snapshot. El proveedor de
 * mapa la crea y `BitmapCodecFoto` la desenvuelve; en medio, `RevelarFoto` solo ve [ImagenMapa].
 */
class BitmapImagenMapa(val bitmap: Bitmap) : ImagenMapa
