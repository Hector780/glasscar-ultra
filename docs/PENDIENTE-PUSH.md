## 4.7.1 — CarPlay a pantalla completa (13 noviembre 2026)

### Evidencia recibida del usuario

- El usuario confirma que el brillo de 4.7 ya cambia físicamente. Conservar su implementación sin cambios; esta confirmación no implica haber probado todos los modos de luces o suspensión.
- Las fotos muestran CarPlay arriba a la izquierda, con espacio sobrante abajo y a la derecha, incluso después de ocultarse los controles.
- La foto de Trasera fue tomada al tocar el selector **sin R puesta**. No es evidencia de un fallo al entrar en reversa. El usuario reitera que quiere imagen trasera manual fuera de R; la reversa debería seleccionar esa vista automáticamente. Esa función sigue pendiente: el aviso de 4.7 no satisface la vista manual, y no demuestra que la cámara carezca de alimentación. No se cambia la cámara en esta corrección de CarPlay.
- Alcance autorizado ahora: corregir CarPlay, conservar brillo y overlay de volumen TW.
- Confirmación física posterior del usuario: **con R puesta, la cámara OEM se abre a pantalla completa y no está incrustada en GlassCar**. Sigue pendiente cumplir la trasera dentro del panel tanto manual como automática. Esta observación no confirma por sí sola si GlassCar recibió el flanco o cambió de pestaña debajo de la pantalla OEM. Se mantiene la cámara OEM disponible hasta contar con una alternativa físicamente verificada.

### Corrección

- En embedding, el pager compone Tlink al arrancar desde Tablero con un viewport reducido. Antes, ese tamaño se usaba para crear su display virtual y nunca se actualizaba al entrar a CarPlay.
- `LauncherScreen` entrega las dimensiones medidas de la raíz en **píxeles** a `CarPlayPanel` / `RealAppPanel`. Tlink espera esa medición y crea su display y buffer al tamaño completo desde el inicio.
- `onSurfaceTextureSizeChanged` y la recuperación de superficie restauran para Tlink el tamaño del buffer original completo. No se redimensiona ni recrea el display al cambiar de pestaña/cajón ni al revelar las barras. Se mantiene el mapeo táctil existente hacia las coordenadas del buffer.
- Maps y DVR conservan su selección de tamaño anterior. Diagnóstico de embedding agrega `vista=ANCHOxALTO` junto a `buffer=ANCHOxALTO`, display e inicios para comparar en el auto.
- Sin cambios a `TopwayBrightness`, `TopwayAudio`, `MainActivity`, `QuickControlBar`, `EmbeddingPolicy`, `EmbeddedAppService`, AIDL ni overlay TW. No se usa `-BuildOverlay`.
- Versión de entrega: **4.7.1**, code **12**, paquete `com.glasscar.launcher.embedtest` y firma local. Script `tools/Compilar-4.7.1.ps1`; se conserva el script 4.7.
- Pruebas de regresión: tamaño completo desde la precarga, espera por medición inicial, tamaños de Maps/DVR intactos y callbacks de superficie conservando buffer/display de Tlink. Las pruebas previas de `ImmersiveControlsTest` comprueban que las barras se superponen sin cambiar el área invitada.

Referencia del comportamiento de superficie: [TextureView.SurfaceTextureListener](https://developer.android.com/reference/android/view/TextureView.SurfaceTextureListener) y [SurfaceTexture.setDefaultBufferSize](https://developer.android.com/reference/android/graphics/SurfaceTexture#setDefaultBufferSize(int,int)). La corrección de código no equivale a haber probado Tlink/iPhone físicamente.

### Aceptación pendiente en el auto

1. Actualizar sobre 4.7 local sin desinstalar ni tocar el overlay. Entrar a CarPlay: debe llenar ancho y alto al ocultarse las barras.
2. Revelar/ocultar controles, cambiar a Tablero/Cámara y volver: mismo tamaño completo, toques alineados y sesión conservada.
3. Si queda margen o los toques no coinciden: copiar la línea completa `com.zjinnova.zlink` del bloque «Apps reales» con `buffer`, `vista`, `display`, `inicios`, `iniciosApp` y `movimientos`, más una foto sin barras visibles.

### Validación local

- `tools/Compilar-4.7.1.ps1`: `testDebugUnitTest lintDebug assembleEmbedding` — **BUILD SUCCESSFUL**.
- **124 pruebas**, 0 fallos, 0 errores, 0 omitidas (120 previas + 4 de CarPlay). Lint: **0 errores, 81 advertencias, 1 información**.
- `aapt` confirma `com.glasscar.launcher.embedtest`, code **12**, `4.7.1-ts10-integrated-real-app-prototype`.
- APK local: `INSTALAR_DESPUES/GlassCar-Ultra-4.7.1-local-embedding.apk`; SHA-256 `ee6a51a4bfc462ec3693696676e04e7ecdc7052a63758f252c98ac3c2fe93c92`.
- `apksigner` verifica firma válida y certificado SHA-256 `6a2cb5763f9f5de3ec92f9487a3d02b0ed1aa693d4b0f072cca138a57b531a7b`, idéntico al APK 4.7 local verificado de nuevo. Permite actualizarlo sin desinstalar. No se instaló nada en el estéreo desde esta sesión.
- Revisión de diff: implementación de brillo, audio TW y overlay sin cambios. Pendiente confirmar visualmente CarPlay en el auto.
- Commit y CI: por completar después de publicar.
