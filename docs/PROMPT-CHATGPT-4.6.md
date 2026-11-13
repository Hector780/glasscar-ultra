> Nota de continuidad: este prompt describe el punto de partida. La revisión posterior y la entrega verificada están en [VERIFICACION-4.6.md](VERIFICACION-4.6.md) y en el último bloque de [CONTINUAR-CODEX.md](CONTINUAR-CODEX.md). El usuario revisará los videos en otro chat.

Eres el agente que continúa el proyecto GlassCar Ultra (launcher Android para mi estéreo TS10 de una Ford Escape 2010). Trabaja en español. Antes de cambiar nada, lee completo `docs/CONTINUAR-CODEX.md` (sección "4.6" y las notas del 13 nov). Abajo está el contexto actualizado y lo que necesito que hagas.

## Dónde está todo
- Repo: `C:\Users\hbernal\Documents\TS10_root\glasscar-ultra` → GitHub `Hector780/glasscar-ultra` (repo personal), rama `work/topway-carinfo`. NO tocar `main`.
- HEAD: `357da6d`. Encima hay cambios 4.6 SIN commit:
  - Modificados: `app/build.gradle.kts` (versionCode 10, versionName "4.6-ts10-integrated"), `EmbeddedAppService.kt`, `EmbeddingPolicy.kt`, `LauncherScreen.kt`, `MainActivity.kt`, `ProjectionCalls.kt`, `RealAppPanel.kt`, `TopwayAudio.kt`, tests `AppDrawerTest.kt`, `EmbeddingPolicyTest.kt`, `TopwayAudioTest.kt`, `docs/CONTINUAR-CODEX.md`.
  - Nuevos: `.gitattributes` (`*.sh text eol=lf`), `tools/Compilar-4.6.ps1`, `tools/overlay-tw-volume/` (overlay RRO + scripts).
- Herramientas locales: `..\work\build-tools\` (jdk-17, android-sdk con build-tools 34 y platform android-34, gradle-8.7). CI: `.github/workflows/build-apk.yml` (corre en push a `work/**`: testDebugUnitTest, lintDebug y assemble de todas las variantes; artefacto `GlassCar-Ultra-APK` con `app-embedding.apk`).
- Entregables para el estéreo: `C:\Users\hbernal\Documents\TS10_root\INSTALAR_DESPUES\`.
- Desensamblados de referencia: `C:\Users\hbernal\Documents\Codex\2026-11-12\new-chat\work\camera\inspection\TW\disassembly\com\tw\service\` (TW.apk 20210731) y `...\work\embedding\inspection\uCarDvr\` (HD Car DVR).

## Lo que el agente anterior (Claude) NO pudo hacer: hazlo tú
1. **Compilar y probar 4.6.** Claude escribió el código sin poder compilarlo (no tenía SDK de Android). Corre:
   `powershell -ExecutionPolicy Bypass -File tools\Compilar-4.6.ps1`
   (equivale a `gradle --offline --no-watch-fs --no-daemon --max-workers=2 testDebugUnitTest lintDebug assembleEmbedding` con JAVA_HOME/ANDROID_HOME de `..\work\build-tools`). Corrige cualquier error de compilación, test o lint (errores, no warnings) con el cambio mínimo. Puntos con más riesgo:
   - `EmbeddedAppService.kt`: reflexión a `IUsbManager` (getDeviceList/grantDevicePermission/setDevicePackage), `ActivityThread.getPackageManager().getPackageUid`, `getAllStackInfos` (campos stackId/displayId/topActivity/taskNames), helper privado `run(List<String>)`.
   - `LauncherScreen.kt`: `HorizontalPager(beyondBoundsPageCount = …)`; capa del cajón con `.pointerInput(Unit){ awaitPointerEventScope { while(true) awaitPointerEvent() } }`; `drawerApps(...)`.
   - `RealAppPanel.kt`: watchdog con `private companion object { const val WATCHDOG_MS }`.
   - `TopwayAudio.kt`: `LIGHT_CHANNEL = 0x204`, `nightLights`, `queryLights()` con default en la interfaz.
2. **Commit y push** a `work/topway-carinfo` (Windows tiene core.autocrlf; el repo guarda LF). Mensaje sugerido: "4.6: keep embedded sessions alive, hide Tlink, DVR USB grant, panel self-healing, MCU night channel, TW volume overlay".
3. **Esperar el CI**, descargar el artefacto y verificar `app-embedding.apk` (aapt dump badging: paquete `com.glasscar.launcher.embedtest`, versionCode 10; `apksigner verify --print-certs`; SHA-256). Dejarlo en `INSTALAR_DESPUES` con un `LEEME-4.6.txt` (commit, URL del run, firma, SHA-256).
   - OJO con la firma: la 4.5 instalada salió de CI. Si instalo una APK con otra firma, Android la rechaza ("conflicts with existing package") y hay que desinstalar antes, perdiendo los ajustes del launcher. Si la build local de `Compilar-4.6.ps1` y la de CI tienen firma distinta, dime cuál conviene y por qué.
4. Actualiza `docs/CONTINUAR-CODEX.md` con el resultado real (pasó o falló, qué corregiste).

## Qué hace 4.6 (código, aún sin probar en el estéreo)
- Con `REAL_APP_EMBEDDING`, las 4 pestañas quedan siempre compuestas (Tablero/Maps, CarPlay/Tlink, Cámara/DVR, Instrumentos) y el cajón se dibuja ENCIMA. Objetivo: que Maps, CarPlay y DVR no se reinicien nunca.
- Tlink (`com.zjinnova.zlink`) oculto en cajón, dock y recientes; cualquier apertura lleva a la pestaña CarPlay.
- `EmbeddedAppService` (corre vía Shizuku):
  (a) DVR: concede el permiso USB y fija HD Car DVR como app por defecto para 1b3f:8301 y 1908:2311. Hipótesis: el "me pide abrir la app" es el diálogo de Android "¿Abrir HD Car DVR para controlar…?". El filtro está en `SystemReceiverActivity` (translúcida: llama `XCDVR1Service.p()` y termina).
  (b) `am display move-stack` regresa al panel cualquier tarea del paquete que quede en display 0 (hand-off `Splash` → `MainActivityEx`, que es singleInstance; pantallas de llamada).
  (c) Espera 4.5 s por el `Splash` del DVR.
  (d) `status()` se repara solo (mueve o relanza) y `RealAppController` lo llama cada 2.5 s.
  Sin confirmar: que shell tenga MANAGE_USB e INTERNAL_SYSTEM_WINDOW. El diagnóstico lo mostrará.
- `TopwayAudio` abre también el canal MCU 0x204 (`arg1 == 1` = noche/luces, igual que TWService clase `x`, what 516) como respaldo de `can.lightsOn`.

## Ya hecho y CONFIRMADO en el estéreo (no lo rehagas)
- Root con Magisk + Termux. Módulo `/data/adb/modules/glasscar_ts10` instalado con `INSTALAR_DESPUES/glasscar-termux-4.6.txt`.
- **El popup de volumen de TW ya NO sale (confirmado por mí).** El popup es `com.tw.service.fa`, layout `volume_adjust` (0x7f080014), ventana TYPE_SYSTEM_DIALOG (2008). No hay ningún ajuste para apagarlo; `com.tw.service` es `android.uid.system`. Solución: overlay RRO `com.glasscar.overlay.twvolume` (raíz GONE de 0 dp que conserva los ids 0x7f06005a/5e/64 fijados con stable-ids) en `/vendor/overlay` vía Magisk. `cmd overlay list com.tw.service` → `[x]`.
- Lecciones aprendidas:
  - `pm install` del overlay en /data FALLA en Android 10 (firma distinta y sin `targetName`); como /vendor (Magisk) sí funciona.
  - `am force-stop` NO mata procesos persistentes: para reiniciar TW usa `kill -9 $(pidof com.tw.service)`. Ya está así en el `service.sh` del módulo y en los scripts.
  - Desactivar el overlay: `cmd overlay disable --user 0 com.glasscar.overlay.twvolume; kill -9 $(pidof com.tw.service)`.
- Vigilante de llamadas `/data/local/tmp/glasscar/llamadas.sh` (lo arranca el módulo). TWService abre `com.tw.bt/com.tw.bt.ATBluetoothActivity` cuando el módulo BT propio del estéreo recibe una llamada. Pasa cuando el iPhone queda conectado por BT además de CarPlay WiFi (me pasa a veces). El vigilante, si Tlink corre: manda a HOME y hace `am force-stop com.tw.bt`. Al volver de suspensión (salto de reloj > 30 s) relanza `com.tw.bt/.ATBluetoothService`. Toggles: archivos `apagar_llamadas` y `modo_suave` en `/data/local/tmp/glasscar/`. Log: `llamadas.log`. AÚN NO PROBADO con una llamada real.

## Reglas para trabajar con el estéreo
- En Termux NO uses `su -c "comando largo con comillas"`: el `su` de Termux las reacomoda mal. Escribe un archivo con heredoc y corre `su -c sh ~/archivo.sh`.
- Al pegar, el teclado a veces antepone `[200~`: empieza y termina cada bloque con una línea de relleno (`echo inicio` / `echo fin`).
- No borres ni deshabilites HD Car DVR, Tlink ni SystemUI completo. No silencies streams globales. El DVR original guarda historial por minuto y su video depende de un protocolo USB propietario (JNI); no hay sustituto.
- Tlink5 viene empaquetado y cifrado (dex de 70 KB): no se puede analizar estáticamente.
- `revserse_status` (Settings.System) solo se escribe en 1; no lo uses como fuente de reversa.
- Herramienta de captura: `tools/Capturar-TS10.ps1 -Event Volume|Call|Dvr|Can` (vía ADB; ADB normalmente no detecta el estéreo).

## Mis requisitos originales (verifícalos, también con los videos que te mande)
1. Maps, CarPlay y DVR NO se reinician al cambiar de pestaña ni al abrir el cajón.
2. CarPlay a pantalla completa. Pestañas y tablero aparecen con un gesto leve de arriba hacia abajo desde el borde superior. Volumen y brillo, con un gesto de abajo hacia arriba desde el borde inferior.
3. Al subir o bajar volumen desde el volante, aparece la barra de volumen y brillo del launcher (no la de TW/Android).
4. Sin icono flotante de volumen (hecho), sin el texto "confirmando volumen" (hecho en 4.5) y sin el texto enorme del cajón sobre cómo borrar apps (hecho en 4.5).
5. Llamada por CarPlay: el launcher salta a la pestaña CarPlay y la llamada se ve ahí. Sin el popup ni el ringtone de Android/TW. CarPlay solo existe dentro del launcher.
6. DVR visible en su pestaña sin que pida abrir la app, conservando la grabación por minuto.
7. CANbus completo. Hoy el voltaje sale a veces; velocidad, marcha y luces no. Usa el diagnóstico del launcher y lecturas reales; no lo des por resuelto porque pasan los tests.

## Cómo revisar mis videos
Extrae fotogramas (al menos 2 por segundo; más en los momentos clave) y dime por cada requisito: OK, falla o no se ve, con el segundo del video. Al final suelo mostrar "Diagnóstico del estéreo": transcríbelo y úsalo (inicios=1 y el mismo display por paquete = no se relanzó; `USB: permiso DVR concedido`; `TW luces (MCU 0x204)`; lecturas CAN).

## Seguridad
- No me pidas ni pegues tokens, contraseñas ni claves en el chat. Usa la sesión ya autenticada de git/gh.
- Antes de cualquier cambio en el estéreo que pueda dejarlo inestable, dame el comando para revertirlo.
- Prioriza cambios pequeños y verificables. Al terminar cada etapa, actualiza `docs/CONTINUAR-CODEX.md` para que otro agente pueda seguir.
