# Verificación y entrega 4.6

## Estado que debe usar el próximo chat

El usuario revisará los videos en otro chat de ChatGPT Work desde el celular. No se han revisado videos nuevos en esta continuación; no inventar segundos ni convertir pruebas de software en comprobaciones físicas. Usar esta rama `work/topway-carinfo`, este documento y `CONTINUAR-CODEX.md`. El prompt anterior describe el estado previo a esta revisión.

**Confirmado por el usuario:** el popup de volumen TW desapareció con el overlay en el módulo Magisk `glasscar_ts10`. No rehacerlo. El vigilante de llamadas de ese módulo sigue sin prueba real. No se hicieron cambios en el estéreo desde esta revisión.

## Error encontrado en la entrega 4.5

El archivo que estaba en `INSTALAR_DESPUES/GlassCar-Ultra-4.5-0069aa5-embedding.apk` era en realidad:

- Paquete `com.glasscar.launcher.cameradiag`, code 9, `4.5-ts10-integrated-camera-diagnostic`.
- SHA-256 `146c46b19909b1ca93b6020abc7976480c442c1c269a8477f245c881a9113de2`.
- Su LEEME indicaba como origen `cameradiag/app-cameradiag.apk` y la comprobación de firma había fallado por JAVA_HOME.

Por tanto, el nombre externo no probaba embedding. Si ese fue el archivo instalado, las pantallas sin integración no demuestran un fallo del código embedding. No afirmar cuál quedó instalado sin consultar el paquete en el dispositivo.

La variante correcta dentro del artefacto CI 4.5 es **`embedding/app-embedding.apk`**, paquete `com.glasscar.launcher.embedtest`, code 9. El ZIP descargado del run [43481086251](https://github.com/Hector780/glasscar-ultra/actions/runs/43481086251) tiene SHA-256 `2023a55a45559fbbf7cf335e299b87181f61ccd5f47c3887f14055c401fb7d94`.

## Revisión del código 4.6

Se conservaron las cuatro páginas compuestas, el cajón superpuesto, Tlink oculto del cajón/dock/recientes, USB DVR, recuperación de tareas y canal MCU 0x204. Correcciones de esta revisión:

- El watchdog programa la siguiente consulta al terminar la anterior: no acumula trabajo si Binder tarda más de 2.5 s.
- Se comprueba que la tarea regresó al display antes de anunciar recuperación. Un inventario global vacío se trata como consulta no disponible, sin relanzar la app a ciegas.
- No se mueve un stack sin nombres de tareas o que mezcle otro paquete. Cada stack se intenta una sola vez durante el inicio, evitando repetir 90 comandos de movimiento fallidos.
- La presencia de la tarea invitada cuenta aunque una actividad auxiliar esté encima. La interacción táctil no espera el monitor que protege un comando lento de recuperación.
- `iniciosApp` cuenta lanzamientos/reaperturas y `movimientos` los traslados. `inicios=1` del controlador por sí solo ya NO demuestra que el watchdog no relanzó una app.
- El resultado USB se conserva en el diagnóstico, incluso con errores. `predeterminada=N/M` distingue concesión de permiso de selección de app predeterminada; no se oculta el fallo de esta última.
- El UID de DVR se consulta sobre la interfaz IPackageManager y para el usuario del llamador, no sobre la clase privada del proxy ni siempre para user 0.
- Una lectura de luces distinta de 0/1 queda desconocida en lugar de interpretarse como día.
- `Compilar-4.6.ps1` verifica variante/code y firma; entrega un APK explícitamente **local**. No reconstruye el overlay ni abre Explorer por defecto. `-BuildOverlay` y `-OpenOutput` son opciones separadas.

El contrato USB se contrastó con [IUsbManager de Android 10](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-10.0.0_r1/core/java/android/hardware/usb/IUsbManager.aidl). La presencia de los métodos no demuestra permisos suficientes de shell ni compatibilidad del firmware: verificar Shizuku y diagnóstico real.

## Firma e instalación

- Certificado de CI 4.5: `b0a3b465d7ba6c6e72d806771e98010d2d3fd9a2ec181dc37aee8dd504de0dbc`.
- Certificado local persistente de esta PC: `6a2cb5763f9f5de3ec92f9487a3d02b0ed1aa693d4b0f072cca138a57b531a7b`.
- La clave local vive fuera del repo en `.android/debug.keystore`; no se publica. El workflow actual genera una clave debug en cada runner: no ofrece una identidad estable para actualizaciones.

Recomendación: usar el APK 4.6 **local verificado**, compilado del mismo código que pasó CI, para conservar firma en futuras compilaciones de esta PC. Guardar una copia privada de la clave. La variante CI se entrega por separado para trazabilidad, no como garantía de actualización sobre CI 4.5. Comparar el certificado real del paquete instalado antes de sustituirlo.

Si lo instalado es `.cameradiag`, `.embedtest` se instala como otra aplicación. Si ya existe `.embedtest` con otra firma, Android rechaza la actualización: no usar `-r` como supuesto bypass, ni desinstalar automáticamente. Guardar los ajustes antes de una reinstalación. No borrar DVR, Tlink ni el módulo Magisk.

## Checklist para los videos nuevos

| Requisito | Evidencia física necesaria |
|---|---|
| Sesiones persistentes | Ruta/conexión/imagen antes y después de pestañas y cajón; mismo display y sin incremento de `iniciosApp`. |
| CarPlay completo | Cuatro bordes, revelado superior/inferior y ocultamiento tras 4 s. |
| Volante | Audio y aparición conjunta de volumen/brillo; popup TW ya confirmado eliminado. |
| Llamada | Segundo en que timbra, cambio a CarPlay, ausencia/presencia de popup/ringtone duplicado, audio al contestar. |
| DVR | Permiso y predeterminada en diagnóstico, imagen dentro del panel y segmentos consecutivos de un minuto. |
| CAN | Valores reales de velocidad/marcha/luces/voltaje; distinguir MCU 0x204 de CarInfoService y de velocidad GPS. |

Extraer al menos dos fotogramas por segundo; más alrededor de transiciones y textos fugaces. Marcar OK / falla / no se ve con segundo exacto. Transcribir el diagnóstico final legible. Las llamadas requieren también escuchar audio: una imagen sin popup no demuestra ausencia de ringtone.

## Resultado de compilación y APK

El bloque final se completa tras la validación y descarga de CI. El resultado local se guarda en `work/glasscar-4.6-build.log`; la entrega incluye `INSTALAR_DESPUES/LEEME-4.6.txt` y APK separados local/CI. No tomar el APK anterior sin commit como entrega definitiva.


Validación local final de esta revisión: `tools/Compilar-4.6.ps1` → BUILD SUCCESSFUL; 95 pruebas, 0 fallos/errores/omitidas; lint 0 errores, 80 advertencias, 1 información. APK local code10 firmado y verificado; SHA-256 `e38aafb52172830fb1aee830928bb211eb002d67db8fbf62d63f941dbc30a17f`. El bloque de CI se añadirá al terminar la publicación.
