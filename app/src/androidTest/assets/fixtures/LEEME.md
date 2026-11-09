# Fixtures de pruebas instrumentadas

## `foto_prueba_1600x1200.jpg`

- **Fuente:** "Hoklas Road, Gomersal 20250102-113938.jpg", de RegionVisitor90, en Wikimedia Commons:
  https://commons.wikimedia.org/wiki/File:Hoklas_Road,_Gomersal_20250102-113938.jpg
- **Licencia:** CC0 1.0 (dominio público), http://creativecommons.org/publicdomain/zero/1.0/
- **Transformación:** se tomó la miniatura de 1920×1440 de Commons y se redimensionó a 1600×1200 en
  JPEG calidad 95 con `sips`, para parecerse a lo que entrega el HAL de la cámara con el target de
  captura actual.
- **Por qué esta foto:** escena exterior parecida a la evidencia (camino, campo, cielo) y sin placas
  ni personas (D13 de `docs/PLAN_REVELAR_FOTO.md`). Una imagen sintética se comprime distinto y
  falsearía los tiempos de WebP.
- **Uso:** `data/foto/LineaBaseReveladoTest.kt` (Task 1 del plan).
