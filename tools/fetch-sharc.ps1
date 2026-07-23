[CmdletBinding()]
param(
    [string]$Destination = (
        Join-Path `
            (Split-Path -Parent $PSScriptRoot) `
            '.deps\sharc-1.8.0.0'
    )
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repository = 'https://github.com/NVIDIA-RTX/SHARC.git'
$expectedCommit =
    'e19ccacd511f42a3df6f850052d508c13c9e9737'

$licenseUrl =
    "https://github.com/NVIDIA-RTX/SHARC/blob/" +
    "$expectedCommit/License.md"

function Invoke-Git {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments
    )

    & git @Arguments

    if ($LASTEXITCODE -ne 0) {
        throw (
            "git failed with exit code " +
            "${LASTEXITCODE}: git " +
            ($Arguments -join ' ')
        )
    }
}

if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    throw 'git was not found on PATH.'
}

$Destination = [IO.Path]::GetFullPath($Destination)

Write-Host 'NVIDIA SHaRC is separately licensed.'
Write-Host "Review the pinned license before use: $licenseUrl"
Write-Host "Pinned commit: $expectedCommit"
Write-Host "Destination: $Destination"

if (Test-Path -LiteralPath $Destination) {
    $gitDirectory = Join-Path $Destination '.git'

    if (-not (
        Test-Path `
            -LiteralPath $gitDirectory `
            -PathType Container
    )) {
        throw (
            "Destination exists but is not a git checkout: " +
            $Destination
        )
    }

    $dirty = @(
        & git `
            -C $Destination `
            status `
            --porcelain `
            --untracked-files=all
    )

    if ($LASTEXITCODE -ne 0) {
        throw "Could not inspect SHaRC checkout: $Destination"
    }

    if ($dirty.Count -gt 0) {
        throw (
            "SHaRC checkout has local modifications. " +
            "Refusing to overwrite them:`n" +
            ($dirty -join "`n")
        )
    }

    $origin = (
        & git `
            -C $Destination `
            remote `
            get-url `
            origin 2>$null
    )

    if ($LASTEXITCODE -ne 0) {
        Invoke-Git @(
            '-C', $Destination,
            'remote', 'add',
            'origin', $repository
        )
    } elseif (
        $origin.Trim() -ne $repository
    ) {
        Invoke-Git @(
            '-C', $Destination,
            'remote', 'set-url',
            'origin', $repository
        )
    }
} else {
    $parent = Split-Path -Parent $Destination

    New-Item `
        -ItemType Directory `
        -Force `
        -Path $parent |
        Out-Null

    Invoke-Git @(
        'init',
        $Destination
    )

    Invoke-Git @(
        '-C', $Destination,
        'remote', 'add',
        'origin', $repository
    )
}

Invoke-Git @(
    '-C', $Destination,
    'fetch',
    '--force',
    '--depth', '1',
    'origin',
    $expectedCommit
)

Invoke-Git @(
    '-C', $Destination,
    'checkout',
    '--detach',
    '--force',
    'FETCH_HEAD'
)

$actualCommit = (
    & git `
        -C $Destination `
        rev-parse `
        HEAD
).Trim()

if ($LASTEXITCODE -ne 0) {
    throw "Could not read SHaRC commit at $Destination"
}

if ($actualCommit -ne $expectedCommit) {
    throw (
        "Expected SHaRC commit $expectedCommit, " +
        "but checkout contains $actualCommit"
    )
}

$requiredFiles = @(
    'include\HashGridCommon.h',
    'include\HashGridTypes.h',
    'include\SharcCommon.h',
    'include\SharcGlslHelpers.h',
    'include\SharcTypes.h',
    'License.md'
)

foreach ($relativePath in $requiredFiles) {
    $candidate = Join-Path $Destination $relativePath

    if (-not (
        Test-Path `
            -LiteralPath $candidate `
            -PathType Leaf
    )) {
        throw (
            "Pinned SHaRC checkout is missing: " +
            $relativePath
        )
    }
}

$commonHeader = Join-Path `
    $Destination `
    'include\SharcCommon.h'

$commonText = [IO.File]::ReadAllText(
    $commonHeader
)

$expectedMacros = @(
    'SHARC_VERSION_MAJOR',
    'SHARC_VERSION_MINOR',
    'SHARC_VERSION_BUILD',
    'SHARC_VERSION_REVISION'
)

foreach ($macro in $expectedMacros) {
    if (-not $commonText.Contains($macro)) {
        throw (
            "SHaRC checkout does not identify itself " +
            "as version 1.8.0.0. Missing: $macro"
        )
    }
}

$env:SHARC_SDK = $Destination

Write-Host "Pinned NVIDIA SHaRC 1.8.0.0 ready at $Destination"

[pscustomobject]@{
    Version = '1.8.0.0'
    Commit = $actualCommit
    Root = $Destination
    CommonHeader = $commonHeader
    EnvironmentVariable = 'SHARC_SDK'
}
