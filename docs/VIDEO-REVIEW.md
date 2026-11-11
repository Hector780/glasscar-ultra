# Revisión del video · versión 4.0

Fuente: `IMG_0419.mp4`, 06:44. Los números corresponden al checklist original. «Implementado» describe el código y no implica una prueba en el automóvil.

| # | Solicitud | Resultado |
| --- | --- | --- |
| 01 | Cambiar apps asociadas | Selector persistente por acceso; elección coherente entre paneles y accesos principales. |
| 02 | Apple Music por defecto | Prioridad por paquete respetando el orden, panel y acceso unidos a la misma preferencia. |
| 03 | Teléfono sin FaceTime | Icono vectorial de auricular, destino conservado/configurable. |
| 04 | Bluetooth correcto | Símbolo Bluetooth específico. |
| 05 | Maps día/noche | Una misma identidad vectorial en ambos temas. |
| 06 | File manager | Icono de carpeta/Archivos. |
| 07 | Google sin clima | Usa el icono real de la app instalada. |
| 08 | DoFun Play | Icono de personalización/temas. |
| 09 | Coherencia de iconos | Reglas compartidas entre accesos y cajón; sin asociaciones meteorológicas falsas. |
| 10 | Fuente de temperatura | Fuente Open-Meteo, coordenadas, hora de lectura y consulta visibles; errores y antigüedad explícitos. |
| 11 | Datos reales de clima | Temperatura, humedad, viento y condición del servicio; sin ceros inventados ante fallos. |
| 12 | Clima responde al toque | Diálogo con detalle, actualización y apertura de app elegida. |
| 13 | App de clima dedicada | Asignación opcional y búsqueda en Play Store; no instala nada automáticamente. |
| 14 | Sustituir Waze | Google Maps prioritario y selección libre. |
| 15 | Mapa en vivo integrado | Mapa GPS OSM funcional sin clave; SDK Google opcional con clave. No se implementa un motor de rutas ni se incrusta la app Google Maps. |
| 16 | Redistribuir paneles | Mapa principal, velocidad compacta, música mayor y panel inferior de clima/accesos. Instrumentos grandes en otra página. |
| 17 | Miniatura cámara frontal | Panel intercambiable en tablero y página de cámara: Camera2 o widget. Depende de exposición de cámara por el fabricante. |
| 18 | Cámara abre AUX | AUX excluido de detección; selector de app DVR/cámara. |
| 19 | Accesos personalizables | Dos páginas con cuatro accesos cada una; pulsación larga para asignar. |
| 20 | Iconos mayores | Tamaño adaptable y cajón con iconos de 64 dp. |
| 21 | Brillo real | Ajuste del sistema con permiso, lectura actual y mínimo 12 %. Validación física pendiente en firmware del estéreo. |
| 22 | Un solo volumen | Parcial: acceso al control nativo en unidades MCU evita mostrar una barra Android como si fuera la del amplificador. Sin API del fabricante no hay barra MCU sincronizada. |
| 23 | Música dinámica | Carátula, progreso y vista de letras de LRCLIB, si existen. |
| 24 | Eliminar salto vacío | Pausar la actividad no vacía los metadatos; reconexión de sesiones prioriza la activa. |
| 25 | Apps recientes | Historial de lanzamientos persistente, complementado por estadísticas de uso con permiso opcional. |
| 26 | Indicadores legibles | Iconos mayores y detalle de Wi-Fi, Bluetooth y GPS. Bluetooth encendido se distingue de una conexión no comprobada. |
| 27 | Qué significa 12 V | Eliminado valor fijo; voltaje CAN con fuente y caducidad o «sin lectura». |
| 28 | Página Tlink5 | Página dedicada con apertura de app o widget disponible. La app externa no queda incrustada si no proporciona widget. |
| 29 | Páginas horizontales | Tablero, CarPlay, Cámara e Instrumentos con pestañas y deslizamiento. |
| 30 | Gesto vertical | Arriba o abajo sobre el fondo abre el cajón; los componentes interactivos conservan sus gestos. |

## Comprobación en el estéreo

Con el vehículo estacionado: confirmar asignaciones (Apple Music, teléfono, Maps, DVR y Tlink5); conceder ubicación, notificaciones y permisos opcionales necesarios; reproducir/pausar y regresar al tablero; mover brillo y contrastarlo con ajustes nativos; comprobar el control real del volumen; elegir cámara; conectar el iPhone y recorrer páginas. Probar día/noche, reinicio y pérdida de GPS/conexión. Para completar la integración propietaria se necesita el modelo/firmware y la interfaz de cámara/MCU que expone el fabricante.
