param(
    [string]$Adb = "$PSScriptRoot/../../work/build-tools/android-sdk/platform-tools/adb.exe",
    [ValidateSet('Volume', 'Call', 'Dvr', 'Can')][string]$Event = 'Volume',
    [string]$Device = '',
    [string]$Output = "$PSScriptRoot/../../work/ts10-capture.txt"
)
$ErrorActionPreference = 'Stop'
$Adb = (Resolve-Path -LiteralPath $Adb).Path
$connected = @(& $Adb devices | Select-String '^([^\s]+)\s+device$' | ForEach-Object { $_.Matches[0].Groups[1].Value })
if ($Device) {
    if ($Device -notin $connected) { throw 'El dispositivo indicado no está conectado y autorizado.' }
} elseif ($connected.Count -eq 1) { $Device = $connected[0] }
else { throw 'Conecta y autoriza un único estéreo por ADB, o indica -Device. No se cambió el equipo.' }

# Read only. Do not save notification bodies, phone numbers, serials or full window dumps.
$report = [System.Collections.Generic.List[string]]::new()
$report.Add("GlassCar TS10 · evento $Event · $(Get-Date -Format o)")
foreach ($property in @('ro.build.version.sdk', 'ro.hardware', 'ro.build.display.id')) {
    $value = & $Adb -s $Device shell getprop $property
    $report.Add("$property=$value")
}
foreach ($package in @('com.glasscar.launcher.embedtest', 'com.zjinnova.zlink', 'com.williexing.android.apps.xcdvr1', 'com.tw.carinfoservice', 'com.android.systemui')) {
    $report.Add($package)
    $details = & $Adb -s $Device shell dumpsys package $package
    foreach ($line in ($details | Select-String '^\s*(versionCode=|versionName=)')) { $report.Add($line.Line.Trim()) }
}
Write-Host "Durante los próximos 12 segundos reproduce el evento $Event con el vehículo estacionado."
for ($sample = 0; $sample -lt 12; $sample++) {
    $report.Add("muestra=$sample")
    $windows = (& $Adb -s $Device shell dumpsys window windows) -join "`n"
    $components = [regex]::Matches($windows, '\b(?:[A-Za-z][\w]*\.){2,}[A-Za-z][\w]*(?:/[\w.$]+)?') |
        ForEach-Object { $_.Value } | Sort-Object -Unique
    foreach ($component in $components) { $report.Add("ventana=$component") }
    $tasks = (& $Adb -s $Device shell dumpsys activity activities) -join "`n"
    foreach ($line in ($tasks -split "`n" | Select-String 'mResumedActivity|topResumedActivity')) {
        foreach ($component in [regex]::Matches($line.Line, '\b(?:[A-Za-z][\w]*\.){2,}[A-Za-z][\w]*/[\w.$]+')) {
            $report.Add("actividad=$($component.Value)")
        }
    }
    Start-Sleep -Milliseconds 700
}
$report | Set-Content -LiteralPath $Output -Encoding utf8
Write-Host "Reporte guardado: $Output"
Write-Host 'Añade el texto de Diagnóstico del estéreo de GlassCar para comparar CAN, inicios de sesión y llamadas.'
