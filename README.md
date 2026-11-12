# GlassCar Ultra 4.1 · revisión en equipo

Launcher Android horizontal para el estéreo. La versión 4.0 incorpora la revisión de `IMG_0419.mp4`: tablero con mapa, música ampliada, accesos configurables y páginas de CarPlay, cámara e instrumentos.

La revisión 4.1 responde al nuevo video `IMG_0421.mp4`: mejora carátula, letras, escala, accesos y clima; elimina la marcha estimada y añade diagnóstico del estéreo. **La integración de las apps Google Maps/Tlink5/DVR y del volumen MCU sigue pendiente de identificar el firmware.** Ver [revisión y pendientes](docs/VIDEO-REVIEW-4.1.md).

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
| Volumen | En unidades detectadas con MCU se abre su panel nativo. El modo Android usa `STREAM_MUSIC`. No se ha implementado una barra MCU sincronizada: necesita la API del fabricante. |
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


## APK de revisión 4.1

La variante `review` genera `app/build/outputs/apk/review/app-review.apk` con paquete `com.glasscar.launcher.review` y nombre **GlassCar 4.1 Prueba**. Puede instalarse junto a 4.0 sin sustituirla ni depender de su certificado. Tiene permisos y ajustes propios. Para obtener los datos del equipo, abre Configuración → Diagnóstico del estéreo → Copiar diagnóstico.
