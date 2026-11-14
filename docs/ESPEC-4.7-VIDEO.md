> Especificación de entrada para 4.7. Origen: análisis de Codex (cuenta hbernal) del video IMG_0612.mp4 y las referencias IMG_0615.jpeg / IMG_0616.jpeg, 13-14 nov 2026. Se guarda sin cambios. El orden de trabajo y los límites están en docs/PROMPT-FABLE-4.7.md.

INSTRUCCIONES PARA EL OTRO CODEX — GLASSCAR ULTRA, BASE 4.6
Revisión de IMG_0612.mp4 y referencias IMG_0615.jpeg / IMG_0616.jpeg.

Continúa mi proyecto GlassCar Ultra para el estéreo TS10 de mi Ford Escape 2010. Trabaja en español y realiza los cambios descritos en este documento sobre la versión 4.6. Necesito una implementación funcional y una entrega comprobada, con evidencia clara de lo que requiere probarse físicamente. Este documento fue preparado mediante revisión de lectura: no se cambió código, no se compiló ni se modificó el estéreo para elaborarlo.

1. BASE CORRECTA Y CONTINUIDAD

Repositorio: https://github.com/Hector780/glasscar-ultra
Rama de trabajo: work/topway-carinfo. No partir de main: main todavía declara 4.0.
HEAD consultado para este análisis: 92e9ddc44edaa3fcd5a539592fe2bee9746f706e.
La rama declara versionCode 10 y versionName 4.6-ts10-integrated. La variante embedding añade el sufijo -real-app-prototype.
El código de la entrega 4.6 documentada se compiló desde ee070248c1a902b9e9f3abf1b5185030751e524c. No confundas el commit posterior de documentación con otro APK nuevo.

Antes de editar:
- Revisa el estado local, cambios sin commit y HEAD remoto. Conserva el trabajo posterior que ya exista; los SHA anteriores son referencias de esta revisión, no instrucciones para retroceder el repositorio.
- Lee docs/CONTINUAR-CODEX.md completo, docs/VERIFICACION-4.6.md, docs/PROMPT-CHATGPT-4.6.md, docs/APP-EMBEDDING.md, docs/CAMERA-INVESTIGATION.md, docs/TS10-AUDIO.md y docs/INTEGRACION-4.5.md. La verificación y los últimos bloques de continuidad tienen prioridad sobre las notas antiguas que dicen «sin compilar» o «sin commit».
- Verifica qué paquete y variante están realmente instalados antes de atribuir todo fallo al código embedding. Existió una entrega 4.5 mal nombrada que era cameradiag. No supongas que sigue siendo el caso ni que el nombre del archivo demuestra la variante.
- Ruta local previamente documentada: C:\Users\hbernal\Documents\TS10_root\glasscar-ultra. Si trabajas en otro entorno, usa el checkout correspondiente y conserva la rama correcta.

La 4.6 documenta 95 pruebas aprobadas y lint sin errores; eso no demuestra funcionamiento físico de CAN, brillo, cámaras, llamadas ni persistencia de sesiones.

2. FUENTES Y ALCANCE DE ESTA REVISIÓN

Video nuevo: IMG_0612.mp4, duración 187.5 segundos. Se extrajeron 375 fotogramas a 2 por segundo y se revisaron secuencias representativas, transiciones y la narración mediante transcripción automática. Los intervalos siguientes son aproximados y agrupan cada observación; no son citas literales. Si necesitas resolver una palabra ambigua, vuelve al audio original.

Referencias visuales:
- IMG_0615.jpeg: centro de control. Fondo oscuro desenfocado; módulos de cristal gris translúcido; controles circulares; dos deslizadores verticales en cápsula, brillo y volumen; contenido blanco que llena la cápsula desde abajo; sol amarillo y altavoz cian.
- IMG_0616.jpeg: centro de notificaciones. Hora y fecha destacadas; tarjetas transparentes con bordes suaves; iconos, texto y hora; agrupaciones apiladas; fondo visible a través del material.

No hay una captura independiente de un popup compacto de volumen entre estas dos imágenes. Para ese popup, deriva el diseño del deslizador de volumen de IMG_0615.jpeg. No inventes que recibiste una tercera referencia.

No copies los nombres, mensajes, porcentajes de batería, operadores, canción, dispositivos del hogar ni la foto personal de las capturas como contenido fijo de GlassCar. Son referencias de aspecto, no datos de demostración para producción.

En el video no aparece el reporte «Diagnóstico del estéreo». No inventes lecturas CAN, display IDs, contadores de relanzamiento ni versiones instaladas a partir de él. El iPhone no aparece conectado a una sesión CarPlay real: se ve la pantalla de conexión de Tlink. Por tanto, no se comprobaron una llamada real, su ringtone, la continuidad de una sesión CarPlay conectada ni archivos de grabación DVR.

3. OBSERVACIONES DEL VIDEO QUE DEBES ATENDER

00:00–00:10 — Volumen y brillo.
El usuario confirma que ya no aparece el popup feo de TW. El control de volumen propio está presente. El brillo sigue sin cambiar físicamente aunque se mueva el deslizador. Conservar la supresión del popup TW; resolver el brillo real.

00:10–00:19 — Instrumentos/CAN.
El usuario reporta que los instrumentos siguen sin recibir los datos CAN y que la velocidad funciona por GPS. En pantalla aparece «KM/H · GPS». Estando estacionado, un cero no demuestra lectura CAN válida. Diagnosticar velocidad, marcha, luces y voltaje con su fuente real y vigencia.

00:20–00:30 — Tamaño de la cámara.
La cámara del DVR ya muestra imagen dentro de GlassCar. El usuario la siente recortada y pide aprovechar más superficie, particularmente arriba. Conservar la integración que ya da imagen y mejorar el encuadre y distribución.

00:30–01:07 — Selector de cámara trasera.
El botón «Cámara trasera · original» abre una pantalla externa y fuera de reversa se ve negra. El usuario pide un control pequeño sobrepuesto sobre el video que cambie la cámara dentro del mismo panel. Quiere poder ver la trasera manualmente sin tener que engranar reversa. Abrir la app OEM por fuera no cumple esta solicitud.

01:08–01:24 — Comportamiento al engranar reversa.
Si está en Tablero u otra página y entra reversa real, quiere cambiar automáticamente a la pestaña Cámara y mostrar la trasera ahí. No pide forzar toda la pantalla a un modo externo de cámara. El cambio debe ocurrir una vez por transición de reversa, sin bucles.

01:24–02:03 — Distribución, CarPlay y gestos.
Pide revisar en conjunto Tablero, CarPlay, Cámara e Instrumentos. CarPlay puede ocupar toda la pantalla, pero al revelar la navegación superior y controles inferiores, estos deben mantener posiciones y proporciones coherentes con las demás vistas. Al intentar el gesto superior se revela la barra Android. Hay que poder sacar los controles propios de forma fácil, sin tener que acertar con un gesto delicado.

02:03–02:14 — Controles al cambiar volumen.
En Tlink se ve que aparece el control inferior al cambiar volumen. El usuario pide que sea mayor y coherente estéticamente; al volver a Tablero la distribución cambia. Unificar geometría y lenguaje visual. Además, por instrucción escrita, el popup de audio debe aparecer también sobre una app externa que hoy no muestre indicación.

02:14–02:35 — Barra lateral y continuidad visual.
Pide conservar el tono visual general pero corregir la barra izquierda y los controles que rompen la continuidad con CarPlay. No basta con que ambos tengan una barra a la izquierda: deben sentirse parte de la misma interfaz, sin dos docks redundantes ni escalas incompatibles.

02:42–02:51 — Centro de control / cortina nativa.
Muestra el panel blanco del sistema con accesos y dos barras horizontales. No le gusta esa interfaz. Sustituir la experiencia habitual por el diseño de cristal de IMG_0615.jpeg, adaptado a la pantalla horizontal.

02:51–03:07.5 — Notificaciones.
Muestra la cortina blanca y varias notificaciones, incluidas persistentes, que dice que no puede borrar. Quiere cambiar su diseño y limpiar esa experiencia. Separar «descartar una notificación» de «ocultarla de la vista GlassCar»; no detener Tlink, DVR, Termux ni otros servicios para conseguir una lista vacía.

4. DISEÑO COMPARTIDO PARA TODO GLASSCAR

Construye un sistema de superficies y controles compartido para centro de control, notificaciones, popup de volumen, pestañas y dock. Usa las capturas como referencia principal del material y la composición; conserva la identidad general actual del launcher.

Aspecto requerido:
- Material de cristal oscuro translúcido, con desenfoque del contenido de fondo donde sea viable, borde fino luminoso, sombras suaves y radios amplios.
- Evitar que cada módulo sea un rectángulo opaco distinto. Mantener contraste y legibilidad sobre mapa, cámara y fondos claros.
- Iconografía uniforme y limpia; no usar emojis o caracteres de texto como sustitutos de controles definitivos.
- Tipografía consistente, jerarquía clara, textos secundarios atenuados y animaciones breves.
- Modo día y noche coherentes con el resto del launcher. No alterar brillo físico para simular un cambio de tema.
- Adaptación horizontal real. No encoger una captura vertical hasta que sus controles queden diminutos.
- Objetivos iniciales de implementación, ajustables tras medir el TS10: márgenes de 12–16 dp, separación 8–12 dp, radios de tarjetas 24–32 dp y zonas táctiles de al menos 48 dp. Estos son criterios propuestos, no mediciones de la captura.

Revisa Glass.kt: SpatialGlassSurface actualmente vuelve a dibujar el fondo de aurora y lo desenfoca; eso por sí solo no es un desenfoque de las apps/mapa/cámara que estén detrás. No declares que ya hay cristal real sobre cualquier app. Evalúa una capa de fondo apropiada y su costo en Android 10; si hay que usar una alternativa translúcida sin blur, que conserve el diseño, no desenfoque el texto y quede documentada como tal. Evita capturas de pantalla continuas como mecanismo de efecto visual.

5. BRILLO FÍSICO DEL TS10

Archivos de partida: HeadUnitControls.kt, MainActivity.setBrightness(), lectura de DeviceStatus en Platform.kt y HardwareDiagnostics.kt.

Problema confirmado por el usuario: mover el control no cambia la iluminación del panel. La implementación actual escribe Settings.System.SCREEN_BRIGHTNESS y limpia la atenuación de ventana. Un retorno true de escritura no demuestra que el firmware cambió la retroiluminación.

Trabajo requerido:
1) Comparar el mecanismo que usa el control nativo del TS10 con el del launcher: valores antes/después, modo manual/automático, límites reales y cualquier servicio del fabricante que gobierne el panel.
2) Revisar las referencias OEM ya disponibles y usar un contrato observado, no un canal MCU o ruta sysfs adivinados.
3) Implementar un adaptador de brillo TS10 si el ajuste Android no gobierna la retroiluminación. Aprovechar root/Shizuku sólo donde haga falta y con alcance limitado al control identificado.
4) Compartir una única fuente de estado entre control inferior, centro de control y cualquier vista expandida; resincronizar si cambia desde el panel nativo.
5) Mantener un mínimo útil y recuperación a un brillo legible. No resolverlo poniendo una capa negra encima, bajando alpha del contenido o cambiando solamente window.screenBrightness de una actividad.
6) Registrar valor solicitado, lectura de retorno y mecanismo utilizado en el diagnóstico; no llenar la interfaz habitual de mensajes técnicos.

Aceptación: con iluminación ambiental estable, varios niveles producen un cambio físico claro en Tablero, una app externa y después de volver de suspensión. Si no hay mecanismo confirmado, conservar acceso al ajuste nativo y marcarlo pendiente; no presentar un deslizador visual como solución.

6. CAN: DATOS REALES, MARCHA, LUCES Y REVERSA

Revisar TopwayCarInfo.kt, CanBus.kt, sus AIDL, TopwayAudio.kt y el diagnóstico.
- Ya existe conexión con com.tw.carinfoservice, getters periódicos y callbacks. No repetir una solución genérica de broadcasts sin revisar esa integración.
- Obtener diagnóstico nuevo de conexión, respuestas crudas de getters relevantes, excepciones, callbacks, hora de lectura y configuración real de decodificador. Comparar con la app OEM que sí pueda mostrar esos datos.
- Preservar la compatibilidad exacta de los AIDL; no reordenar métodos/transacciones.
- No convertir ausencia, -1, lecturas caducas o arreglos inválidos en cero, P o luces apagadas.
- Mostrar velocidad CAN cuando exista una lectura válida; GPS como respaldo identificado. La selección manual de marcha nunca debe fingir una lectura CAN.
- Distinguir la señal de luces MCU 0x204 del canal de audio 0x203 y de los datos CarInfoService; ya hay soporte 0x204 en 4.6. No escribir comandos desconocidos para probar.
- No usar revserse_status como fuente fiable: la continuidad del repo documenta que se queda escrita en 1.
- Obtener una señal de reversa real con flancos y vigencia; navegar a Cámara sólo al entrar en reversa. Decidir y documentar qué pasa al salir, preservando la página anterior si es viable. La restauración es una propuesta de UX, no una exigencia literal del video.

Aceptación: evidencias por dato y fuente. Una prueba simulada sólo valida transformación y navegación, no que el vehículo envía el dato. Si CarInfoService no lo proporciona, entregar el hallazgo y el siguiente diagnóstico concreto, sin declarar CAN terminado.

7. CÁMARA FRONTAL/DVR Y TRASERA EN UN PANEL

En 4.6 CameraPanel de EmbeddedPanels.kt deja una fila exclusiva para «Cámara trasera · original» y debajo incrusta HD Car DVR. MainActivity.openRearCamera() abre RearCameraPolicy.intent(), que apunta a com.tw.reverse/com.tw.reverse.ReverseActivity en el display principal. EmbeddingPolicy permite Maps, Tlink y DVR; la app de reversa no está autorizada como invitada.

Cambios requeridos:
- Hacer más grande el video quitando espacio muerto y la fila separada. Poner el selector frontal/trasera como cápsula discreta sobrepuesta en una esquina, con zona táctil suficiente.
- Preservar relación de aspecto y contenido útil. Diferenciar un panel pequeño de un recorte de imagen o un buffer escalado incorrectamente; no «agrandar» haciendo zoom y perdiendo más campo de visión.
- Mantener DVR funcionando y grabando segmentos de un minuto. La app usa un protocolo USB/JNI propietario: no sustituirla por Camera2/UVC sin evidencia de compatibilidad.
- Al cambiar a trasera, presentar la señal en el mismo contenedor. No mostrar una pantalla externa como si fuera integración.
- Investigar por separado: disponibilidad eléctrica de la cámara fuera de reversa, activación de entrada de video, superficie OEM y controles de MCU. Una app abierta con pantalla negra no demuestra que baste incrustar su Activity.
- Determinar si se puede activar únicamente la vista/entrada de video de forma manual sin falsificar la marcha del vehículo. No forzar globalmente el estado «reversa» para obtener imagen.
- No agregar com.tw.reverse a la lista de embedding a ciegas: su política actual existe porque gobierna estado global OEM. Si cambias esa arquitectura, justificar con evidencia y aportar reversión.
- Al detectar reversa real desde otra página, pasar a Cámara y seleccionar trasera, conservando la distribución solicitada. No bloquear la prioridad de la cámara OEM de reversa hasta tener una alternativa físicamente verificada.
- Si no hay señal o soporte para vista manual, mostrar un estado breve dentro del panel y explicar el límite exacto. No contar como resuelto el botón anterior que abre la app original.

Aceptación: frontal visible, selector sobrepuesto, trasera dentro del panel, solicitud manual fuera de reversa, transición por reversa real y continuidad de grabación. Registrar por separado cualquier punto que no pueda probarse o no sea compatible.

8. GEOMETRÍA ESTABLE, CARPLAY Y GESTOS

Revisar LauncherScreen.kt, ImmersiveControls.kt, RealAppPanel.kt y EmbeddedAppService.kt.

Necesito dos comportamientos compatibles:
- CarPlay ocupa el área completa cuando la navegación del launcher está oculta.
- Al revelar pestañas/dock/controles propios, su posición, tamaño y aspecto corresponden al mismo sistema que en Tablero, Cámara e Instrumentos. No desplazar cada elemento a otra coordenada según la página.

La barra superior y los controles inferiores se superponen: revelarlos no debe redimensionar ni recrear la app invitada. Mantener displays, tareas, buffers y superficies entre pestañas, cajón y centros nuevos. No crear dos vistas simultáneas de una misma sesión para resolver el layout.

Gestos:
- Gesto corto hacia abajo en la franja superior para navegación del launcher. Gesto hacia arriba desde abajo para controles rápidos. Mantener acceso sencillo a centros de control/notificaciones con zonas claramente diferenciadas o una selección visible.
- Resolver el conflicto que revela la barra Android. Analizar si lo que se ve pertenece al display principal o a una app invitada; no asumir que WindowInsets del launcher controla ambas.
- Una propuesta inicial es separar el gesto superior en zona izquierda para notificaciones y derecha para control, manteniendo pestañas mediante una zona/asa central. Ajustar esta propuesta si la arquitectura actual ofrece una solución más clara. No debe haber dos acciones compitiendo por el mismo gesto.
- Los controles y deslizamientos propios deben consumir sólo lo que manejan. Fuera de sus regiones, mapa y app invitada reciben sus toques. Respetar ACTION_CANCEL y evitar toques que atraviesen paneles abiertos.
- Mantener los cuatro segundos de ocultamiento de 4.6 como punto de partida; pausar/reiniciar el temporizador durante interacción.
- Conservar una salida clara a Inicio/Tablero sin tener que revelar repetidamente los controles Android.

Barra izquierda:
- Revisar alineación, ancho, iconos, reloj y estados para aproximar la coherencia que el usuario busca con CarPlay.
- Evitar un dock GlassCar permanente más otro dock del propio CarPlay compitiendo por espacio.
- No tapar controles de la interfaz CarPlay transmitida ni prometer modificar internamente la UI del iPhone. Resolverlo en el marco y presentación que GlassCar controla.

La 4.6 ya conserva las cuatro páginas compuestas y el cajón encima. No deshacer eso. El watchdog no debe relanzar Tlink, Maps o DVR por abrir un centro o por pérdida temporal de foco.

9. CENTRO DE CONTROL CON DISEÑO DE IMG_0615.jpeg

Implementar un centro propio de GlassCar funcional, accesible sobre sus páginas y coordinado con apps externas donde el alcance global sea necesario.

Composición horizontal propuesta:
- Módulo de conectividad con botones circulares y estados reales.
- Módulo multimedia con portada cuando exista, título/artista y anterior/reproducir-pausa/siguiente usando las fuentes reales existentes.
- Dos cápsulas verticales destacadas para brillo y volumen, con llenado desde abajo, sol amarillo y altavoz cian. Son el elemento más directo de la captura.
- Controles secundarios pertinentes al estéreo: modo día/noche, ajustes, accesos disponibles y cierre. Evitar llenar la pantalla con funciones de un iPhone o una casa que el TS10 no tiene.
- La posibilidad de personalizar accesos puede reutilizar preferencias existentes; no inventar estados activos para botones decorativos.

Estados:
- Gris translúcido cuando está inactivo; azul para conectividad activa y otros colores sólo cuando corresponda al estado real.
- Si una API no permite cambiar Wi-Fi/Bluetooth, abrir su ajuste específico de manera clara. No cambiar el color como si la radio se hubiera activado.
- No duplicar controles de audio que gobiernen volúmenes distintos. En este estéreo el volumen relevante es MCU/TW, no STREAM_MUSIC.
- No poner un botón de encendido que apague o reinicie el estéreo sin una función definida y protección contra toques accidentales. No es obligatorio copiar ese botón de la captura.

Abrir/cerrar el centro no debe cambiar de pestaña ni detener sesiones. La cortina blanca no debe seguir apareciendo en el flujo habitual donde se pidió el reemplazo. Conservar una vía explícita de acceso a ajustes/notificaciones del sistema para mantenimiento.

Si sustituir globalmente el gesto de SystemUI requiere intervención del sistema, distinguirlo de dibujar un panel Compose dentro del launcher. Investigar un punto de integración concreto y reversible en el TS10; no deshabilitar SystemUI entero, ni afirmar que un overlay visual ya reemplaza los gestos globales.

10. CENTRO DE NOTIFICACIONES CON DISEÑO DE IMG_0616.jpeg

Cambiar la experiencia blanca nativa por una vista GlassCar con tarjetas de cristal y datos reales.

Aspecto y distribución:
- Fecha/hora con jerarquía clara, sin gastar toda la altura de la pantalla horizontal.
- Encabezado «Centro de notificaciones», cierre y acción de limpiar las descartables.
- Tarjetas de cristal con icono real de app, título, texto, hora relativa y miniatura cuando exista.
- Agrupación por app/conversación cuando los datos lo permitan, indicador de cantidad y apilamiento visual; al tocar, expandir sin mezclar mensajes de otras apps.
- Lista desplazable, estado vacío, estado de acceso no concedido y textos largos legibles.
- El fondo debe ser el del usuario/GlassCar o el contexto permitido, no la foto de la captura puesta como wallpaper fijo.

Datos y acciones:
- Extender MediaListenerService de Platform.kt o extraer una capa compartida conservando música, navegación y llamadas. Actualmente NO es un repositorio de todas las notificaciones: sólo alimenta esas funciones específicas.
- Mantener instantánea inicial, altas, actualizaciones y bajas por clave estable; sin duplicados tras reconexión.
- Respetar permisos, contenido sensible y acciones que realmente estén disponibles. No registrar cuerpos de mensajes ni números en logs de diagnóstico.
- Abrir mediante contentIntent/acciones originales cuando existan; manejar PendingIntent cancelados. No simular acciones con texto.
- Descartar individuales o «limpiar» únicamente cuando la notificación lo permita. Gestionar el rechazo sin dejar UI desincronizada.
- Para notificaciones persistentes que el sistema no deja borrar, ofrecer ocultación local en GlassCar, silenciamiento visual o una sección «Servicios activos» plegada, con opción de volver a mostrarlas. Dejar claro que ocultar no cancela el servicio ni elimina la notificación del sistema.
- No detener DVR, Termux, Tlink, CarInfoService ni cambiar protecciones del sistema para que desaparezcan tarjetas.
- Tener una forma de consultar lo ocultado y ajustar filtros por app; conservar la preferencia al reiniciar.
- Las notificaciones del iPhone sólo aparecen si Tlink u otra integración realmente las expone. No afirmar que NotificationListenerService de Android da acceso a todas las notificaciones de iOS.

11. POPUP GLOBAL DE VOLUMEN, CON EL DISEÑO DE LA CAPTURA

Requisito escrito adicional: estando en una app externa donde al subir/bajar volumen no aparece ninguna indicación, debe aparecer una indicación GlassCar con el diseño enviado.

El popup antiguo TW YA FUE ELIMINADO y confirmado por el usuario. El módulo instalado se llama glasscar_ts10; no confundirlo con los scripts standalone glasscar_twvolume. No reinstalar ni desinstalar ese overlay como parte de esta corrección.

Hallazgo concreto del código 4.6:
- TopwayAudio arranca en MainActivity.onStart() y se detiene en onStop().
- El disparador visual volumeEvent vive en LauncherScreen.kt.
- ImmersiveControls sólo dibuja su capa cuando está en el árbol del launcher y en modo inmersivo.
Eso no garantiza indicación sobre una app que haya llevado el launcher a STOPPED. No basta agrandar QuickControlBar ni agregar onKeyDown a MainActivity.

Arquitectura a resolver:
1) Centralizar la conexión/estado TW y eventos de volumen en un propietario con ciclo de vida adecuado para el alcance global solicitado, evitando dos conexiones y dos emisores de comandos. Evaluar un servicio con el mecanismo de ejecución y permisos apropiado para Android/firmware presentes.
2) Dentro de GlassCar, dibujar el popup desde una capa común. Fuera, evaluar una ventana de aplicación superpuesta con el permiso correspondiente o un mecanismo de integración TS10 limitado. Justificar cuál funciona realmente en Android 10 y el firmware de este equipo.
3) Usar la misma implementación visual y estado. Un solo gestor decide dónde dibujar; nunca dos popups GlassCar simultáneos ni el viejo popup OEM reactivado.
4) Mantener una fuente nativa de lectura: TopwayAudio, canal 0x203, nivel/máximo/silencio decodificados. No hardcodear máximo 15 o 30 ni cambiar STREAM_MUSIC para imitar el amplificador.
5) Los eventos deben provenir de cambios reales o de entradas de volumen confirmadas. Las consultas periódicas sin cambios no deben abrir el popup cada dos segundos. La lectura inicial y una reconexión tampoco deben abrirlo sin interacción. Considerar pulsaciones en máximo/mínimo si existe un evento fiable aunque el nivel no cambie.
6) No sumar un comando a un evento que la MCU ya procesó: evita doble incremento por volante/tecla. Al arrastrar el control, seguir el contrato validado de escribir y confirmar mediante lectura de retorno.
7) Conservar protección contra lecturas caducas, comandos rechazados y callbacks antiguos. Si no hay lectura, no mostrar un nivel inventado.
8) Probar arranque, suspensión/despertar, cierre temporal del launcher y revocación del permiso de superposición. Si falta un permiso, explicar una vez cómo activarlo, sin bucles.

Diseño:
- Cápsula vertical compacta derivada del volumen de IMG_0615.jpeg: base gris translúcida, parte blanca desde abajo y altavoz cian; borde y sombra compartidos con el centro de control.
- Posición lateral que no tape la navegación principal; tamaño legible en el estéreo. No ocupar todo el ancho ni abrir el centro completo al pulsar volumen.
- Puede mostrar el nivel nativo de forma discreta. No mostrar «confirmando volumen» ni textos técnicos fugaces.
- Entrada/salida suave y desaparición a los pocos segundos desde el último cambio, prolongada mientras se interactúa. La ventana debe ocupar sólo el área necesaria y no capturar toques fuera de ella.
- Si el centro de control ya está abierto, actualizar su deslizador y evitar un popup redundante. En reversa o llamada, respetar la prioridad visual y de audio de esa función.

La cápsula de audio global es nueva presentación; la barra inferior existente de brillo/volumen debe conservar su acceso por gesto y compartir estado. No es necesario abrir ambos controles completos cada vez que se pulsa volumen en una app externa.

Aceptación mínima: volumen del volante y controles físicos probado en Tablero, CarPlay incrustado, DVR, cajón y una app externa abierta de verdad. Comprobar indicación, valor y audio real, sin doble paso, sin popup nativo y sin robar foco ni reiniciar la app.

12. ARCHIVOS Y PUNTOS DE ENTRADA

Todas las clases Kotlin listadas viven en app/src/main/java/com/glasscar/launcher/:
- LauncherScreen.kt: distribución, pestañas, dock, QuickControlBar, volumeEvent, navegación por llamadas y conservación de páginas.
- ImmersiveControls.kt: franjas superior/inferior, temporizador, eventos de volumen; revisar geometría y conflictos de gestos.
- Glass.kt y Glyphs.kt: material, paleta, superficies e iconos compartidos.
- MainActivity.kt: lifecycle de audio/CAN, teclas, apertura externa, reversa y ajustes.
- TopwayAudio.kt: estado nativo, protocolo 0x203, luces 0x204 y confirmación de cambios.
- HeadUnitControls.kt: brillo y fallback Android/nativo.
- Platform.kt / MediaListenerService: ampliar notificaciones sin romper MediaSource, navegación y ProjectionCalls/ProjectionMedia.
- LauncherSheets.kt y LauncherPreferences.kt: configuración, filtros, selección de apps, opciones y diagnóstico.
- EmbeddedPanels.kt: cámara, CarPlay, mapa; fila externa de trasera que debe sustituirse por selector dentro del video.
- RearCameraPolicy.kt: apertura OEM actual; no equivale a panel integrado.
- RealAppPanel.kt, EmbeddedAppService.kt y EmbeddingPolicy.kt: tareas/displays, tacto, buffers, permisos USB, recuperación y allowlist.
- TopwayCarInfo.kt, CanBus.kt, HardwareDiagnostics.kt y CameraHardwareDiagnostics.kt: fuentes reales, estados y evidencia.
- app/src/main/AndroidManifest.xml y app/build.gradle.kts: permisos/componentes que requiera el alcance global, variante y versión.
- tools/overlay-tw-volume/: referencia histórica de supresión ya instalada; conservar.

Puedes separar componentes nuevos para centro de control, notificaciones, popup y servicio de estado. Sus nombres son decisiones de implementación; no finjas que ya existen. Evitar seguir creciendo una sola pantalla con todas las responsabilidades.

13. VERIFICACIÓN Y ENTREGA DEL SIGUIENTE CODEX

Primero conserva los comportamientos que ya funcionan. Después implementa estado global/audio y brillo, estructura visual/gestos, centros, y cámara/CAN según evidencia. Puedes desarrollar la UI mientras el hardware queda pendiente, pero debes distinguir ambos estados.

Pruebas automatizadas útiles, adicionales a las regresiones existentes:
- La misma lectura periódica de volumen no abre popup; un cambio sí; no duplicar eventos/ventanas; reconexión, caducidad y límite de volumen.
- Ruteo de popup entre capa local/global, temporizador durante interacción y revocación de permiso.
- Instantánea/actualización/baja de notificaciones, agrupación, descarte permitido y ocultación de persistentes sin detener servicios.
- Prioridad entre centro abierto, reversa, llamada y popup; no permitir toques que atraviesen paneles.
- Flancos de reversa, navegación única, ausencia de datos CAN y conservación de página/sesiones.
- Mostrar/ocultar controles o abrir centros conserva displays/tareas; revisar iniciosApp y movimientos además de inicios. inicios=1 por sí solo no demuestra que el watchdog no relanzó.
- Capturas de UI horizontales en modo día/noche, tamaños reales y fuente más grande. Verificar bordes, solapamientos y legibilidad.

Pruebas físicas pendientes y evidencia mínima:
- Brillo: cambio visible real a varios niveles, dentro y fuera de GlassCar.
- CAN: fuente, dato y vigencia por velocidad/marcha/luces/voltaje/reversa; cero estacionado no basta.
- Cámara: mayor área sin perder campo útil; selector dentro del video; disponibilidad manual de trasera; reversa real; clips DVR consecutivos de un minuto.
- CarPlay: iPhone conectado, pestañas, gestos sin cortina Android accidental y sesión conservada.
- Llamada: no empeorar el trabajo existente; popup/ringtone duplicado sigue pendiente de prueba real. No declarar que este video lo valida.
- Centro de control/notificaciones y popup global: acceso desde las ubicaciones prometidas, datos reales, cierre, filtros persistentes y ningún servicio interrumpido.

Compila con las herramientas existentes. Punto de partida documentado: JDK17, Android SDK34 y Gradle8.7; herramientas locales bajo ..\work\build-tools. El script tools/Compilar-4.6.ps1 verifica la versión 4.6: si incrementas versión, actualiza también esas comprobaciones o crea el script correspondiente para no generar una falsa falla ni una entrega mal nombrada.

Ejecuta pruebas, lint y assemble de la variante embedding que se entregará; toma como referencia las tareas existentes testDebugUnitTest, lintDebug y assembleEmbedding y el workflow. No afirmar que probaste una variante distinta. Actualiza versionCode/versionName de forma coherente con el estado actual del repo; propuesta 4.7 sólo si sigue siendo la siguiente disponible.

Al publicar, conservar la política de la rama de trabajo y no tocar main. Esperar el CI pertinente antes de declarar una entrega verificada. Si ya hay trabajo más reciente, no sobrescribirlo.

Verifica el APK por sus metadatos, no por su nombre: variante embedding, applicationId previsto com.glasscar.launcher.embedtest, versión, versionCode, firma y SHA-256. Verifica la identidad real instalada antes de planear una actualización. Las firmas CI4.5, CI4.6 y local documentadas son distintas; no desinstalar automáticamente para solucionar una incompatibilidad ni perder preferencias.

Entregar:
1) APK inequívoco de la variante correcta y procedencia local/CI, si este agente tiene el entorno necesario para compilar.
2) Resumen por requisito: implementado en software / verificado físicamente / pendiente o bloqueado, sin mezclar categorías.
3) Informe de revisión del video con intervalos, evidencia y acciones, manteniendo este documento como especificación de entrada.
4) Capturas comparables de centro de control, notificaciones y popup; resultados de pruebas y limitaciones reales.
5) LEEME con commit, run de CI cuando exista, variante, versión, firma, hash y pasos de actualización.
6) docs/CONTINUAR-CODEX.md actualizado, con los siguientes pasos concretos y sin conservar afirmaciones viejas como si fueran actuales.

No instales ni modifiques el estéreo desde un entorno sin conexión confirmada. Para cualquier intervención de sistema nueva, prepara primero el procedimiento de reversión específico. No flashear firmware, deshabilitar SystemUI completo, borrar HD Car DVR/Tlink ni cambiar parámetros CAN desconocidos para ocultar fallos. En Termux, seguir el método de scripts documentado en continuidad en vez de comandos largos con comillas problemáticas.

No marques todo como terminado porque el APK compila. El resultado que busco es una interfaz coherente, cámaras integradas, brillo y datos reales, centros rediseñados y un único popup de volumen que funcione también sobre otras apps.
