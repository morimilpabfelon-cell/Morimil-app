param(
    [Parameter(Mandatory = $true)]
    [string]$ArtifactPath,

    [string]$Serial,

    [string]$ReportDirectory,

    [switch]$SkipBuild
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$ExpectedFilename = "morimil-deliberative-v0.2.candidate.litertlm"
$ExpectedSha256 = "2ed7bc3a0026c93d5b8a4544b352d9d00cd66ff0bac3ef6a20ac3d2cba4010d6"
$ExpectedSizeBytes = [Int64]3655827456
$ExpectedReportSchema = "morimil.android-arm64-candidate-runtime.v0"
$RequiredAbi = "arm64-v8a"
$TargetPackage = "com.morimil.app"
$TestPackage = "com.morimil.app.test"
$Runner = "androidx.test.runner.AndroidJUnitRunner"
$HarnessClass = "com.morimil.app.reasoning.intrinsic.Gemma3nE2bArm64CandidateHarnessV0Test"
$DeviceRoot = "files/morimil-arm64-harness"
$DeviceModel = "$DeviceRoot/input/$ExpectedFilename"
$DeviceReport = "$DeviceRoot/output/morimil-arm64-candidate-runtime-v0.json"
$MinimumFreeBytes = ([Int64]$ExpectedSizeBytes * [Int64]2) + ([Int64]2 * 1024 * 1024 * 1024)
$MinimumMemoryKilobytes = [Int64]6 * 1024 * 1024

function Stop-Work {
    param([Parameter(Mandatory = $true)][string]$Message)
    throw "Detenido: $Message"
}

function Invoke-External {
    param(
        [Parameter(Mandatory = $true)][string]$Step,
        [Parameter(Mandatory = $true)][string]$FilePath,
        [Parameter(Mandatory = $true)][string[]]$CommandArguments,
        [switch]$AllowFailure
    )

    Write-Host ""
    Write-Host "==> $Step"
    $rawOutput = @(& $FilePath @CommandArguments 2>&1 | ForEach-Object { "$_" })
    $exitCode = $LASTEXITCODE
    $rawOutput | ForEach-Object { Write-Host $_ }
    if (-not $AllowFailure -and $exitCode -ne 0) {
        Stop-Work "fallo en '$Step' (codigo $exitCode)"
    }
    return [pscustomobject]@{
        ExitCode = $exitCode
        Lines = $rawOutput
        Text = ($rawOutput -join "`n")
    }
}

function Invoke-Adb {
    param(
        [Parameter(Mandatory = $true)][string]$Step,
        [Parameter(Mandatory = $true)][string[]]$CommandArguments,
        [switch]$AllowFailure
    )

    $allArguments = @()
    if (-not [string]::IsNullOrWhiteSpace($script:DeviceSerial)) {
        $allArguments += @("-s", $script:DeviceSerial)
    }
    $allArguments += $CommandArguments
    return Invoke-External `
        -Step $Step `
        -FilePath $script:AdbPath `
        -CommandArguments $allArguments `
        -AllowFailure:$AllowFailure
}

function Get-LowerSha256 {
    param([Parameter(Mandatory = $true)][string]$Path)
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Get-OnlyDeviceSerial {
    param([Parameter(Mandatory = $true)][string]$AdbPath)

    $result = Invoke-External `
        -Step "Enumerar dispositivos adb" `
        -FilePath $AdbPath `
        -CommandArguments @("devices")
    $devices = @(
        $result.Lines |
            Where-Object { $_ -match "^([^\s]+)\s+device$" } |
            ForEach-Object { [regex]::Match($_, "^([^\s]+)").Groups[1].Value }
    )
    if ($devices.Count -ne 1) {
        Stop-Work "se necesita exactamente un dispositivo adb en estado device; encontrados: $($devices.Count)"
    }
    return $devices[0]
}

function Get-FirstToken {
    param([Parameter(Mandatory = $true)][string]$Text)

    $trimmed = $Text.Trim()
    if ([string]::IsNullOrWhiteSpace($trimmed)) {
        Stop-Work "la salida esperada esta vacia"
    }
    return ($trimmed -split "\s+")[0]
}

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
$resolvedArtifact = [System.IO.Path]::GetFullPath($ArtifactPath)
if (-not (Test-Path -LiteralPath $resolvedArtifact -PathType Leaf)) {
    Stop-Work "el artefacto no existe: $resolvedArtifact"
}

$artifactItem = Get-Item -LiteralPath $resolvedArtifact -Force
if (($artifactItem.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
    Stop-Work "el artefacto local no puede ser enlace o reparse point"
}
if ($artifactItem.Name -cne $ExpectedFilename) {
    Stop-Work "nombre de artefacto inesperado: $($artifactItem.Name)"
}
if ($artifactItem.Length -ne $ExpectedSizeBytes) {
    Stop-Work "tamano local inesperado: $($artifactItem.Length)"
}

Write-Host "==> Verificar dos veces el artefacto local exacto"
$localHashFirst = Get-LowerSha256 -Path $resolvedArtifact
$localHashSecond = Get-LowerSha256 -Path $resolvedArtifact
if ($localHashFirst -cne $localHashSecond) {
    Stop-Work "el hash local no fue estable"
}
if ($localHashFirst -cne $ExpectedSha256) {
    Stop-Work "SHA-256 local inesperado: $localHashFirst"
}

$adbCommand = Get-Command adb -ErrorAction SilentlyContinue
if ($null -eq $adbCommand) {
    Stop-Work "adb no esta instalado o no esta en PATH"
}
$script:AdbPath = $adbCommand.Source
$script:DeviceSerial = if ([string]::IsNullOrWhiteSpace($Serial)) {
    Get-OnlyDeviceSerial -AdbPath $script:AdbPath
} else {
    $Serial.Trim()
}

Invoke-Adb -Step "Confirmar estado del dispositivo" -CommandArguments @("get-state") | Out-Null
$abiResult = Invoke-Adb -Step "Comprobar ABI del dispositivo" -CommandArguments @(
    "shell",
    "getprop ro.product.cpu.abilist"
)
$abiList = $abiResult.Text.Trim()
if (($abiList -split ",") -notcontains $RequiredAbi) {
    Stop-Work "el dispositivo no declara $RequiredAbi; ABI: $abiList"
}

$qemuResult = Invoke-Adb -Step "Rechazar emulador" -CommandArguments @(
    "shell",
    "getprop ro.kernel.qemu"
)
if ($qemuResult.Text.Trim() -eq "1") {
    Stop-Work "este harness requiere un telefono o tableta fisica arm64"
}

$memoryResult = Invoke-Adb -Step "Leer memoria fisica" -CommandArguments @(
    "shell",
    "cat /proc/meminfo | head -n 1"
)
if ($memoryResult.Text -notmatch "MemTotal:\s+(\d+)\s+kB") {
    Stop-Work "no se pudo leer MemTotal"
}
$memoryKilobytes = [Int64]$Matches[1]
if ($memoryKilobytes -lt $MinimumMemoryKilobytes) {
    Stop-Work "el dispositivo declara menos de 6 GiB de RAM; MemTotal=$memoryKilobytes kB"
}

$diskResult = Invoke-Adb -Step "Comprobar espacio libre en /data" -CommandArguments @(
    "shell",
    "df -k /data"
)
$dataLine = @($diskResult.Lines | Where-Object { $_ -match "\s/data$" } | Select-Object -Last 1)
if ($dataLine.Count -ne 1) {
    Stop-Work "no se pudo interpretar df -k /data"
}
$columns = @($dataLine[0].Trim() -split "\s+")
if ($columns.Count -lt 4) {
    Stop-Work "salida df inesperada: $($dataLine[0])"
}
$availableBytes = [Int64]$columns[3] * [Int64]1024
if ($availableBytes -lt $MinimumFreeBytes) {
    Stop-Work "espacio insuficiente en /data; disponibles=$availableBytes requeridos=$MinimumFreeBytes"
}

$debugApk = Join-Path $repoRoot "app\build\outputs\apk\debug\app-debug.apk"
$testApk = Join-Path $repoRoot "app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk"
if (-not $SkipBuild) {
    $gradleWrapper = Join-Path $repoRoot "gradlew.bat"
    if (-not (Test-Path -LiteralPath $gradleWrapper -PathType Leaf)) {
        Stop-Work "falta gradlew.bat en $repoRoot"
    }
    Push-Location $repoRoot
    try {
        Invoke-External `
            -Step "Compilar APK debug y androidTest" `
            -FilePath $gradleWrapper `
            -CommandArguments @(
                ":app:assembleDebug",
                ":app:assembleDebugAndroidTest"
            ) | Out-Null
    } finally {
        Pop-Location
    }
}
if (-not (Test-Path -LiteralPath $debugApk -PathType Leaf)) {
    Stop-Work "falta APK debug: $debugApk"
}
if (-not (Test-Path -LiteralPath $testApk -PathType Leaf)) {
    Stop-Work "falta APK androidTest: $testApk"
}

Invoke-Adb -Step "Instalar APK debug de Morimil" -CommandArguments @(
    "install",
    "-r",
    "-t",
    $debugApk
) | Out-Null
Invoke-Adb -Step "Instalar APK de instrumentacion" -CommandArguments @(
    "install",
    "-r",
    "-t",
    $testApk
) | Out-Null

$runStamp = Get-Date -Format "yyyyMMdd-HHmmss"
if ([string]::IsNullOrWhiteSpace($ReportDirectory)) {
    $ReportDirectory = Join-Path $repoRoot "build\morimil-arm64-harness-reports"
}
$resolvedReportDirectory = [System.IO.Path]::GetFullPath($ReportDirectory)
$hostRunDirectory = Join-Path $resolvedReportDirectory $runStamp
New-Item -ItemType Directory -Path $hostRunDirectory -Force | Out-Null
$hostReportPath = Join-Path $hostRunDirectory "morimil-arm64-candidate-runtime-v0.json"
$hostTranscriptPath = Join-Path $hostRunDirectory "instrumentation-output.txt"
$remoteTemporary = "/data/local/tmp/$ExpectedFilename.$([guid]::NewGuid().ToString('N')).partial"

try {
    Invoke-Adb -Step "Subir candidato a staging shell temporal" -CommandArguments @(
        "push",
        $resolvedArtifact,
        $remoteTemporary
    ) | Out-Null

    $remoteSizeResult = Invoke-Adb -Step "Verificar tamano del staging shell" -CommandArguments @(
        "shell",
        "wc -c < '$remoteTemporary'"
    )
    $remoteSize = [Int64](Get-FirstToken -Text $remoteSizeResult.Text)
    if ($remoteSize -ne $ExpectedSizeBytes) {
        Stop-Work "tamano remoto inesperado: $remoteSize"
    }

    $remoteHashResult = Invoke-Adb -Step "Verificar SHA-256 del staging shell" -CommandArguments @(
        "shell",
        "sha256sum '$remoteTemporary'"
    )
    $remoteHash = (Get-FirstToken -Text $remoteHashResult.Text).ToLowerInvariant()
    if ($remoteHash -cne $ExpectedSha256) {
        Stop-Work "SHA-256 remoto inesperado: $remoteHash"
    }

    Invoke-Adb -Step "Preparar staging privado del APK" -CommandArguments @(
        "shell",
        "run-as $TargetPackage sh -c 'rm -rf $DeviceRoot && mkdir -p $DeviceRoot/input $DeviceRoot/output'"
    ) | Out-Null

    Invoke-Adb -Step "Copiar candidato al staging privado y volverlo solo lectura" -CommandArguments @(
        "shell",
        "cat '$remoteTemporary' | run-as $TargetPackage sh -c 'cat > $DeviceModel.partial && mv $DeviceModel.partial $DeviceModel && chmod 400 $DeviceModel'"
    ) | Out-Null

    $privateSizeResult = Invoke-Adb -Step "Verificar tamano del staging privado" -CommandArguments @(
        "shell",
        "run-as $TargetPackage sh -c 'test -r $DeviceModel && test ! -w $DeviceModel && wc -c < $DeviceModel'"
    )
    $privateSize = [Int64](Get-FirstToken -Text $privateSizeResult.Text)
    if ($privateSize -ne $ExpectedSizeBytes) {
        Stop-Work "tamano privado inesperado: $privateSize"
    }

    $privateHashResult = Invoke-Adb -Step "Verificar SHA-256 del staging privado" -CommandArguments @(
        "shell",
        "run-as $TargetPackage sha256sum $DeviceModel"
    )
    $privateHash = (Get-FirstToken -Text $privateHashResult.Text).ToLowerInvariant()
    if ($privateHash -cne $ExpectedSha256) {
        Stop-Work "SHA-256 privado inesperado: $privateHash"
    }

    $instrumentation = Invoke-Adb `
        -Step "Ejecutar inferencia LiteRT-LM CPU en arm64" `
        -CommandArguments @(
            "shell",
            "am instrument -w -r -e morimilArm64HarnessEnabled true -e class $HarnessClass $TestPackage/$Runner"
        ) `
        -AllowFailure
    [System.IO.File]::WriteAllText(
        $hostTranscriptPath,
        $instrumentation.Text + "`n",
        [System.Text.UTF8Encoding]::new($false)
    )
    if ($instrumentation.ExitCode -ne 0) {
        Stop-Work "adb devolvio codigo $($instrumentation.ExitCode) durante instrumentacion"
    }
    if ($instrumentation.Text -match "FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=") {
        Stop-Work "la instrumentacion Android informo un fallo"
    }
    if ($instrumentation.Text -notmatch "OK \(2 tests\)") {
        Stop-Work "la instrumentacion no confirmo dos tests correctos"
    }

    $reportResult = Invoke-Adb -Step "Extraer informe JSON del proceso Android" -CommandArguments @(
        "shell",
        "run-as $TargetPackage cat $DeviceReport"
    )
    $reportText = $reportResult.Text.Trim()
    if ([string]::IsNullOrWhiteSpace($reportText)) {
        Stop-Work "el informe Android esta vacio"
    }
    [System.IO.File]::WriteAllText(
        $hostReportPath,
        $reportText + "`n",
        [System.Text.UTF8Encoding]::new($false)
    )

    $report = $reportText | ConvertFrom-Json
    if ([string]$report.schemaVersion -cne $ExpectedReportSchema) {
        Stop-Work "esquema de informe inesperado"
    }
    if ([string]$report.status -cne "passed") {
        Stop-Work "el harness Android no termino en estado passed"
    }
    if ([string]$report.expectedArtifactSha256 -cne "sha256:$ExpectedSha256") {
        Stop-Work "el informe declara otro hash esperado"
    }
    if ([string]$report.hashBeforeFirst -cne "sha256:$ExpectedSha256" -or
        [string]$report.hashAfter -cne "sha256:$ExpectedSha256") {
        Stop-Work "el informe no confirma el hash exacto antes y despues"
    }
    if ($report.hashStable -ne $true) {
        Stop-Work "el informe no confirma estabilidad del artefacto"
    }
    if ($report.process64Bit -ne $true -or [string]$report.requiredAbi -cne $RequiredAbi) {
        Stop-Work "el informe no confirma proceso arm64 de 64 bits"
    }
    if ($report.strictOutputPassed -ne $true) {
        Stop-Work "la salida estricta de humo no fue confirmada"
    }
    if ($report.engineInitialized -ne $true -or
        $report.engineClosed -ne $true -or
        $report.conversationClosed -ne $true) {
        Stop-Work "los recursos LiteRT-LM no quedaron confirmados como cerrados"
    }
    if ($report.certified -ne $false -or
        $report.signed -ne $false -or
        $report.installed -ne $false -or
        $report.promotionAllowed -ne $false -or
        $report.productionAuthorization -ne $false) {
        Stop-Work "el informe no conserva el estado fail-closed"
    }

    Write-Host ""
    Write-Host "HARNESS ANDROID ARM64 V0 COMPLETADO."
    Write-Host "Dispositivo:          $($report.manufacturer) $($report.model)"
    Write-Host "Android API:          $($report.sdkInt)"
    Write-Host "ABI requerida:        $($report.requiredAbi)"
    Write-Host "Carga LiteRT-LM:      $($report.loadMilliseconds) ms"
    Write-Host "Inferencia:           $($report.inferenceMilliseconds) ms"
    Write-Host "Respuesta:            $($report.responseText)"
    Write-Host "Hash estable:         $($report.hashStable)"
    Write-Host "Informe JSON:         $hostReportPath"
    Write-Host "Transcripcion:        $hostTranscriptPath"
    Write-Host ""
    Write-Host "NO certificado, NO firmado, NO instalado y promocion bloqueada."
} finally {
    Invoke-Adb `
        -Step "Eliminar staging shell temporal" `
        -CommandArguments @("shell", "rm -f '$remoteTemporary'") `
        -AllowFailure | Out-Null
    Invoke-Adb `
        -Step "Eliminar staging privado del APK" `
        -CommandArguments @("shell", "run-as $TargetPackage rm -rf $DeviceRoot") `
        -AllowFailure | Out-Null
}
