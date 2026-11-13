#!/system/bin/sh
# GlassCar 4.6 - revierte por completo el overlay del popup de volumen TW.
# Uso en Termux:  su -c sh /storage/XXXX-XXXX/quitar_overlay_tw.sh
PKG=com.glasscar.overlay.twvolume
cmd overlay disable --user 0 "$PKG" 2>/dev/null
pm uninstall "$PKG" 2>/dev/null
[ -d /data/adb/modules/glasscar_twvolume ] && touch /data/adb/modules/glasscar_twvolume/remove
kill -9 $(pidof com.tw.service) 2>/dev/null
echo "Overlay desactivado. Si usaste el modulo Magisk, reinicia para borrarlo."
