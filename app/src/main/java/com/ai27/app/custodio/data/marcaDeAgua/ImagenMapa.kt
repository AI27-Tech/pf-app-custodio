package com.ai27.app.custodio.data.marcaDeAgua

/**
 * Envoltorio opaco de un `Bitmap` de mapa. Deja que [com.ai27.app.custodio.data.foto.RevelarFoto]
 * mueva el snapshot del proveedor ([com.ai27.app.custodio.data.mapa.ProveedorSnapshotMapa]) al
 * codec ([com.ai27.app.custodio.data.foto.CodecFoto]) sin que aparezca `android.graphics.Bitmap`
 * en código que se prueba en JVM. La implementación Android es [BitmapImagenMapa].
 */
interface ImagenMapa
