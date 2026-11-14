# Prompt para Claude (Fable) — GlassCar 4.7

Continúa mi proyecto GlassCar Ultra: un launcher Android para el estéreo TS10 de mi Ford Escape 2010. Trabaja en español y enfócate en **cambiar código**. El análisis ya está hecho.

## Antes de empezar
- Repo: `C:\Users\hbernal\Documents\TS10_root\glasscar-ultra`, rama `work/topway-carinfo`. Pide acceso a la carpeta `TS10_root` si no la tienes conectada.
- Lee, en este orden:
  1. `docs/ESPEC-4.7-VIDEO.md`: la especificación completa, sacada de mi video. Es la fuente de verdad de lo que quiero.
  2. `docs/VERIFICACION-4.6.md`
  3. El final de `docs/CONTINUAR-CODEX.md`
  4. Lee `docs/TS10-AUDIO.md` y `docs/APP-EMBEDDING.md` solo cuando las necesites.
- Revisa `git status` y `git log -3`. Si hay trabajo sin commit, no lo pises.

## Restricción de GitHub (IT)
- Tú NO haces `git commit`, `git push` ni operaciones con GitHub. No las hagas de forma indirecta a través de otra herramienta o agente.
- Editas archivos en la carpeta y al terminar cada etapa actualizas `docs/PENDIENTE-PUSH.md` con:
  - archivos cambiados y por qué
  - pruebas agregadas
  - riesgos o puntos que no pudiste verificar (incluye el riesgo de que no compile)
  - el mensaje de commit sugerido
- Yo reviso ese archivo y luego le pido a Codex que compile, pruebe, haga commit y push, y deje el APK verificado. Si tu entorno no tiene el SDK de Android, escribe código conservador y marca en `PENDIENTE-PUSH.md` las líneas con más riesgo de no compilar.

## Qué ya funciona: NO tocar
- El popup de volumen de TW ya está eliminado y confirmado en el estéreo. Lo hace el overlay RRO `com.glasscar.overlay.twvolume`, que vive en el módulo Magisk `/data/adb/modules/glasscar_ts10`. No lo reconstruyas, no lo reinstales y no lo desinstales.
- El vigilante de llamadas `/data/local/tmp/glasscar/llamadas.sh` sigue instalado; todavía no se ha probado con una llamada real.
- 4.6 mantiene las 4 páginas compuestas y el cajón encima. El watchdog `EmbeddedAppService` regresa las apps a su panel y reporta `iniciosApp`/`movimientos`. Mantén todo eso.
- El DVR ya muestra imagen dentro de GlassCar. No lo sustituyas por Camera2/UVC; su protocolo USB es propietario (JNI) y graba por minuto.

## Hechos técnicos confirmados (úsalos, no los redescubras)
- **Brillo nativo del TS10.** El panel de SystemUI no usa `Settings.System`. Escribe por TWUtil con `write(516, modo, valor)` (`QSPanelBrightness$1.onProgressChanged`).
  - `modo`: si `blmode==0` (automático) usa `nightMode`; si `blmode==1` es 1; si `blmode==2` es 0.
  - Consulta: `write(516, 255)`. También `write(258, 255)` en `initView`.
  - TWService (clase `x`, `what` 516) recibe `arg1==1` como noche, el byte bajo de `arg2` como brillo de día y `(arg2>>8)&255` como brillo de noche.
  - Evidencia: `TS10_root/work/systemui-inspection/com_android_systemui_tw_qspanel_QSPanelBrightness*.txt` y `...\new-chat\work\camera\inspection\TW\disassembly\com\tw\service\X.txt` (alrededor de las líneas 2350 y 5290).
  - `TopwayAudio` ya abre 0x203 (volumen) y 0x204 (luces) en una sola conexión TWUtil. El brillo debe usar esa misma conexión, no abrir otra.
  - Antes de escribir, confirma el máximo real del seekbar nativo en esos archivos; no lo adivines.
- **Volumen.** Canal 0x203, `decode(arg1, arg2)` con máximo real y bit de silencio. El volante lo procesa el MCU: no mandes un segundo comando.
- **Reversa.** `revserse_status` no sirve, porque se queda en 1. Busca la señal real (CarInfoService, eventos de `com.tw.reverse` o TWService). Referencias: `TS10_root/apks_topway/com.tw.reverse.apk` y `TS10_root/work/reverse-inspection/`.
- **Termux en el estéreo.** Para darme comandos, usa siempre un archivo con heredoc y `su -c sh ~/archivo.sh`, nunca `su -c "..."` con comillas anidadas. Empieza y termina cada bloque con `echo inicio` / `echo fin` (el teclado antepone `[200~`).
- **Reiniciar TWService.** `am force-stop` no mata procesos persistentes; usa `kill -9 $(pidof com.tw.service)`.

## Orden de trabajo (una etapa a la vez; actualiza PENDIENTE-PUSH.md al cerrar cada una)
1. **Cámara (sección 7 de la espec).**
   - Video más grande, sin la fila «Cámara trasera · original».
   - Selector frontal/trasera como cápsula sobrepuesta.
   - Al entrar en reversa real, cambiar a la pestaña Cámara una sola vez por transición.
   - Investiga si la trasera se puede ver dentro del panel sin falsear la reversa. Si no se puede, deja un estado claro en el panel y el hallazgo documentado. No abras la app OEM por fuera como si fuera la solución.
2. **Brillo físico (sección 5).** Adaptador TS10 con el contrato 516 de arriba. Debe tener un solo estado compartido entre la barra inferior y el futuro centro de control, resincronizarse si cambia desde el panel nativo y quedar registrado en el diagnóstico.
3. **Geometría y gestos (sección 8).**
   - Pestañas, dock y controles en las mismas posiciones y tamaños en las 4 páginas.
   - CarPlay a pantalla completa.
   - Gesto superior que no revele la barra de Android; separa las zonas, por ejemplo con un asa visible.
   - Un solo dock coherente.
   - Abrir controles no debe redimensionar ni relanzar apps invitadas.
4. **Popup de volumen global (sección 11).**
   - Cápsula vertical estilo `IMG_0615`.
   - Un solo dueño del estado TW (servicio) que dibuje dentro del launcher y, fuera, en una ventana superpuesta de tamaño mínimo.
   - Que no salte por lecturas periódicas ni al reconectar.
   - Que no se duplique con la barra inferior.
5. **Centro de control (sección 9) y centro de notificaciones (sección 10)**, con un sistema compartido de superficies de cristal (sección 4).
   - Datos reales.
   - Las persistentes se ocultan solo en GlassCar, nunca deteniendo servicios.
   - No deshabilitar SystemUI; si el gesto global requiere tocar el sistema, propón algo reversible y documenta la reversión.
6. **CAN (sección 6).** Agrega al diagnóstico las lecturas crudas, su fuente y su vigencia. No conviertas -1 ni datos caducos en 0, P o «luces apagadas».

Haz las etapas en orden. Si se te acaban los tokens, `PENDIENTE-PUSH.md` y `CONTINUAR-CODEX.md` deben bastar para que otro agente continúe.

## Reglas
- Sube `versionCode` a 11 y `versionName` a «4.7-ts10-integrated». Crea `tools/Compilar-4.7.ps1` a partir del 4.6 con las comprobaciones de versión actualizadas.
- Para cada cambio importante, agrega pruebas unitarias o de Compose que corran en JVM/Robolectric. Sigue el estilo de los tests existentes.
- No borres HD Car DVR, Tlink ni SystemUI. No escribas comandos MCU desconocidos para probar.
- Antes de cualquier cambio en el estéreo (Termux/root), dame primero el comando para revertirlo.
- Al final de cada etapa, dime en 3-5 líneas qué quedó hecho en software, qué requiere prueba física y qué quedó bloqueado. No declares nada terminado solo porque compila.
