# GlassCar 4.7.1 - compila, prueba y deja el APK en TS10_root\INSTALAR_DESPUES
# Uso (PowerShell, cualquier carpeta):
#   powershell -ExecutionPolicy Bypass -File C:\Users\hbernal\Documents\TS10_root\glasscar-ultra\tools\Compilar-4.7.1.ps1
param([switch]$BuildOverlay, [switch]$OpenOutput)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path "$PSScriptRoot\..").Path
$root = (Resolve-Path "$repo\..").Path
$env:JAVA_HOME = (Get-ChildItem "$root\work\build-tools\jdk" -Directory | Select-Object -First 1).FullName
$env:ANDROID_HOME = "$root\work\build-tools\android-sdk"
$gradle = "$root\work\build-tools\gradle-8.7\bin\gradle.bat"
$log = "$root\work\glasscar-4.7.1-build.log"
Push-Location $repo
try {
    Write-Host 'Compilando y probando (tarda varios minutos)...' -ForegroundColor Yellow
    & $gradle --offline --no-watch-fs --no-daemon --max-workers=2 testDebugUnitTest lintDebug assembleEmbedding *> $log
    if ($LASTEXITCODE -ne 0) {
        Get-Content $log -Tail 60
        throw "La compilacion o las pruebas fallaron. Log completo: $log"
    }
    $apk = "$repo\app\build\outputs\apk\embedding\app-embedding.apk"
    $metadata = Get-Content "$repo/app/build/outputs/apk/embedding/output-metadata.json" -Raw | ConvertFrom-Json
    $element = $metadata.elements[0]
    if ($metadata.applicationId -ne 'com.glasscar.launcher.embedtest' -or $element.versionCode -ne 12 -or
        $element.versionName -ne '4.7.1-ts10-integrated-real-app-prototype') {
        throw "El resultado no es la variante embedding 4.7.1 esperada: $($metadata.applicationId) code $($element.versionCode) $($element.versionName)"
    }
    $signer = & "$env:ANDROID_HOME/build-tools/34.0.0/apksigner.bat" verify --print-certs $apk
    if ($LASTEXITCODE -ne 0) { throw 'La firma del APK no es valida.' }
    $dest = "$root\INSTALAR_DESPUES"
    New-Item -ItemType Directory -Force $dest | Out-Null
    $final = "$dest\GlassCar-Ultra-4.7.1-local-embedding.apk"
    Copy-Item $apk $final -Force
    $sha = (Get-FileHash $final -Algorithm SHA256).Hash
    $tests = Select-String -Path $log -Pattern 'tests completed|BUILD SUCCESSFUL' | ForEach-Object { $_.Line }
    @"
GlassCar Ultra 4.7.1 (versionCode 12 - 4.7.1-ts10-integrated)
Paquete: com.glasscar.launcher.embedtest
Compilado localmente: $(Get-Date -Format o)
Commit base: $((git rev-parse HEAD).Trim())
Cambios sin commit: $([bool](git status --porcelain))
APK: GlassCar-Ultra-4.7.1-local-embedding.apk
SHA-256: $sha
Resultado: $($tests -join ' | ')
Log: $log
Firma: $($signer -join "`n")
"@ | Set-Content "$dest\LEEME-4.7.1-local.txt" -Encoding UTF8
    Write-Host "APK 4.7.1 listo: $final" -ForegroundColor Green
    Write-Host "SHA-256: $sha" -ForegroundColor Cyan
} finally { Pop-Location }

# The installed TW overlay is confirmed working; rebuilding it is a separate explicit action.
if ($BuildOverlay) { & "$repo\tools\overlay-tw-volume\Build-OverlayTW.ps1" }
if ($OpenOutput) { explorer.exe "$root\INSTALAR_DESPUES" }
