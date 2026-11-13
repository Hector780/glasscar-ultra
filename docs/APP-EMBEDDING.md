# Tlink5, Google Maps y HD Car DVR — paneles API 29

## Prioridad y estado real

El usuario pide las aplicaciones completas dentro de GlassCar, con interacción. Esta es la prioridad actual; cámaras y brillo quedan como líneas separadas. El usuario confirmó el 12-11-2026 que la barra de volumen nativo **ya funciona**.

Se implementó una ruta experimental de apps reales con VirtualDisplay + un UserService de Shizuku iniciado y autorizado por el usuario. La variante `embedding` sustituye los paneles Maps/CarPlay por estos hosts. No usa OSM, Maps SDK, WebView, capturas ni widgets para fingir la integración. Tampoco contiene ni modifica APK de Google/Tlink.

**Maps y Tlink5 confirmados por el usuario en el TS10:** las fotografías muestran Google Maps y CarPlay proyectado dentro de GlassCar, y el usuario indica que funcionan. Esto valida la ruta básica de incrustación; no cubre todas las combinaciones de teclado, reconexión y reanudación. La nueva distribución 4.3 y la incrustación de HD Car DVR todavía requieren prueba física. El APK Topway anterior solo añadió datos del vehículo y no incluía estos cambios visuales.

## Evidencia de este equipo

Diagnóstico recibido en `sin_titulo.txt`: hardware ums512_1h10, modelo ums512_1h10_Natv, API 29, compilación QP1A.190711.020; el texto de versión dice Android 13, pero la selección de API debe basarse en SDK_INT=29. Pantalla 2000×1200, 240 dpi. GlassCar reportó ACTIVITY_EMBEDDING, MANAGE_ACTIVITY_TASKS e INJECT_EVENTS no concedidos.

| APK inspeccionado | Evidencia |
|---|---|
| Tlink5.apk, `com.zjinnova.zlink`, 5.5.7 (505007), SHA256 `8f58cec0d7921a2e2de4e07620ffee389e0b85dcc6cdce9646e2645fbb434a9c` | `com.zjinnova.android.zlink.features.main.MainActivity`, exportada, singleTask; aplicación y actividad `resizeableActivity=false`; sin allowEmbedded; meta-data isMainSplitScreenEnable=false. |
| Maps.apk, `com.google.android.apps.maps`, 26.45.00.1011720968, SHA256 `16cbd3648a7698467874c0c9cd3a99b2c2ea330c0eb94dcf55d3d7dca1da5650` | `com.google.android.maps.MapsActivity`, exportada, singleTask, resizeableActivity=true; sin allowEmbedded. La GhostActivity de Android Auto sí tiene allowEmbedded pero exige otro permiso y no sustituye MapsActivity. |

Tlink declara SecondaryScreenService sin exportación ni filtros (no público); DaemonService está exportado y exige zjinnova.android.permission.ZLINK_SERVICE, pero no se ha identificado un contrato para Surface/entrada. Su DEX visible usa MyWrapperProxyApplication y no contiene las implementaciones principales esperadas. No se intentó evadir su empaquetado/protecciones ni se inventó un SDK.

## Por qué hace falta un puente autorizado

En AOSP Android 10, `ActivityStackSupervisor.isCallerAllowedToLaunchOnDisplay` (tag android-10.0.0_r1) permite temprano al llamador con INTERNAL_SYSTEM_WINDOW; para una pantalla virtual de una app normal exige allowEmbedded del destino y, además, ACTIVITY_EMBEDDING o presencia del llamador. Ser dueño de una pantalla pública no elimina esa comprobación previa.

El manifiesto AOSP de Shell declara INTERNAL_SYSTEM_WINDOW, INJECT_EVENTS y MANAGE_ACTIVITY_STACKS. Un UserService de Shizuku puede ejecutar operaciones con la identidad shell autorizada; el proceso de GlassCar sigue como app normal. En firmware OEM estos permisos/APIs pueden variar. No se asume que `pm grant` pueda conceder permisos signature a GlassCar ni que instalarla como priv-app baste para permisos signature.

Fuentes primarias:
- [ActivityStackSupervisor Android 10](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-10.0.0_r1/services/core/java/com/android/server/wm/ActivityStackSupervisor.java)
- [Permisos de Shell Android 10](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-10.0.0_r1/packages/Shell/AndroidManifest.xml)
- [Permisos de plataforma](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-10.0.0_r1/core/res/AndroidManifest.xml)
- [Shizuku API y UserService](https://github.com/RikkaApps/Shizuku-API)
- [Arranque oficial de Shizuku](https://shizuku.rikka.app/guide/setup/)

## Implementación

- Variante `embedding`: paquete `com.glasscar.launcher.embedtest`, nombre **GlassCar Apps Reales Prueba**, instalada aparte de las pruebas de audio/cámara.
- `RealAppPanel`: la app ocupa toda la superficie, sin título, marco de diagnóstico ni botones de prueba. Arranca cuando el panel está visible, la superficie está disponible y Shizuku está autorizado. La ayuda y Reintentar aparecen únicamente durante un fallo; los detalles técnicos siguen en Diagnóstico del estéreo.
- CarPlay oculta el dock lateral y reduce márgenes; conserva las cuatro pestañas, volumen y brillo. Al volver a Tablero reaparece el dock. No lanza Tlink externamente al seleccionar la pestaña en la variante embedding.
- Maps ocupa el 64% del tablero y recupera el espacio antes dedicado a controles de prueba. El botón Ampliar mapa permite usar el área central completa; Reducir mapa o Tablero restauran la distribución. Los gestos sobre las apps quedan para sus mapas/listas; se cambia de página con las pestañas.
- Cámara usa HD Car DVR mediante su actividad pública `com.williexing.android.apps.xcdvr2.Splash`, identificada en el APK 2.13.3-gsj/3311, paquete `com.williexing.android.apps.xcdvr1`. No se abre su alias privado ni se llama a sus servicios USB. Su imagen en una pantalla secundaria queda pendiente de validación física.
- `RealAppController`: crea VirtualDisplay PUBLIC | OWN_CONTENT_ONLY | DESTROY_CONTENT_ON_REMOVAL; al salir detiene sesión/libera display/surface y desvincula UserService. Ajusta buffer y display al cambiar de tamaño. Inicio/cierre se serializan para no destruir el display mientras el comando de inicio sigue pendiente; vuelve a arrancar al regresar a primer plano.
- Antes de iniciar exige FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS: AOSP canPlaceEntityOnDisplay rechaza displays secundarios cuando el sistema no soporta esa capacidad. Si falta, no se envía el comando de lanzamiento ni se ofrece apertura externa como sustituto.
- `EmbeddedAppService`: UserService no registrado como Service exportado. Se obtiene exclusivamente mediante Shizuku. Sólo acepta los tres componentes exactos (Maps, Tlink5 y Splash de HD Car DVR); comprueba propietario real y nombre del display, UID del llamador, vida del Binder y displayId > 0. No ofrece ejecución arbitraria de comandos ni entrada sobre la pantalla principal.
- Inicio con argumentos separados de `/system/bin/am start --user current --display ID -n COMPONENT -f 0x10000000`, sin shell interpolado, force-stop, edición de settings ni privilegios persistentes. Después consulta stacks y exige que la app esté realmente en ese display antes de habilitar interacción. Si singleTask/firmware la desvía, se informa el fallo; no se considera integración completada.
- Toques por MotionEvent parcelado, conservando punteros y acciones; inyección con displayId del panel. Atrás también apunta a ese display. No se capturan ni almacenan imágenes ni texto de las apps. Los mensajes técnicos se incluyen en Diagnóstico del estéreo.
- Shizuku API/provider 13.1.5. Provider protegido con INTERACT_ACROSS_USERS_FULL sólo en el manifest de la variante embedding. No se solicitan permisos signature para fingir acceso.

## Prueba física necesaria

1. Confirmar que el TS10 dispone de ADB accesible o root existente. No rootear ni flashear como paso automático. API 29 normalmente requiere computadora para iniciar Shizuku por ADB; no asumir el emparejamiento inalámbrico de Android 11 sólo por la etiqueta Android 13.
2. Instalar Shizuku desde su [fuente oficial](https://shizuku.rikka.app/download/), iniciarlo por el método que indique para ese equipo y autorizar únicamente esta instalación de GlassCar cuando lo solicite. Sin root, suele requerir repetir arranque tras reiniciar el sistema.
3. Instalar GlassCar Apps Reales Prueba 4.3. Los APK de prueba de GitHub Actions usan una firma debug generada en cada runner; no se garantiza una actualización sobre otro APK. Si Android informa un conflicto de firma, guardar primero la configuración que se quiera conservar y desinstalar únicamente GlassCar Apps Reales Prueba antes de instalar el nuevo APK (se borran sus preferencias locales). Con el equipo estacionado y Shizuku activo, Maps debe abrirse automáticamente. Autorizar GlassCar si se solicita; si Shizuku estaba apagado, iniciarlo y pulsar Reintentar.
4. Probar arrastre, zoom y búsqueda/teclado en Maps. Ampliar y reducir el mapa, comprobando imagen, coordenadas táctiles y conservación de la ruta.
5. Abrir CarPlay: debe ocupar casi toda la pantalla, sin dock ni textos de prueba. Conectar el iPhone y comprobar proyección, audio y gestos. Cambiar a Tablero y volver; probar también salir de GlassCar y regresar.
6. Abrir Cámara: debe iniciar HD Car DVR dentro del panel. Comprobar imagen real y controles de esa app. Si falla, copiar el diagnóstico; Maps/Tlink funcionando no garantizan que DVR admita la misma ruta.
7. Confirmar que siguen visibles las cuatro pestañas y volumen/brillo, y que el dock reaparece en Tablero e Instrumentos. Los cambios no modifican el contrato AIDL de CarInfoService.

## Límites conocidos

El código está deliberadamente limitado a API 29. No se ha probado teclado/IME en pantalla secundaria, multiventana OEM, superficie de video protegida, competencia de foco ni reanudación de CarPlay. No modifica globalmente force_resizable_activities ni crea overlays externos como sustituto del panel. Si el firmware bloquea el puente, revisar evidencia antes de ampliar privilegios o cambiar arquitectura.

## Actualización del resto del proyecto

- **Audio:** el usuario confirma funcionamiento de la barra. Reporte TW nivel 10/30 vigente; no había solicitud pendiente en ese instante. Conservar integración actual.
- **Brillo:** no funciona físicamente según usuario; diagnóstico Android 255/255, escritura permitida, modo manual. No afirmar que cambiar Settings.System modifica retroiluminación MCU.
- **DVR:** dispositivo físico confirmado `1b3f:8301`, interfaz 8/6/80, bulk IN 0x81 / OUT 0x02, paquete 512; GlassCar sin permiso USB. Los tres IDs Camera2 no quedan identificados como DVR por eso.
- `uCarDvr.apk` del USB es versión 2.13.2-gsj/code3310 del mismo paquete, anterior a la instalada 2.13.3-gsj/code3311. No instalarla como corrección.
- Copia `elen1`: 307200 bytes, 306520 bytes cero; SHA256 `f07ec4eac29033f9a5015f87e3f30aad08590f205d0f40fbecc3d97178c47ee1`. `zlog`: 10 bytes, patrón 4d cc repetido, SHA256 `609135dea123ad0930a8538110f4428e11f4e72c1f975f0c97687ebd52fe572c`. Estas copias estáticas no demuestran un flujo en vivo ni proporcionan el protocolo completo.
- Reversa instalada: `com.tw.reverse`, TS10_eclipse.20230104/code42. Sigue faltando ese APK para la investigación trasera. Para Maps/Tlink no hacen falta más APK en este punto.

## Continuidad

Base: `1f16d2fbd84057dfc7abb8b85be2554387f27a3d`, rama cámara. Trabajo de embedding en `work/real-app-embedding`; no cambiar ramas ajenas ni main mientras otra IA prueba audio/cámara. Consultar Actions de esta rama para el último estado compilado y outputs/GLASSCAR-RETOMAR.txt del workspace original para rutas, APK y validación.

