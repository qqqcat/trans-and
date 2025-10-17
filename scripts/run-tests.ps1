$ErrorActionPreference = "Stop"

function Invoke-Gradle {
    param (
        [string[]]$Arguments
    )
    $gradleWrapper = Join-Path $PSScriptRoot "..\gradlew.bat"
    if (-not (Test-Path $gradleWrapper)) {
        throw "Unable to locate gradle wrapper at $gradleWrapper"
    }
    & $gradleWrapper @Arguments
}

Write-Host "Running unit tests and collecting coverage metrics..."
Invoke-Gradle -Arguments @("collectTestMetrics")

$metricsPath = Join-Path $PSScriptRoot "..\app\build\metrics\test-metrics.json"
if (Test-Path $metricsPath) {
    Write-Host ""
    Write-Host "Metrics summary:"
    Get-Content $metricsPath
    Write-Host ""
    Write-Host "Results written to $metricsPath"
} else {
    Write-Warning "Metrics file not found. Check Gradle output for details."
}
