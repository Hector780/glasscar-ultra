# Revisión de IMG_0421.mp4 · versión 4.0 instalada

Fuente: video de 103.5 segundos, voz transcrita y capturas inspeccionadas. Base: main `373d96829473f4bb7a6247ea10a19b7b81f8e154`. Los tiempos corresponden a los comentarios de voz; el video cambia de pantalla durante varios comentarios.

## Solicitudes y estado del trabajo

| Tiempo | Solicitud del usuario | Evidencia en 4.0 / cambio 4.1 |
|---|---|---|
| 00:00–00:05 | Quitar franjas laterales de la miniatura musical | El código usa un degradado naranja/magenta fijo detrás de carátulas ajustadas. Se sustituye por fondo derivado de la carátula; se conserva completa en primer plano. |
| 00:05–00:13 | Letra más dinámica, grande, con menos líneas | Antes se mostraba una lista larga. Ahora hay línea actual destacada y contexto anterior/siguiente con transición; letras sin sincronización se recorren manualmente, sin simular tiempos. |
| 00:13–00:18 | Accesos más grandes | Los cuatro accesos pasan a una fila bajo el mapa; iconos de 56 dp y etiquetas de 13 sp, barra lateral de 60 dp. Escala adaptable a pantallas que reportan densidad demasiado baja. |
| 00:16–00:20 | Clima más grande y con información visible | Panel ancho independiente bajo música, temperatura de 40 sp, condición, viento y humedad. No se inventan datos faltantes. |
| 00:19–00:36 | La aplicación Google Maps dentro del tablero, no OSM ni widget | **Pendiente de integración de aplicaciones.** El mapa actual sigue siendo de ubicación; se identifica explícitamente como OSM/SDK. Activar Maps SDK no equivale a incrustar la aplicación Google Maps. |
| 00:35–00:56 | Tlink5 dentro de la página CarPlay; no tiene widget | **Pendiente de integración.** Se deja de ofrecer un widget inexistente. El selector sólo presenta proveedores de la app elegida; nunca widgets ajenos. Abrir Tlink externamente no satisface la integración solicitada. |
| 00:56–01:03 | Cámara frontal no funciona; necesita la app real | **Hardware pendiente.** Camera2 añade espera visible, tiempo máximo sin primer fotograma y reintento. Una cámara no expuesta por el fabricante no queda solucionada por este cambio. |
| 01:03–01:14 | Instrumentos reales y revisar CAN | Se elimina P/D inferido a partir de velocidad y el cero de velocidad sin señal. Marcha desconocida queda sin selección y marcada «Sin lectura de marcha CAN». Protocolo del equipo aún no confirmado. |
| 01:14–01:24 | Botones del volante sí mueven volumen | Evidencia de control físico; no demuestra por sí sola que la APK tenga acceso al bus ni identifica su protocolo. Se añade reporte de modelo, Android, hardware y eventos de las acciones CAN candidatas. |
| 01:24–01:35 | Una sola barra vinculada al volumen real; esconderla no resuelve | **Pendiente del adaptador del estéreo.** Diagnóstico muestra STREAM_MUSIC en tiempo real para contrastarlo con botones físicos. No se declara sincronizado el MCU. |
| 01:35–01:39 | Brillo conectado al ajuste real de Android | El código ya escribe Settings.System, pero falta comprobar que este firmware lo refleje en el panel. Diagnóstico muestra valor, modo y permiso. No se declara resuelto físicamente. |
| 01:39–01:43 | Determinar si hace falta modificar Android y no sólo launcher | Hace falta identificar firmware y capacidades. No se ha modificado ni flasheado Android. |

## Próxima evidencia del equipo

En Configuración → Diagnóstico del estéreo, con el auto estacionado, cambiar volumen con el volante, observar STREAM_MUSIC y copiar el diagnóstico. Se incluyen fabricante/modelo, compilación Android, tamaño/densidad, apps elegidas y versiones, cámaras Camera2, proveedores de widgets, permisos relevantes y los últimos 20 broadcasts candidatos recibidos mientras el launcher está activo. No se incluyen ubicación, seriales, cuentas ni contenido multimedia. Las claves/tipos de extras CAN se conservan; sólo se muestran valores numéricos y booleanos.

Este diagnóstico no detecta todos los protocolos propietarios ni prueba por sí solo que una app acepte embedding. Hará falta información del fabricante/SDK o inspección dirigida del sistema si sus servicios no se exponen.

## Arquitectura a resolver

- El embedding entre aplicaciones requiere soporte del sistema y consentimiento de la app receptora en la API pública; no basta envolver un Intent en AndroidView.
- El launcher de Android Automotive usa TaskView y permisos privilegiados. No se asume que este estéreo sea Android Automotive ni que una APK común tenga esos permisos.
- Root/ADB/instalación privilegiada son posibles rutas de investigación según el firmware, no una promesa de compatibilidad ni algo aplicado en este cambio.

Referencias primarias:
- https://developer.android.com/develop/ui/views/layout/activity-embedding
- https://android.googlesource.com/platform/packages/apps/Car/Launcher.git/+/refs/tags/android-security-13.0.0_r1/AndroidManifest.xml

## Validación

Se añaden pruebas de marcha desconocida/CAN caduco, escala persistente, reporte de diagnóstico, estado CarPlay sin widget y renderizado en panel mdpi de 1920×1080. Ejecutar testDebugUnitTest, lintDebug, assembleDebug y assembleRelease mediante GitHub Actions. Se entrega una variante review separada, instalable junto a 4.0 para conservar su instalación y configuración. El estado final del flujo determina si el APK se entrega como compilado; no reemplaza la prueba en el estéreo.

## Seguimiento 4.2

La prueba del volante confirmó que STREAM_MUSIC no sigue el volumen nativo. Se identificó el protocolo de volumen en System UI del equipo y se implementó un adaptador de prueba con confirmación de lectura. No está validado todavía en hardware. Ver [TS10-AUDIO.md](TS10-AUDIO.md).
