[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [Parameter(Mandatory = $true)]
    [string]$InstanceRoot,

    [ValidateRange(0, 5)]
    [int]$GeneratedFrames = 0
)

$ErrorActionPreference = 'Stop'

function Assert-PrismAndMinecraftClosed {
    param([string]$ResolvedInstanceRoot)

    $prism = @(Get-Process -Name 'prismlauncher' -ErrorAction SilentlyContinue)
    if ($prism.Count -gt 0) {
        throw "PrismLauncher is still running (PID(s): $($prism.Id -join ', ')). Close it manually; this script never terminates processes."
    }

    try {
        $javaProcesses = @(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" |
            Where-Object {
                $commandLine = [string]$_.CommandLine
                $commandLine.IndexOf($ResolvedInstanceRoot, [StringComparison]::OrdinalIgnoreCase) -ge 0 -or
                    $commandLine -match '(?i)(net\.minecraft\.client\.main\.Main|minecraft-launcher|minecraft\\)'
            })
    } catch {
        throw "Could not prove Minecraft is closed; refusing configuration repair. $($_.Exception.Message)"
    }
    if ($javaProcesses.Count -gt 0) {
        throw "Minecraft is still running (PID(s): $($javaProcesses.ProcessId -join ', ')). Close it manually; this script never terminates processes."
    }
}

function Set-TomlSectionValue {
    param(
        [string]$Text,
        [string]$Section,
        [string]$Key,
        [string]$Value
    )

    $sectionPattern = '(?ms)(^\[' + [regex]::Escape($Section) + '\][ \t]*\r?\n)(.*?)(?=^\[|\z)'
    $match = [regex]::Match($Text, $sectionPattern)
    if (-not $match.Success) {
        throw "Missing [$Section] section in caustica.toml"
    }
    $body = $match.Groups[2].Value
    $keyPattern = '(?m)^[ \t]*' + [regex]::Escape($Key) + '[ \t]*=.*$'
    if ([regex]::IsMatch($body, $keyPattern)) {
        $body = [regex]::Replace($body, $keyPattern, "`t$Key = $Value", 1)
    } else {
        $body = "`t$Key = $Value`r`n" + $body
    }
    return $Text.Substring(0, $match.Groups[2].Index) + $body +
        $Text.Substring($match.Groups[2].Index + $match.Groups[2].Length)
}

$resolvedRoot = (Resolve-Path -LiteralPath $InstanceRoot).Path.TrimEnd('\')
$instanceCfg = Join-Path $resolvedRoot 'instance.cfg'
$minecraftRoot = Join-Path $resolvedRoot 'minecraft'
$causticaToml = Join-Path $minecraftRoot 'config\caustica.toml'
if (-not (Test-Path -LiteralPath $instanceCfg -PathType Leaf)) {
    throw "Missing Prism instance.cfg: $instanceCfg"
}
if (-not (Test-Path -LiteralPath $causticaToml -PathType Leaf)) {
    throw "Missing Caustica TOML: $causticaToml"
}

Assert-PrismAndMinecraftClosed -ResolvedInstanceRoot $resolvedRoot

$instanceText = [IO.File]::ReadAllText($instanceCfg)
$tomlText = [IO.File]::ReadAllText($causticaToml)

# Remove only Caustica's proof/development launch overrides. Normal JVM flags remain untouched.
$instanceText = [regex]::Replace($instanceText,
    '(?i)(?<!\S)-Dcaustica\.rt\.dlssRr=(?:true|false)(?!\S)', '')
$instanceText = [regex]::Replace($instanceText,
    '(?i)(?<!\S)-Dcaustica\.streamline\.dlssg\.[^\s"]+(?!\S)', '')
$instanceText = [regex]::Replace($instanceText, '(?m)^(JvmArgs=".*?) {2,}', '$1 ')
$instanceText = [regex]::Replace($instanceText, '(?m) +"$', '"')

$tomlText = Set-TomlSectionValue $tomlText 'dlss-rr' 'enabled' 'true'
$tomlText = Set-TomlSectionValue $tomlText 'frame-generation' 'enabled' 'true'
$tomlText = Set-TomlSectionValue $tomlText 'frame-generation' 'mode' '"fixed"'
if ($GeneratedFrames -gt 0) {
    $tomlText = Set-TomlSectionValue $tomlText 'frame-generation' 'multi-frame-count' ([string]$GeneratedFrames)
}

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backupRoot = Join-Path $resolvedRoot ".caustica-backups\$stamp"
New-Item -ItemType Directory -Path $backupRoot -Force | Out-Null
Copy-Item -LiteralPath $instanceCfg -Destination (Join-Path $backupRoot 'instance.cfg')
Copy-Item -LiteralPath $causticaToml -Destination (Join-Path $backupRoot 'caustica.toml')

if ($PSCmdlet.ShouldProcess($resolvedRoot, 'Remove DLSS proof overrides and enable fixed DLSSG plus DLSSD')) {
    $utf8 = New-Object Text.UTF8Encoding($false)
    [IO.File]::WriteAllText($instanceCfg, $instanceText, $utf8)
    [IO.File]::WriteAllText($causticaToml, $tomlText, $utf8)
}

[pscustomobject]@{
    InstanceRoot = $resolvedRoot
    BackupRoot = $backupRoot
    GeneratedFrames = if ($GeneratedFrames -gt 0) { $GeneratedFrames } else { 'preserved' }
    InstanceCfgSha256 = (Get-FileHash -LiteralPath $instanceCfg -Algorithm SHA256).Hash
    CausticaTomlSha256 = (Get-FileHash -LiteralPath $causticaToml -Algorithm SHA256).Hash
}
