# Continuidad: integración permanente TS10 (solicitud 13 noviembre 2026)

## Punto de partida

- Repositorio Hector780/glasscar-ultra, carpeta TS10_root/glasscar-ultra, rama work/topway-carinfo.
- Base publicada y probada automáticamente: c099093 (4.4, versionCode 8). 81 pruebas; lint 0 errores/70 advertencias. APK anterior: ../INSTALAR_DESPUES/GlassCar-4.4-c099093.apk.
- El usuario confirma fallos físicos en 4.4. NO considerar las pruebas automáticas evidencia de funcionamiento del hardware.

## Solicitud vigente completa

1. Corregir CAN: voltaje intermitente; velocidad/marcha/luces no funcionan según usuario. No se dispone aún de diagnóstico reciente.
2. Maps, CarPlay y HD Car DVR no deben reiniciarse al cambiar de pestaña.
3. Investigar DVR sin depender de su interfaz/app; conservar grabación por segmentos de un minuto. No borrar DVR antes de tener sustituto funcional y grabación verificada. Usuario no tiene a mano el error exacto.
4. CarPlay literalmente a pantalla completa. Pestañas solo con gesto corto desde borde superior hacia abajo; volumen/brillo desde borde inferior hacia arriba. Mostrar estos últimos también cuando se cambia volumen desde volante.
5. Quitar texto fugaz de confirmación de volumen y texto explicativo grande del cajón.
6. Sustituir el popup de volumen original por controles propios; investigar propietario/mecanismo exacto sin deshabilitar SystemUI completo.
7. Al recibir llamada del iPhone en CarPlay, ir a pestaña CarPlay. Evitar popup/ringtone Bluetooth nativos duplicados si puede identificarse una configuración específica; no silenciar el audio de la llamada ni CarPlay a ciegas.
8. Dentro de GlassCar, toda apertura de Tlink debe ir al panel, sin salida externa. No deshabilitar su actividad ni quitar su permiso de lanzamiento, porque el panel depende de ella. Bloqueo global desde otros launchers exige intervención del dispositivo y prueba.
9. Publicar rama, esperar Actions, descargar APK con nombre/version explícitos, verificar SHA/version/firma y actualizar guía. Mantener continuidad en este documento.

## Hallazgos confirmados al retomar

- LauncherScreen utiliza pager y condiciones settledPage; elimina la composición de los paneles al cambiar de pestaña. RealAppPanel.onDispose destruye VirtualDisplay con DESTROY_CONTENT_ON_REMOVAL: elimina la tarea invitada. Causa directa del reinicio.
- MainActivity.onPause detiene CAN, ubicación y TopwayAudio aunque launcher siga visible al ceder foco a la app invitada. Media ya se movió a START/STOP en 4.4.
- TopwayCarInfo solo llama getters al conectar. Datos de marcha/voltaje caducan en UI y los callbacks solo llegan al cambiar. Revisar consultas periódicas, errores independientes de registro, reconexión y evitar callbacks antiguos.
- AIDL de com/tw/carinfoservice debe permanecer compatible con APK original; no cambiar transacciones ni inventar protocolos.
- DVR USB identificado 1b3f:8301, interfaz almacenamiento 8/6/80, no UVC confirmado. APK en workspace original; docs/CAMERA-INVESTIGATION.md documenta JNI libxufscamera.so y servicios. Investigar interoperabilidad, no prometer imagen independiente ni eliminar grabadora todavía.
- Tlink5 5.5.7 está protegido por cargador nativo; no hay contrato de llamadas/música privado confirmado. Pueden usarse notificaciones/sesiones reales, con límites documentados.
- La consulta adb de esta sesión falló al iniciar por carpeta .android no escribible; no hay conexión física confirmada.

## Herramientas y validación

PowerShell desde repo: JAVA_HOME=../work/build-tools/jdk/jdk-17.0.21+9; ANDROID_HOME=../work/build-tools/android-sdk; Gradle ../work/build-tools/gradle-8.7/bin.
Usar `gradle --offline --no-watch-fs --no-daemon --max-workers=2 testDebugUnitTest lintDebug assembleEmbedding` con acceso a caché autorizado. --no-watch-fs evita pausa larga enumerando discos Windows. GitHub compila también otras variantes.
APK/inspección originales: C:/Users/hbernal/Documents/Codex/2026-11-12/new-chat/work/camera/inspection y embedding/inspection. Nuevos APK Topway en ../apks_topway.
Desensamblador disponible: jars smali-baksmali/smali-dexlib2/smali-util, guava, failureaccess y jcommander en android-cli/cmdline-tools/lib/external; main com.android.tools.smali.baksmali.Main. No distribuir código/binarios OEM.

## Estado de trabajo al 13 noviembre 2026 (continuación)

- [x] Software: sesiones/VirtualDisplay/SurfaceTexture por paquete conservadas al navegar, lifecycle del store en la raíz. Liberación al cerrar el launcher/error, no en cada pestaña. Escalado táctil del buffer retenido. Prueba física pendiente.
- [x] Software: CarPlay sin márgenes ni barras; bordes de 18 dp revelan pestañas/controles durante 4 s. Cambio de volumen TW muestra volumen/brillo. Pruebas UI de gestos, timeout y no redimensionamiento.
- [x] Software: CAN/audio/ubicación START/STOP; getters cada 2 s en worker compartido. Consultar también getOriginalCarVoltage como respaldo. Conexión/callback/death recipient por generación, timeout/reintento y rechazo de respuestas viejas. Cuatro pruebas de conexión con servicio simulado, además de conversiones previas.
- [x] Software: retirados texto de confirmación/toast y explicación grande del cajón.
- [x] Software: open(AppEntry)/openPackage redirigen Tlink al panel en embedding. No bloqueo global de Tlink ni deshabilitación de su actividad.
- [x] Hallazgo NUEVO: receptor com.tw.service.u.onReceive del TW original recibe acción com.zjinnova.zlink, extra status, PHONE_CALL_ON/OFF y DISCONNECT. Se implementó receptor de solo navegación + respaldo de notificaciones CATEGORY_CALL de Tlink. No inferir llamadas desde Siri/conexión/música. No afirmar que llega al timbrar hasta probarlo.
- [x] Investigación SystemUI y DVR actualizada en INTEGRACION-4.5.md. No hay supresión global OEM ni video/grabación independiente funcional todavía.
- [x] Validación local del código: 91 pruebas, 0 fallos/errores/omitidas; lint 0 errores / 72 advertencias / 1 información; assembleEmbedding correcto. El resultado de publicación/CI y SHA se registra en ../INSTALAR_DESPUES/LEEME-4.5.txt al concluir.

### Archivos y evidencia nuevos

- `ImmersiveControls.kt`, `ProjectionCalls.kt`, cambios en LauncherScreen/MainActivity/RealAppPanel/TopwayCarInfo/Platform/HardwareDiagnostics/AppDrawer.
- `TopwayConnectionTest.kt`, `ProjectionCallsTest.kt`, `ImmersiveControlsTest.kt`; actualización de DashboardUiTest.
- `docs/INTEGRACION-4.5.md`: contratos, límites, pruebas y referencias Android. README apunta a 4.5 conservando historia.
- `tools/Capturar-TS10.ps1`: captura de solo lectura ADB de versiones/propietarios de ventanas. Sintaxis PowerShell validada; no dispositivo conectado. No almacena números ni texto de notificaciones. No confundir esto con un diagnóstico físico ya recibido.
- Inspección local NO distribuible: `../work/systemui-inspection/`, 59 clases TW y clases de volumen de SystemUI original, extraídas del ZIP OEM conocido.
- `VolumeUI.start`: recursos enable_volume_ui / enable_safety_warning. No son Settings. No usar sysui_volume_down_silent/up_silent para ocultar popup (son políticas de volumen). EasyTouch está en SystemUI: com.my.maple_easytouch es un archivo SharedPreferences, NO evidencia de otro paquete. Clave assistivetouch; el mosaico SuspensionTile cambia la preferencia y muestra/retira el botón. Identificar dueño de la ventana real antes de modificarlo.
- DVR: XUsbMassStorageDevice abre InputStream de elen1, JNI de XUFSCameraService sigue siendo el límite del protocolo. Splash abre MainActivity y termina. Mantener original para no perder historial de un minuto. No se ha implementado sustituto.
- Logs locales: `../work/integrated-compile.log`, `integrated-validation.log` (primer fallo UI), `integrated-validation-final.log` (lint API restringida), `integrated-verified.log` (onKeyDown/up públicos; validación actual). No repetir fallos ya corregidos ni presentar logs intermedios como resultado final.

### Pendientes que necesitan el estéreo

1. ADB devices devuelve lista vacía: no se instaló nada, no se cambiaron ajustes del firmware. Obtener diagnóstico de 4.5 tras alternar pestañas; `inicios=1` y mismo display por paquete verifican que el launcher no relanza.
2. CAN debe probarse contra getters reales. Si siguen en -1/vacíos, revisar servicio/decodificador/configuración con evidencia; no dar por resuelto por pasar tests.
3. Probar si PHONE_CALL_ON ocurre al timbrar o al contestar; comprobar que el firmware no mueve Tlink fuera del display virtual.
4. Identificar propietario de ventanas de volumen y llamada/ringtone; captura -Event Volume o Call. No deshabilitar SystemUI completo, no silenciar llamadas/CarPlay para esconder el síntoma.
5. DVR: video independiente y segmentación de un minuto siguen sin implementación/validación. No borrar OEM hasta tener reemplazo real y verificar archivos.
6. APK requiere Shizuku y variante embedding. Firma CI suele cambiar por runner; verificar antes de actualizar. No desinstalar automáticamente aplicaciones del usuario.

### Entrega y publicación

Rama `work/topway-carinfo`, HEAD inicial c099093cc70f72764c8260dae2706c9a0b3fda3a. Identificar el commit actual con git log; no tocar main. Entrega prevista en `../INSTALAR_DESPUES/`: APK 4.5 con hash abreviado en el nombre y `LEEME-4.5.txt` con commit completo, URL y resultado de Actions, firma y SHA-256. El LEEME se completa tras recibir CI, sin modificar el código compilado. Si falta, terminar publicación/espera/descarga/verificación antes de entregar.


## 4.6 (13 nov 2026, Claude/Cowork) — sin commit todavía

Cambios en el código (sin probar en el estéreo; compilar con `tools/Compilar-4.6.ps1`):
- LauncherScreen: en embedding las 4 páginas quedan compuestas siempre (`beyondBoundsPageCount = 3`, sin `settledPage`) y el cajón se dibuja ENCIMA del pager. Maps/Tlink/DVR ya no pierden su TextureView al cambiar de pestaña o abrir el cajón, y arrancan los tres al iniciar el launcher.
- Tlink oculto del cajón, dock y recientes (`drawerApps`); abrirlo desde cualquier lado lleva a la pestaña CarPlay.
- EmbeddedAppService: (a) DVR: concede permiso USB y fija HD Car DVR como app predeterminada para 1b3f:8301 y 1908:2311 vía IUsbManager (shell). Hipótesis: el "pide abrir la app" es el diálogo de Android "¿Abrir HD Car DVR para controlar…?" que dispara `SystemReceiverActivity` (USB_DEVICE_ATTACHED). Esa actividad es translúcida, llama `XCDVR1Service.p()` y termina: como predeterminada arranca la grabación sin UI. (b) `am display move-stack` regresa al panel cualquier tarea del paquete que quede en display 0 (hand-off Splash→MainActivityEx singleInstance del DVR, pantallas de llamada de Tlink). (c) espera 4.5 s para el Splash del DVR. (d) `status()` se auto-repara (mueve o relanza) y RealAppController lo consulta cada 2.5 s.
- TopwayAudio abre también el canal MCU 0x204: `arg1 == 1` = noche/luces (igual que TWService clase x). Respaldo de `can.lightsOn` cuando CarInfoService no reporta ILL. Diagnóstico muestra "TW luces (MCU 0x204)".
- Pruebas nuevas: TopwayAudioTest (canal 0x204), EmbeddingPolicyTest (move-stack/USB/settle), AppDrawerTest (`drawerApps`).

Popup de volumen (hallazgo confirmado en TW.apk 20210731):
- NO es SystemUI: es `com.tw.service.fa`, layout `volume_adjust` (0x7f080014), ventana TYPE_SYSTEM_ALERT de 2.5 s. `x.handleMessage` (canal 515) lo muestra siempre que el MCU no marque el bit alto de arg2; no hay Setting que lo apague. com.tw.service es `android.uid.system` → appops no aplica.
- Solución opcional: `tools/overlay-tw-volume/` (RRO sin código) reemplaza `volume_adjust` por una raíz GONE de 0 dp conservando IDs 0x7f06005a/5e/64 (stable-ids; el build aborta si no coinciden). Instalar con `instalar_overlay_tw.sh` (Termux + su): intenta overlay en /data, si Android lo rechaza crea módulo Magisk en /vendor/overlay con auto-reversión si TWService no queda estable. Revertir: `quitar_overlay_tw.sh`.
- El botón flotante EasyTouch de SystemUI se apaga con su mosaico nativo (SuspensionTile), sin código.

Pendiente / no verificado:
1. Compilar y correr pruebas (no hubo SDK Android disponible para Claude). 2. Confirmar en el estéreo que shell tiene MANAGE_USB e INTERNAL_SYSTEM_WINDOW (si no, el diagnóstico muestra "USB: SecurityException" o no mueve tareas; con Shizuku por root sí). 3. Popup/ringtone de llamada: dueño sin identificar (Tlink5 viene empaquetado/cifrado; dex de 70 KB). Capturar con `tools/Capturar-TS10.ps1 -Event Call`. Si es el marcador Bluetooth de TW, la causa es HFP del iPhone conectado al estéreo además de CarPlay. 4. CAN: velocidad/marcha siguen dependiendo de CarInfoService; `revserse_status` (Settings.System) solo se escribe en 1, no se usó como fuente. 5. DVR sin app (protocolo USB propietario por JNI) sigue fuera de alcance; la app se conserva.

Hallazgo llamadas (13 nov, tarde): TWService (clase x, X.txt ~3680) abre `com.tw.bt/com.tw.bt.ATBluetoothActivity` en la pantalla principal ante eventos de teléfono del módulo Bluetooth propio del estéreo (ATBluetoothService, comandos AT, no la pila Bluetooth de Android). Usuario: el iPhone se empareja por BT para iniciar CarPlay inalámbrico y luego debería quedar solo por WiFi, pero "a veces" queda conectado por ambos. Hipótesis principal: en esos casos la llamada entra también por el BT del estéreo → popup + ringtone de com.tw.bt. Arreglo de raíz: desactivar conexión automática del BT del estéreo. Confirmar con Capturar-TS10.ps1 -Event Call (debe aparecer com.tw.bt).

Resultado en el estéreo (13 nov 17:08): `pm install` del overlay en /data FALLA en Android 10 ("Overlay ... and target com.tw.service signed with different certificates, and the overlay lacks <overlay android:targetName>"). Como módulo Magisk `/data/adb/modules/glasscar_ts10` (system/vendor/overlay) SÍ carga: `cmd overlay list com.tw.service` → `[x] com.glasscar.overlay.twvolume`, sin archivo `disable` (TWService estable). Instalado con `INSTALAR_DESPUES/glasscar-termux-4.6.txt` (incluye vigilante de llamadas com.tw.bt).

Diagnóstico overlay (13 nov 17:20): idmap correcto (layout/volume_adjust y los 3 id mapeados 1:1, target /system/priv-app/com.tw.service_a5a4) y `overlay paths` presente, pero la ventana seguía midiendo 1082x237 (diseño original). Causa: `am force-stop` NO mata procesos persistentes en Android 10, así que TWService nunca se reinició con el overlay. Se reemplazó por `kill -9 $(pidof com.tw.service)` en instalador, service.sh y scripts.

CONFIRMADO por el usuario (13 nov): tras `kill -9` de com.tw.service el popup de volumen de TW ya no aparece. Overlay + módulo Magisk `glasscar_ts10` funcionando. Pendiente: verificar vigilante de llamadas (llamadas.log) en una llamada real de CarPlay y las pruebas del launcher 4.6 (pestañas sin reinicio, USB DVR, luces MCU).


## Continuación Codex: revisión 4.6 y entrega (13 noviembre)

Prioridad de lectura: `docs/VERIFICACION-4.6.md` refleja la revisión posterior al prompt de Claude. El usuario enviará los videos en OTRO chat de ChatGPT Work desde su celular; no esperar ni inventar una revisión audiovisual aquí. Dejar este contexto publicado en GitHub.

- SDK/JDK/Gradle locales SÍ están disponibles. La 4.6 inicial compiló. Se hicieron correcciones puntuales a watchdog/recuperación/diagnóstico USB, reflexión de IPackageManager y lecturas inválidas de luces; detalle y tests en VERIFICACION-4.6.md.
- ERROR DE ENTREGA 4.5: el archivo llamado GlassCar-Ultra-4.5-0069aa5-embedding.apk era realmente `.cameradiag`, code9; SHA146c46b1... El LEEME anterior incluso apuntaba a cameradiag/app-cameradiag.apk y contenía un fallo de JAVA_HOME en la verificación de firma. No asumir que el usuario probó embedding. La variante correcta del ZIP es embedding/app-embedding.apk; ambas variantes CI4.5 usan certificado b0a3b465...; el APK local usa 6a2cb576... Comparación completa en VERIFICACION-4.6.md.
- Para interpretar diagnóstico 4.6: `inicios=1` sólo cuenta controladores/displays; el watchdog puede relanzar. Revisar también `iniciosApp` y `movimientos`. La concesión USB y la elección predeterminada se informan por separado. No afirmar permiso/default/video por el nombre de un archivo.
- Overlay TW confirmado: se conservó intacto. Compilar-4.6 ya NO lo reconstruye por defecto. Scripts standalone usan glasscar_twvolume; el módulo instalado se llama glasscar_ts10. No confundirlos ni ejecutar su desinstalador como parte de actualizar el launcher.
- No se instaló ningún APK en el estéreo ni se ejecutaron comandos de Termux/root. La supresión de llamadas duplicadas y la continuidad de DVR/CAN requieren prueba física.
- Entrega final: completar abajo commit, run CI, resultados y certificados después de verificar. Usar paquetes/local/CI explícitos en INSTALAR_DESPUES y mantener fuentes publicadas en work/topway-carinfo, sin tocar main.


Validación local final de esta revisión: `tools/Compilar-4.6.ps1` → BUILD SUCCESSFUL; 95 pruebas, 0 fallos/errores/omitidas; lint 0 errores, 80 advertencias, 1 información. APK local code10 firmado y verificado; SHA-256 `e38aafb52172830fb1aee830928bb211eb002d67db8fbf62d63f941dbc30a17f`. Ver el resultado de CI a continuación.


## Resultado final publicado de 4.6

- Código compilado: `ee070248c1a902b9e9f3abf1b5185030751e524c` en `work/topway-carinfo`.
- [GitHub Actions 43531075891: SUCCESS](https://github.com/Hector780/glasscar-ultra/actions/runs/43531075891). Reporte descargado: 95 tests, 0 fallos/errores/omitidas; lint 0 errores, 81 advertencias, 1 información. Local: mismas 95 pruebas y 0 errores de lint (80 advertencias, 1 información).
- Se descargó el artefacto ID14086614667, SHA-256 ZIP `a33365f2b8fa9facfe4a4adc49017a2c7586e642f10343d731336fdd14b3f641`, y se extrajo **exactamente embedding/app-embedding.apk**.
- Ambos APK verificados por aapt/apksigner: `.embedtest`, code10, `4.6-ts10-integrated-real-app-prototype`.

| Archivo en INSTALAR_DESPUES | SHA-256 APK | SHA-256 certificado |
|---|---|---|
| GlassCar-Ultra-4.6-local-embedding.apk (recomendado para continuidad de firma local) | `e38aafb52172830fb1aee830928bb211eb002d67db8fbf62d63f941dbc30a17f` | `6a2cb5763f9f5de3ec92f9487a3d02b0ed1aa693d4b0f072cca138a57b531a7b` |
| GlassCar-4.6-ee07024-CI.apk | `99a95fc258733c10ba4d41a40daf06a493d630948cd5906254421b34658e3cc0` | `abfb317816efb622035806e5cb890c709a6162c230140f3d719f29a924580263` |

**Las dos firmas difieren de CI4.5 (`b0a3b465...`).** Si `.embedtest` instalado tiene aquella firma, ni local4.6 ni CI4.6 actualizan encima. Si lo instalado es `.cameradiag`, son paquetes distintos y pueden coexistir. No se comprobó el paquete instalado ni se desinstaló nada. El LEEME explica conservar ajustes y la clave local privada; no se publicaron claves ni credenciales.

Se corrigieron los nombres de APK previos en INSTALAR_DESPUES conservando los archivos: el falso «4.5 embedding» ahora se llama `GlassCar-4.5-cameradiag-ARCHIVO-ANTES-MAL-NOMBRADO.apk`; el verdadero4.5 queda como referencia. La 4.6 previa sin commit quedó marcada `PREVIA-SIN-COMMIT`. La entrega vigente está en `LEEME-4.6.txt`.

El siguiente commit sólo documenta esta entrega y usa `[skip ci]`: no modifica el código del APK validado. Continuar con los videos en el otro chat; no rehacer el overlay de volumen ni presentar la continuidad DVR/CAN/llamadas como probadas físicamente.


## 4.7 etapa 1: Cámara y reversa real (13 nov 2026, Claude/Cowork) — SIN COMPILAR, SIN COMMIT

Especificación: `docs/ESPEC-4.7-VIDEO.md`. Orden y límites: `docs/PROMPT-FABLE-4.7.md`. Detalle de archivos, riesgos de compilación, pruebas físicas y mensaje de commit: **`docs/PENDIENTE-PUSH.md`** (leerlo antes de compilar). Claude no hace commit/push; Codex compila con `tools/Compilar-4.7.ps1` (code 11, `4.7-ts10-integrated`).

Hecho en código (sin probar en el estéreo):
- Cámara (embedding): se quitó la fila «Cámara trasera · original»; el DVR ocupa todo el panel y el selector Frontal/Trasera es una cápsula sobrepuesta (`EmbeddedCameraPanel`, `CameraSourceCapsule`, `RearCameraState` en EmbeddedPanels.kt).
- Señal real de reversa: canal TW `0x9f1c` (arg1 1/0), el mismo mensaje con el que TWService abre ReverseActivity. Se escucha junto con `0x20b` en la conexión TWUtil que ya tenía TopwayAudio (con reintento a la lista 4.6 si TW rechaza la ampliada), más la difusión `com.unisound.intent.action.DO_MUTE/DO_UNMUTE` con extra `reverse`. Solo lectura: no existe consulta conocida, no se escribe nada. Estado en `ReverseSignal` (ReverseSignal.kt).
- Navegación: `ReverseNavigator` pasa a Cámara + Trasera una vez por flanco de subida y regresa a la página anterior al salir si el conductor no tocó nada.
- Diagnóstico: bloque «Reversa» y línea «TW reversa (escucha 0x9f1c/0x20b)».

Límites que NO se resolvieron (no presentarlos como hechos):
- La trasera no se ve dentro del panel. Fuera de reversa la propia app OEM se ve negra; su ruta usa `sys.camera.ch`, `Camera.open(0)` y `TW 0x304`, que GlassCar no puede o no debe usar sin prueba. `com.tw.reverse` sigue fuera de `EmbeddingPolicy` a propósito (es la pantalla de seguridad que TWService abre en reversa real).
- En reversa real TWService sigue mostrando su cámara a pantalla completa encima de GlassCar.
- Nada verificado físicamente: aceptación de los canales por una app que no es de sistema, salto de pestaña, tamaño del video, grabación DVR.

Siguiente paso para Codex: compilar, corregir según la lista de riesgos de PENDIENTE-PUSH.md, correr las 16 pruebas nuevas, commit/push y APK. Siguiente etapa para Claude: etapa 2 (brillo físico, contrato TW 516) sobre la misma conexión TWUtil.


## 4.7 etapa 2: brillo físico TW 516 (13 nov 2026, Claude/Cowork) — SIN COMPILAR, SIN COMMIT

Detalle y riesgos en `docs/PENDIENTE-PUSH.md`, sección «Etapa 2».

- Contrato usado: `TWUtil.write(516, modo, valor, null)` con valor 1..10 (el deslizador nativo hace `setMax(10)`), lectura 516 (`arg1` noche, `arg2` = día | noche << 8) y blmode por el canal 258 (0x102, `arg2`). Todo por la conexión TWUtil única de `TopwayAudio` (ahora abre 0x203, 0x204, 0x102, 0x9f1c, 0x20b con reintento escalonado hasta la lista 4.6).
- **La tabla de blmode del prompt estaba invertida.** Según el desensamblado: 0 automático (sigue nightMode), 1 día (modo 0), 2 noche (modo 1). Se implementó así en `TopwayBrightnessProtocol.activeNight`; confirmar físicamente si el estéreo está en día fijo o noche fijo.
- Estado único: `TopwayAudio.controller.state.brightness` + `brightnessModel()` (TopwayBrightness.kt). La barra inferior ya lo usa; el centro de control de la etapa 5 debe usar ese mismo modelo y `LauncherActions.setBrightness`, sin estado propio.
- `MainActivity.setBrightness` ya no escribe `Settings.System` cuando el TS10 está conectado.
- Pendiente físico: que el estéreo acepte 516 y responda 258 a una app normal, y que el panel cambie de verdad.

Siguiente etapa para Claude: etapa 3 (geometría y gestos). Tener en cuenta la observación de PENDIENTE-PUSH.md sobre el display de Tlink creado con tamaño no inmersivo.
