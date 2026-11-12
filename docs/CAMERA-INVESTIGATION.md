# Cámaras TS10: investigación del 12 de noviembre de 2026

## Objetivo y estado

El usuario prioriza HD Car DVR dentro de GlassCar; también desea reversa si es posible. Confirma que HD Car DVR **sí muestra imagen en vivo y está conectada por USB**. Abrir la app externamente o mostrar un widget inexistente no cumple el objetivo.

La integración de imagen sigue pendiente. Este cambio añade exclusivamente inventario de cámara/USB y metadatos de apps para elegir una ruta con evidencia del equipo. El diagnóstico no abre USB, no solicita permisos USB, no reclama interfaces, no transmite comandos, no lee almacenamiento, no inicia servicios OEM y no cambia el firmware. No incluye seriales, rutas de dispositivos ni nombres de archivos del usuario. La vista previa Camera2 preexistente permanece independiente del inventario.

Base: `716dc74359bd2cecfcbb21d3ae54bf6ec53fcd89`, rama `work/video-review-4.1`. La prueba de audio 4.2 está en validación física por el usuario. La variante `cameradiag` usa `com.glasscar.launcher.cameradiag`, nombre **GlassCar Cámaras Diagnóstico**, y se instala aparte; no reemplaza la APK de volumen.

## Evidencia de APK originales

Inspección estática local con Androguard 4.1.4: manifiestos, instrucciones DEX y bibliotecas incluidas. No se ejecutaron los APK, ni se redistribuyen sus binarios, bibliotecas o código descompilado.

| Archivo | Paquete / versión | SHA-256 |
|---|---|---|
| HD Car DVR.apk | `com.williexing.android.apps.xcdvr1`, 2.13.3-gsj, code 3311, target 29 | `7c4521701d48842094d2e8fce3ac1cf230e3f931359b010a6f7b4a28e1b28141` |
| CameraIPControl.apk | `com.sprd.cameraipcontrol`, 1.0 | `041d5622458cdc8bcf2fff7e90a9518cb64e44f6e52cf93ff907e8cd8d1b90e7` |
| Right view.apk | `com.tw.rightview`, 1.0 | `bbbc2e5d600e148cd53b28239fc902d3c427805516b77d75e3108f5f4c461e75` |
| TW.apk | `com.tw.service`, 20210731 | `f17c234eb13a016aee506e84fd6e2e0bc963f790eb9519c274bf3cccfd3810a2` |

### HD Car DVR

- `SystemReceiver.onReceive` y `xandroid.xumsd.XUsbMassStorageDevice.getDevice` reconocen VID decimal 6975 / PID 33537: **`1b3f:8301`**. Esto identifica lo que busca el APK, no confirma todavía el USB conectado al usuario.
- El recurso del filtro USB `0x7f120000` (`res/GH.xml`) declara también VID 6408 / PID 8977: **`1908:2311`**. El diagnóstico identifica ese segundo candidato sin atribuirle el transporte del primero.
- `XUsbMassStorageDevice.getDevice` filtra interfaz clase 8, subclase 6, protocolo 80 (almacenamiento SCSI/Bulk), endpoints bulk IN/OUT. El código usa `UsbManager.openDevice`, `claimInterface`, transferencias bulk/control. No debe asumirse UVC por el solo hecho de estar conectado por USB. La ruta Java de almacenamiento existe; qué ruta utiliza efectivamente esta instalación requiere prueba del dispositivo y más análisis nativo.
- `XUFSCameraService` carga `jpeg-turbo` y `xufscamera`; declara métodos nativos `getXUFSDevice`, `openCamera`, `readCamera`, `closeCamera`, `sendCommand`, `sendCommand2`, además de operaciones distintas de firmware. Bibliotecas incluidas exclusivamente para `armeabi-v7a`.
- Las cadenas de `libxufscamera.so` referencian `XUsbMassStorageDevice`, `XUfsDeviceInputStream` y `elen1`. La clase Java busca un archivo llamado `elen1` y entrega un InputStream. Esto es una pista de transporte propio a través de almacenamiento; las cadenas no bastan para documentar framing, comandos ni la ruta ejecutada. No leer o modificar archivos del USB como experimento sin completar el contrato.
- El servicio `com.williexing.android.xiot.devices.XUFSCameraService` declara **`android:exported="false"`**. Su acción `com.williexing.xiot.device.xufscamera` no convierte el servicio en público. No intentar saltarse esa restricción.
- `XCDVR1Service.onCreate` carga callbacks opcionales por reflexión desde un array de recursos; `onBind` delega a un método opcional. Queda por identificar si hay un SDK autorizado del fabricante o un proveedor externo accesible; no se confirmó un contrato público para video.
- `XCameraService.onBind` devuelve null. `FloatingViewService` está exportado, pero construye un botón azul de texto "Floating Window" y dimensiones fijas; **no es un servicio de vista previa**. No usarlo como solución.
- El manifiesto no declara un proveedor AppWidget. El proveedor `AppUpdateProvider` corresponde a actualización/archivos, no demuestra un stream de cámara.

### CameraIPControl

`IPControlFragment` consulta/cambia propiedades `persist.vendor.cam.ip.*` para HDR, retrato, reducción de ruido, etc. No es un visor de cámara por red y su nombre no prueba RTSP/IP. No cambiar esas propiedades como prueba de integración.

### Right view y reversa

- `LRReverseActivity.J` escribe `sys.camera.ch=2` y abre `android.hardware.Camera.open(cameraId)`, con `cameraId` inicialmente 0. Emplea Camera API antigua, parámetros propietarios y TWUtil. Esto no demuestra que Camera2 ID 0 sea la DVR o reversa, ni que GlassCar tenga permiso para seleccionar canales.
- `TW` referencia explícitamente `com.tw.reverse.ReverseActivity`, `com.tw.reverse.LRReverseActivity` y `com.tw.reverse.RadarService` (por ejemplo, clases `com.tw.service.G` y `com.tw.service.X`). **`com.tw.reverse` no está en el ZIP de 23 APK**. Right view es otro paquete.
- Para continuar reversa, pedir únicamente el APK original de **Reverse (`com.tw.reverse`)**. No hace falta repetir el ZIP ni extraer paquetes sin relación.

## Siguiente prueba útil

1. Terminar la prueba de audio 4.2 y conservar su reporte por separado.
2. Instalar la variante Cámaras Diagnóstico. Con la DVR conectada y el equipo estacionado, abrir Configuración → Diagnóstico del estéreo y copiar el reporte completo. No hay que dar permiso USB para listar descriptores.
3. Revisar VID:PID, clases/subclases/protocolos y endpoints, ABI, Camera2 y metadatos del servicio OEM. Si aparece `1b3f:8301` con almacenamiento 8/6/80, continuar con la ruta propietaria; no añadir una biblioteca UVC a ciegas. Si hay interfaz 14, evaluar UVC por separado y sin confundirla con la entrada analógica.
4. Obtener `com.tw.reverse` para documentar el camino trasero de forma independiente.

## Límites y rutas de investigación

- No copiar `libxufscamera.so` ni clases OEM al launcher como supuesto SDK redistribuible.
- Si el protocolo de la DVR es propietario, buscar documentación/SDK del fabricante o completar un adaptador independiente después de identificar descriptores y contrato de lectura. No probar comandos de firmware ni adivinar órdenes USB/MCU.
- No prometer coexistencia de dos propietarios USB o grabación continua mientras GlassCar reclama la cámara. Ese comportamiento requerirá validar liberación, restauración y grabación del original.
- Incrustar una Activity ajena no se consigue envolviendo un Intent en AndroidView. El embedding público exige soporte y autorización de la app; las capacidades del TS10 API 29 no se dan por supuestas.

Fuentes primarias para las APIs públicas: [USB host](https://developer.android.com/develop/connectivity/usb/host), [UsbManager](https://developer.android.com/reference/android/hardware/usb/UsbManager), [Activity embedding](https://developer.android.com/develop/ui/views/layout/activity-embedding).

## Validación

Ejecutar `testDebugUnitTest lintDebug assembleDebug assembleRelease assembleReview assembleCameradiag` en GitHub Actions. Las nuevas pruebas corren en API 29 y 34: diferencian el ID DVR de otros almacenamientos, evitan afirmar imagen/UVC sin prueba, conservan evidencia de dispositivos compuestos y soportan ausencia de servicio/dispositivos/permisos/apps. No validan imagen real ni firmware. Consultar el último run de la rama `work/camera-investigation` para el estado definitivo.
