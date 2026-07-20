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
$ExpectedReportSchema = "morimil.android-arm64-sustained-profile.v0"
$RequiredAbi = "arm64-v8a"
$TargetPackage = "com.morimil.app"
$TestPackage = "com.morimil.app.test"
$Runner = "androidx.test.runner.AndroidJUnitRunner"
$HarnessClass = "com.morimil.app.reasoning.intrinsic.Gemma3nE2bArm64SustainedProfileV0Test"
$EnableArgument = "morimilArm64SustainedProfileEnabled"
$DeviceRoot = "files/morimil-arm64-sustained-profile"
$DeviceModel = "$DeviceRoot/input/$ExpectedFilename"
$DeviceReport = "$DeviceRoot/output/morimil-arm64-sustained-profile-v0.json"
$MinimumFreeBytes = ([Int64]$ExpectedSizeBytes * [Int64]2) + ([Int64]2 * 1024 * 1024 * 1024)
$MinimumMemoryKilobytes = [Int64]6 * 1024 * 1024
$ExpectedRounds = 6
$MaximumInferenceMilliseconds = [Int64]30000
$MaximumP95Milliseconds = [Int64]20000
$MaximumTotalPssKilobytes = [Int64]8 * 1024 * 1024
$MaximumBatteryTemperatureCelsius = 45.0
$MaximumTemperatureIncreaseCelsius = 8.0
$SevereThermalStatus = 3

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
    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $rawOutput = @(& $FilePath @CommandArguments 2>&1 | ForEach-Object { "$_" })
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
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

$sdkResult = Invoke-Adb -Step "Comprobar version Android" -CommandArguments @(
    "shell",
    "getprop ro.build.version.sdk"
)
$sdkLevel = [Int64](Get-FirstToken -Text $sdkResult.Text)
if ($sdkLevel -lt 29) {
    Stop-Work "el perfil termico requiere Android API 29 o superior; API=$sdkLevel"
}

$qemuResult = Invoke-Adb -Step "Rechazar emulador" -CommandArguments @(
    "shell",
    "getprop ro.kernel.qemu"
)
if ($qemuResult.Text.Trim() -eq "1") {
    Stop-Work "este perfil requiere un telefono o tableta fisica arm64"
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
$dataLine = @(
    $diskResult.Lines |
        Where-Object { $_ -match "\s/data(?:/\S*)?$" } |
        Select-Object -Last 1
)
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
Invoke-Adb `
    -Step "Detener procesos de Morimil antes del perfil" `
    -CommandArguments @("shell", "am force-stop $TargetPackage") `
    -AllowFailure | Out-Null

$runStamp = Get-Date -Format "yyyyMMdd-HHmmss"
if ([string]::IsNullOrWhiteSpace($ReportDirectory)) {
    $ReportDirectory = Join-Path $repoRoot "build\morimil-arm64-sustained-profile-reports"
}
$resolvedReportDirectory = [System.IO.Path]::GetFullPath($ReportDirectory)
$hostRunDirectory = Join-Path $resolvedReportDirectory $runStamp
New-Item -ItemType Directory -Path $hostRunDirectory -Force | Out-Null
$hostReportPath = Join-Path $hostRunDirectory "morimil-arm64-sustained-profile-v0.json"
$hostTranscriptPath = Join-Path $hostRunDirectory "instrumentation-output.txt"
$remoteTemporary = "/data/local/tmp/$ExpectedFilename.$([guid]::NewGuid().ToString('N')).partial"

try {
    Invoke-Adb -Step "Eliminar staging privado anterior" -CommandArguments @(
        "shell",
        "run-as $TargetPackage rm -rf $DeviceRoot"
    ) -AllowFailure | Out-Null

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
        "run-as $TargetPackage sh -c 'mkdir -p $DeviceRoot/input $DeviceRoot/output'"
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
        -Step "Ejecutar perfil sostenido LiteRT-LM CPU en arm64" `
        -CommandArguments @(
            "shell",
            "am instrument -w -r -e $EnableArgument true -e class $HarnessClass $TestPackage/$Runner"
        ) `
        -AllowFailure
    [System.IO.File]::WriteAllText(
        $hostTranscriptPath,
        $instrumentation.Text + "`n",
        [System.Text.UTF8Encoding]::new($false)
    )

    $reportResult = Invoke-Adb `
        -Step "Extraer informe JSON del proceso Android" `
        -CommandArguments @("shell", "run-as $TargetPackage cat $DeviceReport") `
        -AllowFailure
    $reportText = $reportResult.Text.Trim()
    if (-not [string]::IsNullOrWhiteSpace($reportText)) {
        [System.IO.File]::WriteAllText(
            $hostReportPath,
            $reportText + "`n",
            [System.Text.UTF8Encoding]::new($false)
        )
    }

    if ($instrumentation.ExitCode -ne 0) {
        Stop-Work "adb devolvio codigo $($instrumentation.ExitCode) durante instrumentacion"
    }
    if ($instrumentation.Text -match "FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=") {
        Stop-Work "la instrumentacion Android informo un fallo; revise $hostTranscriptPath y $hostReportPath"
    }
    if ($instrumentation.Text -notmatch "OK \(2 tests\)") {
        Stop-Work "la instrumentacion no confirmo dos tests correctos"
    }
    if ($reportResult.ExitCode -ne 0 -or [string]::IsNullOrWhiteSpace($reportText)) {
        Stop-Work "no se pudo extraer el informe Android"
    }

    $report = $reportText | ConvertFrom-Json
    if ([string]$report.schemaVersion -cne $ExpectedReportSchema) {
        Stop-Work "esquema de informe inesperado"
    }
    if ([string]$report.status -cne "passed") {
        Stop-Work "el perfil Android no termino en estado passed"
    }
    if ($report.researchGatePassed -ne $true) {
        Stop-Work "el perfil no supero el gate fisico de investigacion"
    }
    if ([string]$report.expectedArtifactSha256 -cne "sha256:$ExpectedSha256") {
        Stop-Work "el informe declara otro hash esperado"
    }
    if ([string]$report.hashBeforeFirst -cne "sha256:$ExpectedSha256" -or
        [string]$report.hashAfter -cne "sha256:$ExpectedSha256" -or
        $report.hashStable -ne $true) {
        Stop-Work "el informe no confirma integridad estable del artefacto"
    }
    if ($report.process64Bit -ne $true -or [string]$report.requiredAbi -cne $RequiredAbi) {
        Stop-Work "el informe no confirma proceso arm64 de 64 bits"
    }
    if ($report.engineInitialized -ne $true -or $report.engineClosed -ne $true) {
        Stop-Work "el motor LiteRT-LM no quedo confirmado como inicializado y cerrado"
    }

    $summary = $report.summary
    if ([Int64]$summary.requestedRounds -ne $ExpectedRounds -or
        [Int64]$summary.completedRounds -ne $ExpectedRounds -or
        [Int64]$summary.strictOutputPassCount -ne $ExpectedRounds) {
        Stop-Work "el informe no confirma las $ExpectedRounds rondas estrictas"
    }
    if ($summary.allConversationsClosed -ne $true) {
        Stop-Work "no todas las conversaciones quedaron cerradas"
    }
    if ([Int64]$summary.maximumInferenceMilliseconds -gt $MaximumInferenceMilliseconds) {
        Stop-Work "latencia maxima fuera del gate: $($summary.maximumInferenceMilliseconds) ms"
    }
    if ([Int64]$summary.p95InferenceMilliseconds -gt $MaximumP95Milliseconds) {
        Stop-Work "latencia p95 fuera del gate: $($summary.p95InferenceMilliseconds) ms"
    }
    if ([Int64]$summary.peakTotalPssKilobytes -gt $MaximumTotalPssKilobytes) {
        Stop-Work "PSS maximo fuera del gate: $($summary.peakTotalPssKilobytes) kB"
    }
    if ([double]$summary.maximumBatteryTemperatureCelsius -ge $MaximumBatteryTemperatureCelsius) {
        Stop-Work "temperatura maxima fuera del gate: $($summary.maximumBatteryTemperatureCelsius) C"
    }
    if ([double]$summary.batteryTemperatureIncreaseCelsius -gt $MaximumTemperatureIncreaseCelsius) {
        Stop-Work "aumento de temperatura fuera del gate: $($summary.batteryTemperatureIncreaseCelsius) C"
    }
    if ([Int64]$summary.maximumThermalStatus -ge $SevereThermalStatus) {
        Stop-Work "estado termico severo detectado: $($summary.maximumThermalStatusName)"
    }
    if ($summary.lowMemoryObserved -ne $false) {
        Stop-Work "Android declaro low-memory durante el perfil"
    }
    if (@($report.errors).Count -ne 0) {
        Stop-Work "el informe contiene errores"
    }
    if ($report.certified -ne $false -or
        $report.signed -ne $false -or
        $report.installed -ne $false -or
        $report.promotionAllowed -ne $false -or
        $report.productionAuthorization -ne $false -or
        $report.normalRuntimeActivated -ne $false) {
        Stop-Work "el informe no conserva el estado fail-closed"
    }

    $peakPssMiB = [math]::Round([double]$summary.peakTotalPssKilobytes / 1024.0, 1)
    $minimumAvailableGiB = [math]::Round([double]$summary.minimumSystemAvailableBytes / 1GB, 2)

    Write-Host ""
    Write-Host "PERFIL SOSTENIDO ANDROID ARM64 V0 COMPLETADO."
    Write-Host "Dispositivo:             $($report.manufacturer) $($report.model)"
    Write-Host "Android API:             $($report.sdkInt)"
    Write-Host "Carga LiteRT-LM:         $($summary.loadMilliseconds) ms"
    Write-Host "Rondas estrictas:        $($summary.strictOutputPassCount)/$($summary.requestedRounds)"
    Write-Host "Latencia mediana:        $($summary.medianInferenceMilliseconds) ms"
    Write-Host "Latencia p95:            $($summary.p95InferenceMilliseconds) ms"
    Write-Host "Latencia maxima:         $($summary.maximumInferenceMilliseconds) ms"
    Write-Host "PSS maximo:              $peakPssMiB MiB"
    Write-Host "Memoria disponible min.: $minimumAvailableGiB GiB"
    Write-Host "Bateria inicial/final:   $($summary.initialBatteryLevelPercent)% / $($summary.finalBatteryLevelPercent)%"
    Write-Host "Temperatura inicial/max: $($summary.initialBatteryTemperatureCelsius) C / $($summary.maximumBatteryTemperatureCelsius) C"
    Write-Host "Aumento temperatura:     $($summary.batteryTemperatureIncreaseCelsius) C"
    Write-Host "Estado termico maximo:   $($summary.maximumThermalStatusName)"
    Write-Host "Hash estable:            $($summary.hashStable)"
    Write-Host "Gate de investigacion:   $($report.researchGatePassed)"
    Write-Host "Informe JSON:            $hostReportPath"
    Write-Host "Transcripcion:           $hostTranscriptPath"
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
    Invoke-Adb `
        -Step "Detener proceso de Morimil despues del perfil" `
        -CommandArguments @("shell", "am force-stop $TargetPackage") `
        -AllowFailure | Out-Null
}
