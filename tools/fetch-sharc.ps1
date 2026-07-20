param(
    [string]$Destination = (Join-Path (Split-Path -Parent $PSScriptRoot) '.deps\sharc-1.6.5.0')
)

$ErrorActionPreference = 'Stop'
$expected = '0b9f58bbc8c41736042d4da964830a247e424a00'
$license = 'https://github.com/NVIDIA-RTX/SHARC/blob/v1.6.5.0/License.md'

Write-Host "NVIDIA SHaRC is separately licensed. Review and accept: $license"
if (-not (Test-Path -LiteralPath $Destination)) {
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Destination) | Out-Null
    git clone --branch v1.6.5.0 --depth 1 https://github.com/NVIDIA-RTX/SHARC.git $Destination
}

$actual = (git -C $Destination rev-parse HEAD).Trim()
if ($actual -ne $expected) {
    throw "Expected SHaRC $expected, found $actual at $Destination"
}

Write-Host "Pinned NVIDIA SHaRC 1.6.5.0 ready at $Destination"
Write-Host "Set for this shell with: `$env:SHARC_SDK = '$Destination'"
