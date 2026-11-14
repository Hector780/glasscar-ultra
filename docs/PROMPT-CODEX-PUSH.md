# Prompt para Codex — compilar y subir lo que dejó Claude

Lee `docs/PENDIENTE-PUSH.md`. Revisa el diff sin commit en `C:\Users\hbernal\Documents\TS10_root\glasscar-ultra` (rama `work/topway-carinfo`; no toques `main`). No agregues funciones nuevas: tu trabajo es compilar, corregir lo mínimo y publicar.

1. Corre el script de compilación de la versión en curso (`tools\Compilar-4.7.ps1`; si no existe, `tools\Compilar-4.6.ps1` con sus comprobaciones de versión actualizadas). Debe correr `testDebugUnitTest`, `lintDebug` y `assembleEmbedding`. Si algo falla, corrígelo con el cambio mínimo y anota qué corregiste en `PENDIENTE-PUSH.md`.
2. Haz commit con el mensaje sugerido y push a `work/topway-carinfo`. Espera el CI de GitHub Actions y confirma que pasó.
3. Verifica el APK por sus metadatos, no por su nombre: `com.glasscar.launcher.embedtest`, versionCode, firma local y SHA-256. Cópialo a `TS10_root\INSTALAR_DESPUES` con un nombre inequívoco y su LEEME.
4. Pasa el contenido de `PENDIENTE-PUSH.md` al final de `docs/CONTINUAR-CODEX.md`, con el commit, el run de CI y el resultado, y deja `PENDIENTE-PUSH.md` vacío. Haz un commit de documentación con `[skip ci]`.

No modifiques el estéreo. No reconstruyas el overlay de volumen TW (módulo Magisk `glasscar_ts10`, confirmado). No pidas ni pegues tokens ni contraseñas.
