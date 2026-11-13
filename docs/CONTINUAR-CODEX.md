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
- `VolumeUI.start`: recursos enable_volume_ui / enable_safety_warning. No son Settings. No usar sysui_volume_down_silent/up_silent para ocultar popup (son políticas de volumen). EasyTouch referencia otro paquete com.my.maple_easytouch. Identificar dueño de la ventana real antes de modificarlo.
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
