# GlassCar 4.5: integración persistente

Estado: cambios de software; falta probar en el TS10. ADB no detectó ningún dispositivo el 13 de noviembre de 2026. No se instaló ni modificó el estéreo.

## Cambios

- Cada app incrustada conserva controlador, pantalla virtual, tarea y SurfaceTexture al cambiar de pestaña o abrir el cajón. El cierre se hace al destruir la raíz del launcher, al fallar la sesión o al reintentar después de un fallo. No se mantiene tras muerte del proceso/reinicio del estéreo.
- La superficie conservada se conecta a la nueva TextureView. No se redimensiona el display al ampliar Maps; se escala su imagen y se transforman las coordenadas táctiles. Evita recreaciones por cambio de tamaño, aunque puede cambiar la proporción visual del mapa ampliado. No garantiza que el firmware conserve actividades bajo presión de memoria.
- CarPlay utiliza todo el área del launcher, sin márgenes, dock ni barras permanentes. Gesto hacia abajo desde los 18 dp superiores muestra pestañas; desde los 18 dp inferiores hacia arriba muestra volumen/brillo. Son superposiciones: no redimensionan CarPlay. Se ocultan tras cuatro segundos sin interacción; al manipularlas se renueva el plazo.
- Los cambios de nivel/silencio de TW muestran volumen y brillo; también las teclas de volumen que llegan al launcher. La primera lectura no abre la barra. En modo Android se ajusta STREAM_MUSIC sin FLAG_SHOW_UI; en modo nativo se mantiene el control MCU existente.
- Se retiraron «Confirmando volumen…», el toast de confirmación fallida y la explicación permanente del cajón. Los fallos de audio siguen disponibles en diagnóstico; cuando no hay lectura válida se ofrece el icono de acceso al control nativo.
- Dentro de la variante embedding, cajón, accesos, recientes y openPackage dirigen Tlink a la pestaña CarPlay. La actividad de Tlink permanece habilitada porque el panel la necesita. Esto no bloquea aperturas iniciadas por el firmware ni por otro launcher.
- CAN, ubicación y audio viven entre START/STOP, conservándose al perder solamente el foco. CAN consulta getters cada dos segundos en un hilo separado; fallar al registrar callbacks no cancela los getters. Cada conexión tiene su generación, callback, receptor de muerte y timeout. Respuestas/desconexiones antiguas no alteran la nueva conexión. No se cambió el AIDL ni el protocolo del automóvil.
- Diagnóstico incluye variante/paquete, número de inicios, display y buffer por app, datos crudos reconocidos de CAN y estado de señal de llamada. No registra interlocutores ni contenido de llamadas.

## Llamadas: evidencia nueva

La inspección de `TW.apk` original (SHA-256 `f17c234eb13a016aee506e84fd6e2e0bc963f790eb9519c274bf3cccfd3810a2`) encontró en `com.tw.service.u.onReceive`:

- Acción `com.zjinnova.zlink`, extra de texto `status`.
- Estados `PHONE_CALL_ON`, `PHONE_CALL_OFF`, `DISCONNECT`; también hay estados distintos de conexión y Siri.

GlassCar escucha únicamente esos estados relevantes mientras está visible y cambia a CarPlay cuando empieza una llamada. No envía broadcasts a TW ni replica escrituras MCU del receptor original. Las notificaciones CATEGORY_CALL de Tlink son una segunda fuente, si se autorizó acceso a notificaciones. No se infieren llamadas de texto musical, Siri o conexión. El emisor del broadcast no está autenticado: su único efecto aquí es seleccionar una pestaña, sin ejecutar comandos, aceptar llamadas ni exponer información.

**Pendiente físico:** comprobar si PHONE_CALL_ON llega durante el timbrado o al contestar; el código receptor original demuestra el contrato reconocido, pero no cuándo lo emite Tlink 5.5.7. El cambio de pestaña no puede recuperar por sí solo una actividad que el firmware haya movido a la pantalla principal.

## Avisos originales: pendiente específico

System UI original, versión 10/code29, SHA-256 `08bc9bcdcaa1e9c724f19e1605582ef3e09cb986eac9f60447645dba9167258f`: `VolumeUI.start` lee recursos booleanos `enable_volume_ui` y `enable_safety_warning`; `VolumeDialogImpl.showH` abre el diálogo. No se encontró un ajuste Settings que desactive selectivamente ese diálogo en los métodos revisados. `sysui_volume_down_silent/up_silent` son políticas de volumen, no interruptores del popup. El botón flotante EasyTouch es otra función de SystemUI: `com.my.maple_easytouch` es el nombre de su archivo SharedPreferences (no prueba otro paquete instalado), con la clave `assistivetouch`. El mosaico nativo `SuspensionTile` cambia esa preferencia y muestra/retira el botón. Si el icono señalado resulta ser EasyTouch, usar ese interruptor nativo; no desinstalar un paquete basándose en el nombre del archivo.

No se deshabilitó SystemUI ni se modificaron recursos OEM. Falta identificar qué ventana aparece realmente en este firmware (Android, TW o EasyTouch), antes de sustituir únicamente esa presentación. El tratamiento de teclas Android sin FLAG_SHOW_UI no elimina una ventana generada por el MCU. Tampoco se identificó todavía el componente que genera el popup/ringtone Bluetooth; no se silenciaron los streams de llamada ni Bluetooth.

`tools/Capturar-TS10.ps1 -Event Volume` (o `Call`, `Dvr`, `Can`) permite obtener versiones y propietarios de ventanas por ADB; no modifica el equipo y no guarda cuerpos de notificaciones ni números telefónicos. Se necesita un estéreo conectado y autorizado. Revisar el reporte junto al diagnóstico del launcher.

## DVR independiente

La USB real es `1b3f:8301`, almacenamiento SCSI/Bulk 8/6/80, no una UVC demostrada. Se revisó de nuevo `XUsbMassStorageDevice.getInputStream`: crea `XUfsDeviceInputStream` sobre el archivo `elen1`; `XUFSCameraService` delega apertura/lectura/comandos a JNI. El encuadre/formato y la secuencia de inicio siguen sin reconstruirse ni probarse. La copia estática de `elen1` no equivale al video en vivo.

La entrada pública `Splash` pasa a MainActivity y termina; la conservación de su tarea elimina el relanzamiento provocado por las pestañas, pero no garantiza que la DVR admita video en un display secundario. No existe todavía una implementación independiente de video/grabación. No se borró HD Car DVR, no se reclamó su USB y no se sustituyó la grabación de segmentos de un minuto. Antes de retirarla hay que verificar vista en vivo, grabación continua, integridad de segmentos y recuperación al reconectar.

## Prueba en el equipo

1. Instalar la variante **embedding 4.5 / code9**, con Shizuku activo/autorizado. Revisar firma antes de actualizar; no desinstalar DVR ni Tlink.
2. Abrir una ruta, conectar CarPlay y abrir DVR. Alternar diez veces Tablero/CarPlay/Cámara y el cajón: conservar ruta/conexión; diagnóstico debe mantener `inicios=1` y display por app. Comparar segmentos DVR antes/durante/después.
3. En CarPlay, verificar imagen hasta los cuatro bordes y que los gestos muestran/ocultan controles sin recortar ni reiniciar proyección. Probar también tocar controles cerca de los bordes: el firmware puede reservar gestos del sistema.
4. Cambiar volumen desde el volante: comprobar audio y barras propias. Capturar el propietario si sigue el popup original.
5. Llamar al iPhone desde Tablero: comprobar cambio a CarPlay, momento de la señal, audio al contestar/colgar y cualquier popup duplicado. Registrar solo estado de señal, no números.
6. Revisar CAN después de varias pestañas y de salir/regresar. Si los getters siguen devolviendo `-1`/vacíos, continuar con configuración del decodificador/servicio y evidencia del dispositivo; no inventar valores ni dar por reparado el CAN.

APIs de referencia: [conservación de SurfaceTexture](https://source.android.com/docs/core/graphics/arch-tv), [TextureView](https://developer.android.com/reference/android/view/TextureView), [NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService).
