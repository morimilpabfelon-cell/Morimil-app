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
$oldExpression = '$_ -match "\s/data$"'
$newExpression = '$_ -match "\s/data(?:/\S*)?$"'
$occurrences = [regex]::Matches(
    $content,
    [regex]::Escape($oldExpression)
).Count
if ($occurrences -ne 1) {
    throw "Detenido: se esperaba exactamente un parser antiguo de df; encontrados: $occurrences"
}

$patchedContent = $content.Replace($oldExpression, $newExpression)
$temporaryRunner = Join-Path $PSScriptRoot (
    ".run-gemma3n-e2b-arm64-harness-v0-1." +
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
        $patchedContent,
        [System.Text.UTF8Encoding]::new($false)
    )
    & $temporaryRunner @parameters
} finally {
    Remove-Item -LiteralPath $temporaryRunner -Force -ErrorAction SilentlyContinue
}
