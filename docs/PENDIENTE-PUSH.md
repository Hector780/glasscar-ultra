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
- Commit y resultado de GitHub Actions: se completarán tras publicar y esperar CI.
