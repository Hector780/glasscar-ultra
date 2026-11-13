# GlassCar Ultra 4.6 · TS10

Entrega de prueba: paquete `com.glasscar.launcher.embedtest`, code 10. [Revisión 4.6, firmas y checklist para videos](docs/VERIFICACION-4.6.md). [Continuidad completa](docs/CONTINUAR-CODEX.md).

El popup TW ya fue eliminado mediante el módulo Magisk, según confirmación del usuario; no requiere rehacerlo al actualizar el launcher. Persistencia de apps, USB DVR, llamadas y CAN siguen sujetos a validación física. Atención: la entrega anterior con nombre «4.5 embedding» contenía una variante cameradiag; verificar el paquete interno.

## Historial 4.5 · integración persistente TS10

Variante de prueba `embedding`, paquete `com.glasscar.launcher.embedtest`, versión `4.5-ts10-integrated-real-app-prototype` / code 9. Requiere Android 10/API29 y Shizuku activo/autorizado.

CarPlay ocupa toda la pantalla del launcher con controles por gestos. Las sesiones de Maps, Tlink y DVR se conservan al cambiar de pestaña. Se añadieron consultas/reconexión CAN y retorno a CarPlay mediante la señal de llamada observada en TWService. [Cambios, evidencia y prueba física](docs/INTEGRACION-4.5.md).

**No equivale a una reparación física confirmada:** falta validar CAN, continuidad de proyección/grabación, señal de llamada y gestos en el estéreo. El video DVR independiente y la supresión de ventanas/ringtone OEM siguen pendientes. No borrar HD Car DVR ni Tlink. [Registro para continuar](docs/CONTINUAR-CODEX.md).

## Historial anterior

### GlassCar Ultra 4.2 · prueba TS10

Adaptador experimental identificado en las APK originales del equipo: lectura del volumen/escala/silencio mediante TWUtil y cambio absoluto desde el deslizador con confirmación del estéreo. Solo se activa en el perfil UIS7862/API29 inspeccionado. Si falla el acceso o no llega confirmación, ofrece el panel nativo. **Funcionamiento físico pendiente de prueba.**

La variante `review` ahora usa `com.glasscar.launcher.ts10test` / **GlassCar 4.2 Prueba**, instalable junto a las versiones anteriores, con ajustes propios. Detalles, evidencia y prueba: [docs/TS10-AUDIO.md](docs/TS10-AUDIO.md). Maps/Tlink, DVR y CAN siguen pendientes.

## Revisión de interfaz 4.1

Launcher Android horizontal para el estéreo. La versión 4.0 incorpora la revisión de `IMG_0419.mp4`: tablero con mapa, música ampliada, accesos configurables y páginas de CarPlay, cámara e instrumentos.

La revisión 4.1 responde al nuevo video `IMG_0421.mp4`: mejora carátula, letras, escala, accesos y clima; elimina la marcha estimada y añade diagnóstico del estéreo. **La integración de las apps Google Maps/Tlink5/DVR sigue pendiente; el adaptador de volumen TS10 de 4.2 requiere prueba en el equipo.** Ver [revisión y pendientes](docs/VIDEO-REVIEW-4.1.md).

## Uso

- Mantén pulsado un acceso rápido para elegir su aplicación. Música y mapa comparten la elección con sus paneles. Apple Music y Google Maps tienen prioridad al instalarse.
- El engrane lateral abre la configuración; las elecciones y el tema se conservan al reiniciar.
- Desliza horizontalmente o toca las pestañas para cambiar de página. Un gesto vertical sobre el fondo del tablero abre las aplicaciones; dentro de un mapa, widget o lista se respetan sus propios gestos. El botón de aplicaciones siempre está disponible.
- Toca el clima para consultar fuente, hora, ubicación, actualizarlo o elegir una app meteorológica. La temperatura es una estimación exterior de Open-Meteo; no es un sensor del automóvil.
- En música, «Letra» consulta LRCLIB para la pista actual. No todas las canciones tienen letra; las sincronizadas se resaltan con la posición de reproducción. Se necesita acceso a notificaciones para leer las sesiones multimedia.
- La barra lateral recuerda las aplicaciones abiertas desde el launcher. El permiso opcional de acceso de uso añade aplicaciones abiertas por otros medios.

## Integraciones y límites reales

| Función | Implementación |
| --- | --- |
| Mapa integrado | Mapa GPS de OpenStreetMap por defecto. No calcula una ruta. Las instrucciones de Google Maps se leen de su notificación al navegar, si se autoriza acceso a notificaciones. |
| Google Maps integrado | Se activa al compilar con una clave de Maps SDK for Android, por propiedad `MAPS_API_KEY` o variable de entorno. Restringir la clave al paquete y certificado de firma. Requiere servicios Google compatibles. La app completa de navegación sigue abriéndose con «Abrir». |
| Cámara frontal | Vista Camera2 si el estéreo expone la cámara a Android. Se puede elegir el ID de cámara, usar un widget del proveedor o abrir la app DVR asignada. Una cámara propietaria no expuesta necesita integración del fabricante. |
| Tlink5/CarPlay | Página dedicada que abre la app asignada al llegar a ella. Si el proveedor ofrece widget, puede mostrarse dentro del panel. No incrusta arbitrariamente la actividad de Tlink5 ni implementa el protocolo CarPlay. |
| Volumen | En el perfil TS10 inspeccionado, adaptador TWUtil de prueba con nivel/máximo/silencio y confirmación del cambio. Si no hay lectura válida o confirmación, ofrece el panel nativo. El modo Android usa `STREAM_MUSIC` por separado. |
| Brillo | Ajuste real `Settings.System`, con permiso para modificar ajustes y mínimo de 12 %. Se elimina la atenuación independiente de la ventana. Si el firmware no respeta este ajuste, usar su panel nativo. |
| Voltaje | Sólo aparece un valor si llega una lectura CAN reconocida y reciente; de otro modo indica que no está disponible. Se eliminó el «12V» fijo. |

El APK no sustituye los instrumentos del automóvil. La selección manual de marcha en la página de instrumentos es visual; se identifica como tal y no envía comandos al vehículo. La velocidad GPS y las lecturas CAN dejan de tratarse como actuales al caducar. Sin lectura CAN no se infiere P/D: la marcha se muestra desconocida.

## Compilar y validar

Requisitos: JDK 17 completo, Gradle 8.7 y Android SDK 34 (Build Tools 34.0.0). Define `ANDROID_HOME` o `sdk.dir` en `local.properties`.

```sh
gradle --no-daemon testDebugUnitTest lintDebug assembleDebug assembleRelease assembleReview
```

APK: `app/build/outputs/apk/debug/app-debug.apk` y `app/build/outputs/apk/release/app-release.apk`. GitHub Actions ejecuta las mismas comprobaciones y conserva APK y reportes. Las pruebas Robolectric cubren reglas de asignación, preferencias, datos caducos, reproducción y gestos; generan capturas de interfaz sin depender de servicios externos.

Se conserva la firma de desarrollo que utilizaba el proyecto, también en la variante release. Una compilación de otra máquina puede tener un certificado diferente del APK instalado; Android no permite actualizar una instalación con otra firma. Para actualizaciones estables debe configurarse la clave original de firma fuera del repositorio. No desinstales la versión anterior sin guardar tus ajustes.

Ver [seguimiento de los 30 puntos del video](docs/VIDEO-REVIEW.md). Las integraciones de hardware requieren una prueba en el estéreo real, con el iPhone conectado para CarPlay.


## APK de prueba 4.2

La variante `review` genera `app/build/outputs/apk/review/app-review.apk` con paquete `com.glasscar.launcher.ts10test` y nombre **GlassCar 4.2 Prueba**. Puede instalarse junto a 4.0 y 4.1 sin sustituirla ni depender de su certificado. Tiene permisos y ajustes propios. Para obtener los datos del equipo, abre Configuración → Diagnóstico del estéreo → Copiar diagnóstico.
