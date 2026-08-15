[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string] $MinecraftVersion,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string] $PluginVersion,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string] $PluginJar,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string] $ServerDirectory
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

function Get-Sha256 {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Path
    )

    $stream = [System.IO.File]::OpenRead($Path)
    try {
        $sha256 = [System.Security.Cryptography.SHA256]::Create()
        try {
            $hash = $sha256.ComputeHash($stream)
            return (($hash | ForEach-Object { $_.ToString('x2') }) -join '')
        } finally {
            $sha256.Dispose()
        }
    } finally {
        $stream.Dispose()
    }
}

function Install-FileAtomically {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Source,

        [Parameter(Mandatory = $true)]
        [string] $Destination
    )

    if ([System.IO.File]::Exists($Destination)) {
        $backup = $Destination + '.backup.' + [System.Guid]::NewGuid().ToString('N')
        $replaced = $false
        try {
            if ([System.IO.File]::Exists($backup)) {
                Remove-Item -LiteralPath $backup -Force
            }
            [System.IO.File]::Replace($Source, $Destination, $backup)
            $replaced = $true
        } finally {
            if ([System.IO.File]::Exists($backup)) {
                if (-not $replaced -and -not [System.IO.File]::Exists($Destination)) {
                    Move-Item -LiteralPath $backup -Destination $Destination
                } else {
                    Remove-Item -LiteralPath $backup -Force
                }
            }
        }
    } else {
        [System.IO.File]::Move($Source, $Destination)
    }
}

if (-not (Test-Path -LiteralPath $PluginJar -PathType Leaf)) {
    throw "The packaged HeadDB plugin was not found at '$PluginJar'."
}
$resolvedPlugin = (Resolve-Path -LiteralPath $PluginJar).Path

$serverPath = [System.IO.Path]::GetFullPath($ServerDirectory)
$pluginsPath = Join-Path $serverPath 'plugins'
[System.IO.Directory]::CreateDirectory($pluginsPath) | Out-Null
$runId = [System.Guid]::NewGuid().ToString('N')

$userAgent = "HeadDB-dev-server/$PluginVersion (https://github.com/BitworksMC/HeadDB)"
$buildsUrl = "https://fill.papermc.io/v3/projects/paper/versions/$MinecraftVersion/builds"
Write-Host "Resolving the latest stable Paper $MinecraftVersion build..."
$builds = Invoke-RestMethod `
    -Uri $buildsUrl `
    -Method Get `
    -Headers @{ Accept = 'application/json' } `
    -UserAgent $userAgent `
    -TimeoutSec 30

if ($null -eq $builds) {
    throw "Paper's downloads service returned an empty response."
}
if ($builds.PSObject.Properties['ok'] -and $builds.ok -eq $false) {
    throw "Paper's downloads service returned an error: $($builds.message)"
}

$stableBuild = $builds |
    Where-Object { $_.channel -eq 'STABLE' } |
    Select-Object -First 1
if ($null -eq $stableBuild) {
    throw "Paper does not currently publish a stable build for Minecraft $MinecraftVersion."
}

$downloadProperty = $stableBuild.downloads.PSObject.Properties['server:default']
if ($null -eq $downloadProperty -or $null -eq $downloadProperty.Value) {
    throw "Paper build $($stableBuild.id) does not provide the server:default download."
}

$download = $downloadProperty.Value
$expectedHash = ([string] $download.checksums.sha256).ToLowerInvariant()
if ($expectedHash -notmatch '^[0-9a-f]{64}$') {
    throw "Paper build $($stableBuild.id) returned an invalid SHA-256 checksum."
}

$expectedSize = [long] $download.size
if ($expectedSize -le 0) {
    throw "Paper build $($stableBuild.id) returned an invalid download size."
}

$downloadUri = $null
if (-not [System.Uri]::TryCreate([string] $download.url, [System.UriKind]::Absolute, [ref] $downloadUri) -or
        $downloadUri.Scheme -ne 'https') {
    throw "Paper build $($stableBuild.id) returned an invalid download URL."
}

$paperJar = Join-Path $serverPath 'paper.jar'
$paperIsCurrent = [System.IO.File]::Exists($paperJar) -and
    (Get-Item -LiteralPath $paperJar).Length -eq $expectedSize -and
    (Get-Sha256 -Path $paperJar) -eq $expectedHash

if ($paperIsCurrent) {
    Write-Host "Using cached Paper $MinecraftVersion build $($stableBuild.id)."
} else {
    $temporaryPaperJar = Join-Path $serverPath "paper.jar.download.$runId"
    try {
        if ([System.IO.File]::Exists($temporaryPaperJar)) {
            Remove-Item -LiteralPath $temporaryPaperJar -Force
        }

        Write-Host "Downloading Paper $MinecraftVersion build $($stableBuild.id)..."
        Invoke-WebRequest `
            -Uri $downloadUri `
            -OutFile $temporaryPaperJar `
            -Headers @{ Accept = 'application/java-archive' } `
            -UserAgent $userAgent `
            -UseBasicParsing `
            -TimeoutSec 300

        $actualSize = (Get-Item -LiteralPath $temporaryPaperJar).Length
        if ($actualSize -ne $expectedSize) {
            throw "Paper download size mismatch: expected $expectedSize bytes, received $actualSize."
        }

        $actualHash = Get-Sha256 -Path $temporaryPaperJar
        if ($actualHash -ne $expectedHash) {
            throw "Paper download checksum mismatch: expected $expectedHash, received $actualHash."
        }

        Install-FileAtomically -Source $temporaryPaperJar -Destination $paperJar
    } finally {
        if ([System.IO.File]::Exists($temporaryPaperJar)) {
            Remove-Item -LiteralPath $temporaryPaperJar -Force
        }
    }
}

$pluginDestination = Join-Path $pluginsPath 'HeadDB.jar'
$temporaryPlugin = Join-Path $pluginsPath "HeadDB.jar.download.$runId"
try {
    Copy-Item -LiteralPath $resolvedPlugin -Destination $temporaryPlugin -Force
    Install-FileAtomically -Source $temporaryPlugin -Destination $pluginDestination
} finally {
    if ([System.IO.File]::Exists($temporaryPlugin)) {
        Remove-Item -LiteralPath $temporaryPlugin -Force
    }
}

$eulaFile = Join-Path $serverPath 'eula.txt'
$eula = @(
    '# Generated by HeadDB''s dev-server Maven profile.'
    'eula=true'
) -join [Environment]::NewLine
[System.IO.File]::WriteAllText($eulaFile, $eula + [Environment]::NewLine, [System.Text.Encoding]::ASCII)

Write-Host "Installed HeadDB $PluginVersion at '$pluginDestination'."
Write-Host "Prepared Paper $MinecraftVersion build $($stableBuild.id) at '$serverPath'."
