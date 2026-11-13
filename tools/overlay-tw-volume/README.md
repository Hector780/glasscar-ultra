# Overlay TW confirmado

El usuario confirmó eliminado el popup tras instalar el overlay mediante el módulo combinado **glasscar_ts10** y reiniciar el proceso persistente TW con `kill -9`. Esta revisión conserva los recursos y scripts; no vuelve a construir ni instalar el overlay por defecto.

Los scripts standalone de esta carpeta usan el módulo **glasscar_twvolume**. No confundirlo con el módulo combinado `glasscar_ts10` instalado mediante `INSTALAR_DESPUES/glasscar-termux-4.6.txt`, que también incluye el vigilante de llamadas. `quitar_overlay_tw.sh` no elimina ese módulo combinado. No ejecutar los instaladores standalone sobre la solución confirmada como paso de actualización del launcher.

La prueba de llamadas del vigilante continúa pendiente. El APK del launcher se actualiza por separado; no requiere reiniciar TW ni reactivar el overlay. Ver `docs/VERIFICACION-4.6.md` y `docs/CONTINUAR-CODEX.md`.
