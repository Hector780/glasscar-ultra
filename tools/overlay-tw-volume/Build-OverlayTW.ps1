# GlassCar 4.6 - compila el overlay que oculta el popup de volumen de TWService.
# Uso:  powershell -ExecutionPolicy Bypass -File tools\overlay-tw-volume\Build-OverlayTW.ps1
$ErrorActionPreference = 'Stop'
$here = $PSScriptRoot
$root = (Resolve-Path "$here\..\..\..").Path
$sdk = "$root\work\build-tools\android-sdk"
$bt = (Get-ChildItem "$sdk\build-tools" -Directory | Sort-Object Name | Select-Object -Last 1).FullName
$aapt2 = "$bt\aapt2.exe"; $zipalign = "$bt\zipalign.exe"; $apksigner = "$bt\apksigner.bat"
$androidJar = "$sdk\platforms\android-34\android.jar"
$jdk = (Get-ChildItem "$root\work\build-tools\jdk" -Directory | Select-Object -First 1).FullName
$env:JAVA_HOME = $jdk
$out = "$here\build"
if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory -Force $out | Out-Null

& $aapt2 compile --dir "$here\res" -o "$out\res.zip"
if ($LASTEXITCODE -ne 0) { throw 'aapt2 compile fallo' }
& $aapt2 link -o "$out\unsigned.apk" -I $androidJar --manifest "$here\AndroidManifest.xml" `
    --stable-ids "$here\stable-ids.txt" --min-sdk-version 29 --target-sdk-version 29 "$out\res.zip"
if ($LASTEXITCODE -ne 0) { throw 'aapt2 link fallo' }

# Seguridad: TWService hace findViewById con estos numeros exactos. Si no coinciden, NO se entrega el APK.
$dump = (& $aapt2 dump resources "$out\unsigned.apk") -join "`n"
$expected = @{ 'id/level' = '0x7f06005a'; 'id/message' = '0x7f06005e'; 'id/other_stream_icon' = '0x7f060064' }
foreach ($name in $expected.Keys) {
    $id = $expected[$name]
    if ($dump -notmatch "resource $id $([regex]::Escape($name))\b") {
        Write-Host $dump
        throw "El ID de $name no es $id. No instales este overlay."
    }
    Write-Host "OK $name = $id" -ForegroundColor Green
}

& $zipalign -f -p 4 "$out\unsigned.apk" "$out\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw 'zipalign fallo' }
$ks = "$env:USERPROFILE\.android\debug.keystore"
if (-not (Test-Path $ks)) {
    New-Item -ItemType Directory -Force (Split-Path $ks) | Out-Null
    & "$jdk\bin\keytool.exe" -genkeypair -keystore $ks -storepass android -keypass android -alias androiddebugkey `
        -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Android Debug,O=Android,C=US'
}
& $apksigner sign --ks $ks --ks-pass pass:android --key-pass pass:android --out "$out\GlassCarTWVolume.apk" "$out\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw 'apksigner fallo' }

$dest = "$root\INSTALAR_DESPUES"
New-Item -ItemType Directory -Force $dest | Out-Null
Copy-Item "$out\GlassCarTWVolume.apk", "$here\instalar_overlay_tw.sh", "$here\quitar_overlay_tw.sh" -Destination $dest -Force
Write-Host "Listo en $dest : GlassCarTWVolume.apk + instalar_overlay_tw.sh + quitar_overlay_tw.sh" -ForegroundColor Green
