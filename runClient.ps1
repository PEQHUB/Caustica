$ErrorActionPreference = "Stop"

$upscalerRoot = $PSScriptRoot
$nativesDir = Join-Path $upscalerRoot "run\natives"

$ngxShim = Join-Path $upscalerRoot "native\ngx_shim\out\Release\ngxshim.dll"
if (Test-Path -LiteralPath $ngxShim) {
	New-Item -ItemType Directory -Force -Path $nativesDir | Out-Null
	Copy-Item -LiteralPath $ngxShim -Destination (Join-Path $nativesDir "ngxshim.dll") -Force
	Write-Host "Copied rebuilt ngxshim.dll to $nativesDir"
}

Push-Location $upscalerRoot
try {
	$env:JAVA_TOOL_OPTIONS='-Xmx8G -Dupscaler.renderScale=0.5 -Dupscaler.rt.composite=true -Dupscaler.rt.output=rt -Dupscaler.rt.dlssRr=true -Dupscaler.rt.exposure.key=0.12 -Dupscaler.rt.exposure.maxEv=2.0 -Dupscaler.rt.exposure.minEv=-1.0 -Dupscaler.rt.cancelVanillaWorld=true -Dupscaler.rt.asyncTerrain=true -Dupscaler.rt.workerThreads=4 -Dupscaler.rt.sunNoonSouthDeg=30 -Dupscaler.rt.logCapture=true -Dupscaler.rt.lightStats=true -Dupscaler.rt.risCandidates=8 -Dupscaler.rt.lightDump=true -Dupscaler.rt.lightDumpRadius=3 -Dupscaler.rt.lightMinFillRatio=0.18 -Dupscaler.rt.risNoShadow=false -Dupscaler.rt.lightSurfaceOffset=0.05'
	.\gradlew.bat --stop
	.\gradlew.bat runClient --args="--renderDebugLabels --graphicsBackend VULKAN"
} finally {
	Pop-Location
}
