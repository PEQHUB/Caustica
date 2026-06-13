$env:JAVA_HOME = "C:\Users\i\scoop\apps\corretto25-jdk\current"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

$ErrorActionPreference = "Stop"

$upscalerRoot = $PSScriptRoot
$sodiumRoot = Resolve-Path (Join-Path $upscalerRoot "..\sodium-26.2-beta")
$modsDir = Join-Path $upscalerRoot "run\mods"

Write-Host "Building Sodium Fabric jar..."
Push-Location $sodiumRoot
try {
	.\gradlew.bat :fabric:jar
} finally {
	Pop-Location
}

$sodiumJar = Get-ChildItem -Path (Join-Path $sodiumRoot "build\mods") -Filter "sodium-fabric-*.jar" |
	Sort-Object LastWriteTime -Descending |
	Select-Object -First 1

if ($null -eq $sodiumJar) {
	throw "Could not find Sodium Fabric jar in $sodiumRoot\build\mods"
}

New-Item -ItemType Directory -Force -Path $modsDir | Out-Null
Get-ChildItem -Path $modsDir -Filter "sodium-fabric-*.jar" -ErrorAction SilentlyContinue |
	Remove-Item -Force
Copy-Item -LiteralPath $sodiumJar.FullName -Destination (Join-Path $modsDir $sodiumJar.Name) -Force
Write-Host "Copied $($sodiumJar.Name) to $modsDir"

Push-Location $upscalerRoot
try {
	$env:JAVA_TOOL_OPTIONS='-Dupscaler.fsrDebugView=true -Dupscaler.renderScale=1.0'
	.\gradlew.bat --stop
	.\gradlew.bat runClient
} finally {
	Pop-Location
}
