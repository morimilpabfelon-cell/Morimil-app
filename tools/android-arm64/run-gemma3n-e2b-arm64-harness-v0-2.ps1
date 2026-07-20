param(
    [Parameter(Mandatory = $true)]
    [string]$ArtifactPath,

    [string]$Serial,

    [string]$ReportDirectory,

    [switch]$SkipBuild
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$sourceRunner = Join-Path $PSScriptRoot "run-gemma3n-e2b-arm64-harness-v0.ps1"
if (-not (Test-Path -LiteralPath $sourceRunner -PathType Leaf)) {
    throw "Detenido: falta el runner base: $sourceRunner"
}

$content = Get-Content -LiteralPath $sourceRunner -Raw -Encoding UTF8

$oldDfExpression = '$_ -match "\s/data$"'
$newDfExpression = '$_ -match "\s/data(?:/\S*)?$"'
$dfOccurrences = [regex]::Matches(
    $content,
    [regex]::Escape($oldDfExpression)
).Count
if ($dfOccurrences -ne 1) {
    throw "Detenido: se esperaba exactamente un parser antiguo de df; encontrados: $dfOccurrences"
}
$content = $content.Replace($oldDfExpression, $newDfExpression)

$oldNativeBlock = @'
    $rawOutput = @(& $FilePath @CommandArguments 2>&1 | ForEach-Object { "$_" })
    $exitCode = $LASTEXITCODE
'@
$newNativeBlock = @'
    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $rawOutput = @(& $FilePath @CommandArguments 2>&1 | ForEach-Object { "$_" })
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
'@
$nativeOccurrences = [regex]::Matches(
    $content,
    [regex]::Escape($oldNativeBlock)
).Count
if ($nativeOccurrences -ne 1) {
    throw "Detenido: se esperaba exactamente un bloque antiguo de ejecucion nativa; encontrados: $nativeOccurrences"
}
$content = $content.Replace($oldNativeBlock, $newNativeBlock)

$temporaryRunner = Join-Path $PSScriptRoot (
    ".run-gemma3n-e2b-arm64-harness-v0-2." +
    [guid]::NewGuid().ToString("N") +
    ".ps1"
)

$parameters = @{
    ArtifactPath = $ArtifactPath
}
if (-not [string]::IsNullOrWhiteSpace($Serial)) {
    $parameters.Serial = $Serial
}
if (-not [string]::IsNullOrWhiteSpace($ReportDirectory)) {
    $parameters.ReportDirectory = $ReportDirectory
}
if ($SkipBuild.IsPresent) {
    $parameters.SkipBuild = $true
}

try {
    [System.IO.File]::WriteAllText(
        $temporaryRunner,
        $content,
        [System.Text.UTF8Encoding]::new($false)
    )
    & $temporaryRunner @parameters
} finally {
    Remove-Item -LiteralPath $temporaryRunner -Force -ErrorAction SilentlyContinue
}
