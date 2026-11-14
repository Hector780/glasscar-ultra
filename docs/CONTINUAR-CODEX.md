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


## Cierre publicado de 4.7, etapas 1 y 2 — Codex, 13 noviembre 2026

Las notas siguientes conservan el traspaso original de Claude, incluidas sus indicaciones históricas de «sin compilar». La validación y publicación concluyeron después: código `54acff4e50009e1abf453250fc0754be255865d0`, 120 pruebas aprobadas, lint sin errores y CI automático/manual SUCCESS. No fueron necesarias correcciones de código o pruebas. Los resultados completos y la comparación de firmas están al final de este registro.

### Archivo íntegro de PENDIENTE-PUSH.md al cerrar
# Pendiente de push — GlassCar 4.7

Claude (Cowork) edita archivos en esta carpeta y **no** hace commit, push ni operaciones de GitHub. Codex revisa este archivo, compila con `tools/Compilar-4.7.ps1`, corrige lo que no compile, hace commit/push en `work/topway-carinfo` y deja el APK verificado.

Base de trabajo: `92e9ddc` (4.6 documentada), árbol limpio salvo los tres docs sin seguimiento que ya estaban (`ESPEC-4.7-VIDEO.md`, `PROMPT-CODEX-PUSH.md`, `PROMPT-FABLE-4.7.md`).

**Nada de esto se compiló ni se ejecutó.** El entorno de Claude no tiene SDK de Android ni acceso a Maven. Solo se hizo una revisión de sintaxis con tree-sitter (mismo resultado que la base en los archivos ya existentes). No se tocó el estéreo ni se dieron comandos de Termux en esta etapa.

---

## Etapa 1 — Cámara (sección 7 de la espec) · 13 nov 2026

### Archivos cambiados y por qué

| Archivo | Cambio |
|---|---|
| `app/build.gradle.kts` | `versionCode` 11, `versionName` `4.7-ts10-integrated`. |
| `tools/Compilar-4.7.ps1` (nuevo) | Copia del 4.6 con nombres 4.7. Ahora valida applicationId, code 11 **y** versionName `4.7-ts10-integrated-real-app-prototype`. `Compilar-4.6.ps1` queda intacto. |
| `ReverseSignal.kt` (nuevo) | Señal real de reversa (solo escucha), reductor puro con flancos y fuentes, diagnóstico, `CameraSource` y `ReverseNavigator` (navegación una vez por transición). |
| `TopwayAudio.kt` | La **misma** conexión TWUtil abre además 0x9f1c y 0x20b (solo escucha). Si TW rechaza la lista ampliada, reintenta con la lista de 4.6 (0x203 + 0x204) en una instancia nueva para no perder el volumen confirmado. Nuevo parámetro `onReverse` del controlador y campo `reverseListening` en el estado/diagnóstico. No se agregó ninguna escritura. |
| `MainActivity.kt` | Receptor de `com.unisound.intent.action.DO_MUTE/DO_UNMUTE` (extra entero `reverse`) registrado de `onCreate` a `onDestroy`, para ver la salida de reversa aunque la cámara OEM tape al launcher y TopwayAudio esté detenido. |
| `LauncherScreen.kt` | Estado `cameraSource` y efecto que, en cada flanco, llama a `ReverseNavigator`: entra a la pestaña Cámara con trasera una vez; al salir regresa a la página/cámara anteriores si el conductor no tocó nada. Pasa el selector a `CameraPanel`. |
| `EmbeddedPanels.kt` | `CameraPanel` (embedding) ya no tiene la fila «Cámara trasera · original»: el DVR ocupa todo el panel. Nuevos `EmbeddedCameraPanel`, `CameraSourceCapsule` (cápsula sobrepuesta Frontal/Trasera, 48 dp por opción) y `RearCameraState` (estado dentro del panel). `CameraPanel` pasó a `internal` porque recibe `CameraSource`. La rama sin embedding no cambió. |
| `HardwareDiagnostics.kt` | Agrega el bloque «Reversa» (estado, entradas/salidas, valor crudo, antigüedad y número de eventos por fuente). |
| `docs/CAMERA-INVESTIGATION.md`, `docs/CONTINUAR-CODEX.md` | Hallazgos de reversa/trasera y continuidad. |

No se tocaron: overlay RRO de volumen, módulo Magisk, `EmbeddedAppService`, `EmbeddingPolicy` (com.tw.reverse sigue **fuera** de la lista de embedding), `RealAppPanel`, AIDL, manifest.

### Hallazgos (evidencia estática; falta prueba física)

Fuentes: `apks_topway/com.tw.service.apk` (20210731, desensamblado con androguard en la nube, sin ejecutar) y `work/reverse-inspection/` (com.tw.reverse).

1. **Señal real de reversa = canal TW `0x9f1c`, `arg1` 1 al entrar / 0 al salir.** `com.tw.service.x.handleMessage` (caso `9f1c`, offset `0x0aca`) guarda el valor en `TWService.ta`, y de ahí sale todo lo demás: programa el mensaje `0xfffe` que publica el runnable `com.tw.service.g`, y ese runnable abre `com.tw.reverse.ReverseActivity` y escribe `revserse_status = 1`. Nadie lo regresa a 0 (ni TWService ni com.tw.reverse): por eso ese ajuste se queda en 1.
2. `ReverseActivity$1.handleMessage` trata `0x20b` y `0x9f1c` igual (`arg1 == 1` activa, `arg1 == 0` cierra la pantalla si no es vista manual). TWService abre ambos canales pero solo actúa con `0x9f1c`.
3. En cada cambio TWService difunde `com.unisound.intent.action.DO_UNMUTE` o `DO_MUTE` con extras enteros `call` y `reverse` (`x` offsets `0x0cfa–0x0d76`, también en `0x22c2` y en la clase `I`; siempre `reverse = ta`). Es una segunda fuente que llega aunque el launcher esté en segundo plano.
4. No encontré ninguna **consulta** del estado de reversa (ningún `write(0x9f1c, 0xff)` ni `write(0x20b, 0xff)` en TWService ni en com.tw.reverse). Por eso GlassCar solo escucha: si arranca con la reversa ya puesta, no lo sabrá hasta el siguiente cambio (salvo que el framework reenvíe el último valor al suscribirse, cosa que no está comprobada).
5. **Trasera dentro del panel: no hay camino verificado sin tocar el sistema.** La pantalla OEM hace `SystemProperties.set("sys.camera.ch","0")`, `android.hardware.Camera.open(0)` en TS10 (API antigua), `TW write(0x304, 1)` al mostrarse y `write(0x304, 0)` al ocultarse, y detecta señal con `write(0x9f1d)`. GlassCar no puede fijar `sys.camera.ch` (propiedad de sistema) y `0x304` es un comando cuyo efecto no conocemos, así que no se envía. Además, en el video la propia app OEM se ve negra fuera de reversa aun con esos privilegios: lo más probable es que la cámara solo tenga alimentación con la luz de reversa (falta confirmarlo en el cableado).
6. **No se incrustó `com.tw.reverse` en un display virtual.** Es `singleInstance` y es la misma actividad que TWService abre en reversa real: si viviera dentro del panel, la reversa real la traería al panel en vez de a pantalla completa, y con GlassCar oculto el conductor no vería nada. Es un riesgo de seguridad sin prueba física, así que la política 4.6 se conserva.

### Comportamiento que queda

- **Cámara:** el video del DVR gana la altura de la fila eliminada (≈44 dp). La cápsula Frontal/Trasera flota en la esquina superior derecha del video.
- **Trasera manual (fuera de reversa):** el panel muestra un estado breve («Sin imagen fuera de reversa…») sobre el DVR, que sigue compuesto debajo (misma sesión, misma superficie; no se relanza ni se detiene la grabación). No se abre la app OEM por fuera.
- **Reversa real:** al flanco de subida GlassCar pasa a Cámara + Trasera una sola vez (cierra cajón/hoja/mapa ampliado). TWService sigue abriendo su cámara original a pantalla completa encima; no se bloquea. Al flanco de bajada, si el conductor no cambió de página ni de cámara, GlassCar regresa a la página y cámara anteriores. Lecturas repetidas, valores distintos de 0/1 y flancos ocurridos antes de componer la pantalla no navegan.
- Decisión de UX propia (no viene literal del video): restaurar la página anterior al salir. Se quita cambiando `ReverseNavigator.advance` (rama de salida) si no gusta.

### Pruebas agregadas (16; total esperado 95 + 16 = 111; con la etapa 2, 120)

- `ReverseSignalTest` (10): decodificación 0/1, mapeo de canal con y sin extensión de signo, un flanco por transición, lecturas inválidas o ausentes nunca se vuelven «no reversa», 0x20b solo decide hasta que hable una fuente principal, la difusión cierra la reversa con TW en pausa, texto de diagnóstico, y `ReverseNavigator` (entrar una vez, restaurar solo si no se tocó nada, ya estar en Cámara, flancos con launcher oculto o previos al arranque).
- `ReverseCameraUiTest` (3, Robolectric + `LauncherRoot`): reversa simulada abre Cámara y regresa a Tablero; lecturas repetidas no jalan al conductor de vuelta y se respeta su navegación manual; lecturas inválidas no navegan.
- `EmbeddedPanelUiTest` (+2): la cápsula flota sobre el video a tamaño completo, sin la fila anterior, con objetivo táctil ≥ 48 dp; elegir Trasera muestra el estado y conserva el video compuesto con los mismos límites; el estado nombra la reversa real y la cápsula se oculta sin callback.
- `TopwayAudioTest` (+1): los canales de reversa se reenvían sin producir lecturas de volumen, escrituras ni consultas extra, y se ignoran tras `stop()`.

Las pruebas simuladas validan transformación y navegación, **no** que el vehículo envíe la señal.

### Riesgos y puntos sin verificar

**Riesgo de que no compile (revisar primero):**

1. `EmbeddedPanels.kt`, `CameraSourceCapsule`: `selected = active` dentro de `semantics { }` (usa `import androidx.compose.ui.semantics.selected`). Si choca, quitar esa línea y el import, y cambiar los dos `assertIsSelected()` de `EmbeddedPanelUiTest`.
2. `EmbeddedPanels.kt`, `PanelNote`: `BasicText(text, Modifier…, style = TextStyle(…, textAlign = TextAlign.Center))`.
3. `EmbeddedPanels.kt`: `CameraPanel` ahora es `internal` (expone `CameraSource`, que es `internal`). Si algún otro módulo/variante lo llamaba como público, fallará ahí.
4. `LauncherScreen.kt` ~línea 243: `rememberSaveable { mutableStateOf(CameraSource.FRONT) }` (enum guardable) y la llamada `CameraPanel(…, source = cameraSource, onSource = { cameraSource = it })`.
5. `TopwayAudio.kt`: `private var instance: Any = type.getConstructor().newInstance()` y el `lazy` con argumento con nombre `onReverse`.
6. `ReverseSignal.kt`, `reduce`: smart cast de `value` (Boolean?) después de `if (value == null || …) return`.
7. `MainActivity.kt`: `runCatching { ContextCompat.registerReceiver(…); reverseReceiverRegistered = true }`.

**Riesgo de que fallen pruebas:**

- `ReverseCameraUiTest` es la primera prueba que compone la página Cámara **sin embedding** dentro de `LauncherRoot` en Robolectric (permiso CAMERA, `CameraManager`). Si falla por esa página y no por la navegación, cambiar la aserción de «Cámara frontal» por otra propia de la página 2.
- `EmbeddedPanelUiTest`: las medidas suponen densidad mdpi del calificador (48 dp = 48 px).

**Lint:** posibles advertencias nuevas (orden del parámetro `modifier` en composables); no espero errores.

**Requiere prueba física (nada de esto está verificado):**

1. **Volumen intacto.** Lo más importante: en Diagnóstico debe seguir «Volumen nativo recibido» y aparecer `TW reversa (escucha 0x9f1c/0x20b): canales aceptados`. Si dice «canales rechazados», el reintento conservó el volumen y la reversa queda solo por la difusión.
2. **Reversa real**, estacionado y con freno: poner R y quitarla. Esperado: salto a Cámara (puede verse solo un instante antes de la cámara OEM), regreso a la pestaña anterior al quitar R. Luego copiar el bloque «Reversa» del diagnóstico: debe mostrar `entradas: 1 · salidas: 1` y qué fuentes reportaron (`TW 0x9f1c`, `difusión TWService`, `TW 0x20b`) con su valor crudo.
3. Que 0x9f1c llegue a una app normal (no de sistema). Si `TW 0x9f1c` queda «sin eventos» pero la difusión sí llega, la función sigue operando por la difusión; anotarlo.
4. **Tamaño y cápsula:** que el video se vea más grande y que la cápsula no tape controles propios de HD Car DVR. Si estorba, mover `Alignment.TopEnd` en `EmbeddedCameraPanel`.
5. Grabación DVR en segmentos de un minuto tras alternar Frontal/Trasera y tras una reversa.
6. Alimentación de la cámara trasera: confirmar si solo recibe corriente con la luz de reversa. Si es así, verla manualmente exige un cambio eléctrico, no de software.

### Bloqueado

- Ver la trasera dentro del panel fuera de reversa (puntos 5 y 6 de hallazgos). Siguiente diagnóstico concreto, de solo lectura: con R puesta, anotar en el diagnóstico las «Cámaras Android» (IDs Camera2) y confirmar el cableado de alimentación. Cualquier intento de abrir la entrada analógica desde GlassCar necesita antes un procedimiento de reversión y prueba física, porque compite con la cámara OEM de reversa.

### Observación para la etapa 3 (no se cambió)

En 4.6 las cuatro páginas se componen al arrancar con el tamaño **no inmersivo** del pager. `RealAppController` crea el display virtual con ese tamaño y después solo escala el buffer (`onSurfaceTextureSizeChanged`). Al entrar a CarPlay (inmersivo) el `TextureView` crece a pantalla completa, así que Tlink se ve **escalado** desde un display más chico y con otra proporción (en un lienzo de 1024×600 dp serían ≈896×450 dp estirados a 1024×600). Esto sale de leer el código, no de una medición en el estéreo. «CarPlay a pantalla completa» necesita que el display de Tlink nazca con el tamaño completo.

### Mensaje de commit sugerido

```
4.7 stage 1: camera panel selector, real reverse signal and tab switch

- Remove the "Cámara trasera · original" row; DVR fills the panel with an
  overlay Frontal/Trasera capsule and an in-panel rear state.
- Listen (never write) to TW 0x9f1c/0x20b on the existing TWUtil connection,
  with fallback to the 4.6 channel list, plus the TWService reverse broadcast.
- Switch to the Camera tab once per reverse transition and restore the
  previous page on exit when the driver touched nothing.
- Add reverse block to diagnostics; bump to versionCode 11 / 4.7-ts10-integrated
  and add tools/Compilar-4.7.ps1.
- Tests: ReverseSignalTest, ReverseCameraUiTest, panel and TW audio cases.
- Not physically verified: reverse channels on a non-system app, DVR size.
```

---

## Etapa 2 — Brillo físico (sección 5 de la espec) · 13 nov 2026

Igual que la etapa 1: **sin compilar, sin ejecutar, sin tocar el estéreo.** Puede ir en el mismo commit que la etapa 1 o en uno aparte (abajo hay mensaje propio).

### ⚠ Diferencia con el prompt: la tabla de blmode está al revés

`PROMPT-FABLE-4.7.md` dice «si `blmode==1` es 1; si `blmode==2` es 0». La evidencia dice lo contrario y coincide en cuatro lugares, así que implementé la evidencia:

| blmode (canal 258) | Significado | `modo` que se escribe en `write(516, modo, valor)` |
|---|---|---|
| 0 | automático | `nightMode` (1 si las luces están encendidas) |
| 1 | día fijo | **0** (brillo de día) |
| 2 | noche fijo | **1** (brillo de noche) |

- `QSPanelBrightness$1.onProgressChanged`: con blmode 1 salta a `move v0, v1` (0); con blmode 2 conserva 1.
- `QSPanelBrightness$3.getData` (lectura 516): blmode 1 muestra `arg2 and 255` (día); blmode 2 muestra `(arg2 shr 8) and 255` (noche).
- `com.tw.service.x`, caso 0x204 (offset 0x257c): `Ya == 1` usa el nivel de día; `Ya == 2` el de noche.
- `TWService.Qa` (tecla de brillo, offsets 0x0008–0x001e): `Ya == 1` → modo 0; `Ya == 2` → modo 1.

Con blmode 0 (automático) las dos lecturas dan lo mismo, así que solo importa si el estéreo está en día fijo o noche fijo. Si en la prueba física resulta al revés, se corrige en una sola función: `TopwayBrightnessProtocol.activeNight`.

### Contrato confirmado antes de escribir

- **Máximo real del deslizador nativo: 10.** `QSPanelBrightness.initBrightValue` hace `SeekBar.setMax(10)`. La tecla de brillo del estéreo (`TWService.Qa`) recorre 10 → 5 → 1 y nunca usa 0. GlassCar escribe solo 1..10.
- Escritura: SystemUI llama `TWSystemUI.write(516, modo, valor)`, que es `TWUtil.write(516, modo, valor, null)` (la firma de 4 argumentos). Es el mismo método reflejado que GlassCar ya usa para el volumen; no se agregó ninguna búsqueda reflexiva nueva.
- Lectura 516: `arg1 == 1` noche; `arg2 and 255` día; `(arg2 shr 8) and 255` noche. Consulta `write(516, 255)`: GlassCar ya la hacía cada 2 s desde 4.6 para las luces.
- Lectura 258 (0x102): `arg2` = blmode. Consulta `write(258, 255)`, la misma de `QSPanelBrightness.initView`.
- El TWUtil de SystemUI abre juntos 0x204, 0x102, 0x203 y 0x9f1c (lista en `TWSystemUI`), así que esos canales conviven en una sola conexión.

### Archivos cambiados y por qué

| Archivo | Cambio |
|---|---|
| `TopwayBrightness.kt` (nuevo) | Protocolo puro (decodificar 516, validar blmode, qué nivel gobierna, objetivo 1..10), `TopwayBrightnessState` y `BrightnessModel`/`brightnessModel()`: el **único** modelo de brillo para la barra inferior y el futuro centro de control. |
| `TopwayAudio.kt` | El estado suma `connected` y `brightness`. El controlador decodifica el brillo en el canal 516 que ya leía, escucha 258, consulta blmode al arrancar y cada quinto sondeo (10 s), y agrega `setBrightnessFraction()` con confirmación por lectura de retorno (3 s). El transporte abre también 0x102 **en la misma conexión**; el reintento ahora es de tres pasos: lista completa → sin reversa → lista 4.6. Diagnóstico con cuatro líneas «TW brillo». |
| `MainActivity.kt` | `setBrightness`: con el TS10 conectado usa el adaptador TW y ya no escribe `Settings.System`. Sin TS10 se conserva la ruta Android anterior. |
| `LauncherScreen.kt` | `QuickControlBar` lee el modelo compartido: muestra `n/10`, salta en los 10 pasos reales y se resincroniza con cada lectura. Si el TS10 está conectado pero no hay lectura vigente o falta blmode, en vez del deslizador muestra un botón que abre el panel nativo. |

No se usa `window.screenBrightness`, capas oscuras ni alpha. No se usó root ni Shizuku. No se cambió `HeadUnitControls.kt`.

### Comportamiento que queda

- Mover el deslizador escribe como el panel nativo: un comando por nivel distinto, nunca 0, solo con lectura de menos de 5 s y blmode conocido.
- El control muestra el nivel pedido mientras espera; si el estéreo no lo confirma en 3 s, regresa a la lectura real y el diagnóstico dice «Brillo sin confirmar por el estéreo». No se bloquean intentos posteriores (a diferencia del volumen).
- Si el brillo cambia desde el panel nativo o la tecla del estéreo, la barra lo refleja en el siguiente sondeo (≤ 2 s) o de inmediato si el MCU lo empuja.
- Nada se escribe al arrancar, al sondear ni al reconectar.

### Pruebas agregadas (9; total esperado 120)

`TopwayBrightnessTest`: decodificación y máximo 10; tabla de blmode y objetivo nunca 0; nada se escribe sin lectura vigente y blmode conocido; escritura del nivel de día con confirmación y sin comandos repetidos; nivel de noche cuando gobierna la noche (automático con luces, noche fija, día fijo); cambio sin confirmar o rechazado regresa a la lectura; resincronización con el panel nativo y bloqueo por lectura inválida o caduca; las luces 0x204 siguen funcionando y blmode se consulta al inicio y cada quinto sondeo; modelo único y texto de diagnóstico.

No hay prueba de Compose para la barra con brillo nativo: `QuickControlBar` es privada y lee el singleton `TopwayAudio`. La lógica que usa está cubierta por `brightnessModel`.

### Riesgos y puntos sin verificar

**Riesgo de que no compile:**

1. `TopwayAudio.kt`, `ReflectiveTopwayAudioTransport.open()`: bucle `for (attempt in 0..1)` con `return 0` dentro y reasignación de `instance`.
2. `TopwayAudio.kt`, `receiveBacklight`: argumento con nombre `confirmed = if (confirmed) written else before.confirmed` (el local `confirmed` es Boolean y el campo es Int?). Si confunde, renombrar el local.
3. `TopwayBrightness.kt`: propiedades con getter en una línea (`val level: Int? get() { … }`).
4. `LauncherScreen.kt`, `QuickControlBar`: `if (…) Box(…) { … } else GlassTube(…) { … }` y la asignación `brightness = if (…) … else …` partida en dos líneas.

**Riesgo de pruebas:** `lightsKeepWorkingAndTheModeIsAskedAtStartAndEveryFifthPoll` depende del reloj de Robolectric (sondeos a 0, 2, 4, 6, 8 y 10 s).

**Requiere prueba física (nada verificado):**

1. **Volumen intacto** (misma conexión, un canal más): «Volumen nativo recibido» en el diagnóstico.
2. Diagnóstico, bloque «TW brillo»: deben aparecer `día: N · noche: N`, `gobierna: día/noche` y `blmode (258): 0/1/2`. Si blmode queda «sin lectura», el estéreo no respondió la consulta 258 a GlassCar: la barra mostrará el botón del panel nativo y hay que copiar el diagnóstico.
3. Con luz ambiente estable, mover la barra a 2, 5 y 10: **cambio físico visible** y `confirmado` igual a `solicitado`. Repetir en Tablero, con una app externa abierta y tras suspender/despertar.
4. Mover el deslizador nativo (cortina) y ver que la barra de GlassCar lo sigue.
5. Encender las luces del auto (modo noche) y repetir: debe moverse el nivel de noche. Si el estéreo está en día fijo o noche fijo, confirmar la tabla de blmode de arriba.
6. Que una app normal pueda escribir 516. El volumen (0x203) ya se escribe desde GlassCar, pero el brillo no se ha probado. Si TW lo rechaza, el diagnóstico mostrará `retorno` negativo o «sin confirmar».

Recuperación si la pantalla quedara oscura: GlassCar nunca baja de 1; la tecla de brillo del estéreo y el deslizador de la cortina nativa siguen funcionando y devuelven el nivel.

### Bloqueado

Nada bloqueado en software. Todo depende de la prueba física del punto 3.

### Mensaje de commit sugerido

```
4.7 stage 2: physical TS10 brightness through TW 516 on the shared connection

- Decode day/night backlight levels from the 516 readings already polled and
  listen to 258 (blmode) on the same TWUtil connection (0x102 added to open()).
- setBrightnessFraction writes write(516, mode, 1..10, null) like the native
  panel, only on a user gesture with a fresh reading, and waits for readback.
- One BrightnessModel for the bottom bar and the future control center; the bar
  resyncs with native changes and offers the native panel when not ready.
- blmode table follows the disassembly (1 = day -> mode 0, 2 = night -> mode 1).
- Diagnostics: TW brillo lines. Tests: TopwayBrightnessTest (9).
- Not physically verified.
```

## Validación local Codex — 13 noviembre 2026

- Base revisada: `92e9ddc44edaa3fcd5a539592fe2bee9746f706e`, rama `work/topway-carinfo`.
- Correcciones al código o a las pruebas: **ninguna necesaria**. Se conservaron los cambios de Claude sin alterar su comportamiento.
- Entorno: Windows PowerShell interrumpió la captura de stderr; PowerShell 7 mostró que Gradle no podía cargar `native-platform.dll` dentro del aislamiento. Se ejecutó el mismo `tools/Compilar-4.7.ps1` con PowerShell 7 y acceso autorizado a la caché/SDK local. No se modificó el script ni se usó `-BuildOverlay`.
- Resultado: `testDebugUnitTest lintDebug assembleEmbedding`, **BUILD SUCCESSFUL**. XML de pruebas: **120 pruebas, 0 fallos, 0 errores, 0 omitidas**. XML de lint: **0 errores, 81 advertencias, 1 información**.
- APK comprobado con `aapt dump badging` y `apksigner verify --print-certs`: `com.glasscar.launcher.embedtest`, versionCode **11**, versionName **4.7-ts10-integrated-real-app-prototype**.
- Entrega local: `INSTALAR_DESPUES/GlassCar-Ultra-4.7-local-embedding.apk`; SHA-256 `4356d45e8db30c9fef5508fcc8bea918aba1c8fad14f546489b4bb1d47e30ad7`.
- Certificado local SHA-256: `6a2cb5763f9f5de3ec92f9487a3d02b0ed1aa693d4b0f072cca138a57b531a7b`. Coincide con el de `GlassCar-Ultra-4.6-local-embedding.apk`, también comprobado ahora (paquete idéntico, code 10, SHA-256 APK `e38aafb52172830fb1aee830928bb211eb002d67db8fbf62d63f941dbc30a17f`). **Permite actualizar sobre esa 4.6 local**. La firma del APK instalado en el auto no se inspeccionó; si proviene del CI 4.6, la firma documentada es distinta.
- Se mantienen los canales `0x9f1c`/`0x20b` solo de escucha, la tabla blmode 0 automático / 1 día / 2 noche, la exclusión de `com.tw.reverse` de EmbeddingPolicy y el reintento escalonado de TWUtil.
- No se tocó el estéreo ni el overlay TW. Reversa, brillo físico, tamaño de cámara y grabación DVR siguen pendientes de prueba en el auto.
- Código publicado: `54acff4e50009e1abf453250fc0754be255865d0` en `work/topway-carinfo`.
- CI automático (push): [run 43537968692 — SUCCESS](https://github.com/Hector780/glasscar-ultra/actions/runs/43537968692).
- CI manual: [run 43537915087 — SUCCESS](https://github.com/Hector780/glasscar-ultra/actions/runs/43537915087). Se inició al no aparecer todavía el run del push; después aparecieron ambos y ambos terminaron correctamente con el mismo commit.
- Ambos CI aprobaron `testDebugUnitTest lintDebug assembleDebug assembleRelease assembleReview assembleCameradiag assembleEmbedding` y publicaron APK y reportes. El APK entregado es el compilado y firmado **localmente**, no uno de CI.
- LEEME definitivo: `INSTALAR_DESPUES/LEEME-4.7.txt`. El cierre documental traslada este registro completo a `docs/CONTINUAR-CODEX.md`, deja `docs/PENDIENTE-PUSH.md` con 0 bytes y se publica con `[skip ci]`.
