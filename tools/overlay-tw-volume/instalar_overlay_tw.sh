#!/system/bin/sh
# GlassCar 4.6 - oculta el popup de volumen de TWService con un overlay de recursos (reversible).
# Uso en Termux del estereo:  su -c sh /storage/XXXX-XXXX/instalar_overlay_tw.sh
PKG=com.glasscar.overlay.twvolume
DIR="$(cd "$(dirname "$0")" && pwd)"
APK="$DIR/GlassCarTWVolume.apk"
MOD=/data/adb/modules/glasscar_twvolume

tw_stable() {
  # Espera a que TWService exista y confirma que no se reinicia (crash loop) durante 10 s.
  i=0; while [ -z "$(pidof com.tw.service)" ] && [ $i -lt 25 ]; do sleep 1; i=$((i+1)); done
  p1=$(pidof com.tw.service); sleep 10; p2=$(pidof com.tw.service)
  [ -n "$p1" ] && [ "$p1" = "$p2" ]
}

main() {
  echo "== GlassCar overlay TW $(date)"
  [ "$(id -u)" = 0 ] || { echo "ERROR: ejecuta con su -c"; return 1; }
  VER=$(dumpsys package com.tw.service | grep -m1 versionName | sed 's/.*versionName=//')
  echo "com.tw.service version: $VER"
  [ "$VER" = "20210731" ] || { echo "ERROR: version distinta a la analizada (20210731). No se instala."; return 1; }

  if ! cmd overlay list com.tw.service | grep -q "$PKG"; then
    [ -f "$APK" ] || { echo "ERROR: falta $APK"; return 1; }
    cp "$APK" /data/local/tmp/gc_twvol.apk && pm install -r /data/local/tmp/gc_twvol.apk
    rm -f /data/local/tmp/gc_twvol.apk
  fi
  cmd overlay enable --user 0 "$PKG"
  sleep 1
  STATE=$(cmd overlay list com.tw.service | grep "$PKG")
  echo "Estado: $STATE"

  case "$STATE" in
    *"[x]"*)
      echo "Overlay activo. Reiniciando TWService para aplicarlo..."
      kill -9 $(pidof com.tw.service) 2>/dev/null
      if tw_stable; then
        echo "OK: TWService estable. Sube/baja volumen: ya no debe aparecer el popup de TW."
      else
        echo "FALLO: TWService inestable. Revirtiendo..."
        cmd overlay disable --user 0 "$PKG"; kill -9 $(pidof com.tw.service) 2>/dev/null
        [ -d "$MOD" ] && touch "$MOD/disable"
        tw_stable && echo "Revertido: TWService estable de nuevo." || echo "Reinicia el estereo."
        return 1
      fi ;;
    *)
      echo "Android no acepto el overlay desde /data. Intentando como modulo Magisk (preinstalado)..."
      [ -d /data/adb/modules ] || { echo "ERROR: no hay Magisk (/data/adb/modules). No se cambio nada mas."; pm uninstall "$PKG" >/dev/null 2>&1; return 1; }
      pm uninstall "$PKG" >/dev/null 2>&1
      mkdir -p "$MOD/system/vendor/overlay"
      cp "$APK" "$MOD/system/vendor/overlay/GlassCarTWVolume.apk"
      chmod 644 "$MOD/system/vendor/overlay/GlassCarTWVolume.apk"
      printf 'id=glasscar_twvolume\nname=GlassCar sin popup volumen TW\nversion=1\nversionCode=1\nauthor=GlassCar\ndescription=Overlay RRO de layout/volume_adjust de com.tw.service 20210731\n' > "$MOD/module.prop"
      cat > "$MOD/service.sh" <<'SVC'
#!/system/bin/sh
until [ "$(getprop sys.boot_completed)" = 1 ]; do sleep 2; done
sleep 20
cmd overlay enable --user 0 com.glasscar.overlay.twvolume
kill -9 $(pidof com.tw.service) 2>/dev/null
sleep 25
p1=$(pidof com.tw.service); sleep 10; p2=$(pidof com.tw.service)
if [ -z "$p2" ] || [ "$p1" != "$p2" ]; then
  cmd overlay disable --user 0 com.glasscar.overlay.twvolume
  kill -9 $(pidof com.tw.service) 2>/dev/null
  touch /data/adb/modules/glasscar_twvolume/disable
fi
SVC
      chmod 755 "$MOD/service.sh"
      echo "Modulo instalado. REINICIA el estereo; al arrancar se activa solo y se auto-desactiva si TWService falla." ;;
  esac
}
main 2>&1 | tee "$DIR/overlay_tw_log.txt"
