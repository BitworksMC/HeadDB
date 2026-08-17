[CmdletBinding()]
param(
    [string] $PluginJar = 'headdb-legacy/target/HeadDB-6.0.3-legacy.jar',
    [string] $WorkDirectory = 'build/legacy-smoke',
    [int] $StartupTimeoutSeconds = 120,
    [string[]] $MinecraftVersions
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$versions = @(
    '1.8.8', '1.9.4', '1.10.2', '1.11.2', '1.12', '1.12.1', '1.12.2',
    '1.13', '1.13.1', '1.13.2', '1.14', '1.14.1', '1.14.2', '1.14.3', '1.14.4',
    '1.15', '1.15.1', '1.15.2', '1.16.1', '1.16.2', '1.16.3', '1.16.4', '1.16.5',
    '1.17', '1.17.1', '1.18', '1.18.1', '1.18.2',
    '1.19', '1.19.1', '1.19.2', '1.19.3', '1.19.4',
    '1.20', '1.20.1', '1.20.2', '1.20.4', '1.20.5', '1.20.6'
)
if ($null -ne $MinecraftVersions -and $MinecraftVersions.Count -gt 0) {
    $unknown = $MinecraftVersions | Where-Object { $_ -notin $versions }
    if ($unknown) { throw "Unsupported smoke-test version(s): $($unknown -join ', ')" }
    $versions = $MinecraftVersions
}

function Get-JavaFeature([string] $version) {
    $parts = $version.Split('.')
    $minor = [int] $parts[1]
    $patch = if ($parts.Length -gt 2) { [int] $parts[2] } else { 0 }
    if ($minor -le 16) { return 8 }
    if ($minor -eq 17) { return 16 }
    if ($minor -eq 20 -and $patch -ge 5) { return 21 }
    return 17
}

function Get-Sha256([string] $path) {
    $stream = [IO.File]::OpenRead($path)
    try {
        $sha = [Security.Cryptography.SHA256]::Create()
        try { return (($sha.ComputeHash($stream) | ForEach-Object { $_.ToString('x2') }) -join '') }
        finally { $sha.Dispose() }
    } finally { $stream.Dispose() }
}

function Get-Sha1([string] $path) {
    $stream = [IO.File]::OpenRead($path)
    try {
        $sha = [Security.Cryptography.SHA1]::Create()
        try { return (($sha.ComputeHash($stream) | ForEach-Object { $_.ToString('x2') }) -join '') }
        finally { $sha.Dispose() }
    } finally { $stream.Dispose() }
}

function Get-MojangServer([string] $version, [string] $cacheRoot) {
    [IO.Directory]::CreateDirectory($cacheRoot) | Out-Null
    $jar = Join-Path $cacheRoot "minecraft_server.$version.jar"
    if (Test-Path -LiteralPath $jar -PathType Leaf) { return $jar }
    $manifest = Invoke-RestMethod -Uri 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json' -TimeoutSec 30
    $versionEntry = $manifest.versions | Where-Object { $_.id -eq $version } | Select-Object -First 1
    if ($null -eq $versionEntry) { throw "Mojang manifest has no server $version" }
    $metadata = Invoke-RestMethod -Uri $versionEntry.url -TimeoutSec 30
    if ($null -eq $metadata.downloads.server) { throw "Mojang publishes no server jar for $version" }
    Write-Host "Downloading the official Mojang $version server jar..."
    Invoke-WebRequest -Uri $metadata.downloads.server.url -OutFile $jar -UseBasicParsing -TimeoutSec 300
    if ((Get-Sha1 $jar) -ne ([string] $metadata.downloads.server.sha1).ToLowerInvariant()) {
        Remove-Item -LiteralPath $jar -Force
        throw "Mojang $version checksum mismatch"
    }
    return $jar
}

function Install-Java([int] $feature, [string] $runtimeRoot) {
    $destination = Join-Path $runtimeRoot "java-$feature"
    $java = Join-Path $destination 'bin/java.exe'
    if (Test-Path -LiteralPath $java -PathType Leaf) { return $java }

    [IO.Directory]::CreateDirectory($runtimeRoot) | Out-Null
    $archive = Join-Path $runtimeRoot "java-$feature.zip"
    Write-Host "Downloading Eclipse Temurin Java $feature..."
    $downloaded = $false
    foreach ($imageType in 'jre', 'jdk') {
        $uri = "https://api.adoptium.net/v3/binary/latest/$feature/ga/windows/x64/$imageType/hotspot/normal/eclipse"
        try {
            Invoke-WebRequest -Uri $uri -OutFile $archive -UseBasicParsing -TimeoutSec 300
            $downloaded = $true
            break
        } catch {
            if (Test-Path -LiteralPath $archive) { Remove-Item -LiteralPath $archive -Force }
        }
    }
    if (-not $downloaded) { throw "No Eclipse Temurin Java $feature JRE or JDK package is available" }
    $extract = Join-Path $runtimeRoot "extract-$feature"
    if (Test-Path -LiteralPath $extract) { Remove-Item -LiteralPath $extract -Recurse -Force }
    Expand-Archive -LiteralPath $archive -DestinationPath $extract
    $extractedJava = Get-ChildItem -LiteralPath $extract -Filter java.exe -Recurse |
        Where-Object { $_.FullName -match '[\\/]bin[\\/]java\.exe$' } | Select-Object -First 1
    if ($null -eq $extractedJava) { throw "Java $feature archive did not contain bin/java.exe" }
    $jdkRoot = Split-Path (Split-Path $extractedJava.FullName -Parent) -Parent
    Move-Item -LiteralPath $jdkRoot -Destination $destination
    Remove-Item -LiteralPath $extract -Recurse -Force
    Remove-Item -LiteralPath $archive -Force
    return $java
}

function Get-Paper([string] $version, [string] $cacheRoot) {
    [IO.Directory]::CreateDirectory($cacheRoot) | Out-Null
    $jar = Join-Path $cacheRoot "paper-$version.jar"
    if (Test-Path -LiteralPath $jar -PathType Leaf) { return $jar }

    $headers = @{ Accept = 'application/json' }
    $agent = 'HeadDB-legacy-smoke/6.0.3 (https://github.com/BitworksMC/HeadDB)'
    $builds = Invoke-RestMethod -Uri "https://fill.papermc.io/v3/projects/paper/versions/$version/builds" `
        -Headers $headers -UserAgent $agent -TimeoutSec 30
    $build = $builds | Where-Object { $_.channel -eq 'STABLE' } | Select-Object -First 1
    if ($null -eq $build) { $build = $builds | Select-Object -First 1 }
    if ($null -eq $build) { throw "No Paper build exists for $version" }
    $download = $build.downloads.PSObject.Properties['server:default'].Value
    Write-Host "Downloading Paper $version build $($build.id)..."
    Invoke-WebRequest -Uri $download.url -OutFile $jar -UseBasicParsing -UserAgent $agent -TimeoutSec 300
    $actual = Get-Sha256 $jar
    if ($actual -ne ([string] $download.checksums.sha256).ToLowerInvariant()) {
        Remove-Item -LiteralPath $jar -Force
        throw "Paper $version checksum mismatch"
    }
    return $jar
}

$repository = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$plugin = (Resolve-Path (Join-Path $repository $PluginJar)).Path
$root = [IO.Path]::GetFullPath((Join-Path $repository $WorkDirectory))
$cache = Join-Path $root 'cache'
$runtimeRoot = Join-Path $cache 'java'
$paperRoot = Join-Path $cache 'paper'
$mojangRoot = Join-Path $cache 'mojang'
$results = [Collections.Generic.List[object]]::new()

foreach ($feature in 8, 16, 17, 21) { [void](Install-Java $feature $runtimeRoot) }

foreach ($version in $versions) {
    $started = Get-Date
    $status = 'FAIL'
    $detail = ''
    $process = $null
    try {
        $server = Join-Path $root "servers/$version"
        $plugins = Join-Path $server 'plugins'
        [IO.Directory]::CreateDirectory($plugins) | Out-Null
        Copy-Item -LiteralPath $plugin -Destination (Join-Path $plugins 'HeadDB.jar') -Force
        Copy-Item -LiteralPath (Get-Paper $version $paperRoot) -Destination (Join-Path $server 'paper.jar') -Force
        $minor = [int] $version.Split('.')[1]
        if ($minor -le 12) {
            $serverCache = Join-Path $server 'cache'
            [IO.Directory]::CreateDirectory($serverCache) | Out-Null
            Copy-Item -LiteralPath (Get-MojangServer $version $mojangRoot) `
                -Destination (Join-Path $serverCache "mojang_$version.jar") -Force
        }
        [IO.File]::WriteAllText((Join-Path $server 'eula.txt'), "eula=true`r`n", [Text.Encoding]::ASCII)
        [IO.File]::WriteAllText((Join-Path $server 'server.properties'), "online-mode=false`r`nserver-port=0`r`nmax-tick-time=-1`r`n", [Text.Encoding]::ASCII)

        $stdout = Join-Path $server 'smoke-stdout.log'
        $stderr = Join-Path $server 'smoke-stderr.log'
        $java = Install-Java (Get-JavaFeature $version) $runtimeRoot
        $bootstrapOut = Join-Path $server 'bootstrap-stdout.log'
        $bootstrapErr = Join-Path $server 'bootstrap-stderr.log'
        $bootstrap = Start-Process -FilePath $java `
            -ArgumentList '-Djava.awt.headless=true','-Xms256M','-Xmx768M','-jar','paper.jar','--version' `
            -WorkingDirectory $server -RedirectStandardOutput $bootstrapOut -RedirectStandardError $bootstrapErr `
            -WindowStyle Hidden -PassThru -Wait
        if ($bootstrap.ExitCode -ne 0) { throw "Paper bootstrap/version check exited with code $($bootstrap.ExitCode)" }
        $process = Start-Process -FilePath $java -ArgumentList '-Djava.awt.headless=true','-Xms256M','-Xmx768M','-jar','paper.jar' `
            -WorkingDirectory $server -RedirectStandardOutput $stdout -RedirectStandardError $stderr `
            -WindowStyle Hidden -PassThru

        $deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
        do {
            Start-Sleep -Milliseconds 500
            $process.Refresh()
            $log = if (Test-Path -LiteralPath $stdout) { Get-Content -LiteralPath $stdout -Raw } else { '' }
            if ($process.HasExited) { break }
        } while ((Get-Date) -lt $deadline -and
            ($log -notmatch '\[HeadDB\] Enabled the Minecraft 1\.8\.8-1\.20\.6 legacy implementation\.' -or
             $log -notmatch '\[HeadDB\] Loaded [0-9,]+ heads\.' -or
             $log -notmatch 'Done \([0-9.]+s\)!'))

        if ($log -match '\[HeadDB\] Enabled the Minecraft 1\.8\.8-1\.20\.6 legacy implementation\.' -and
            $log -match '\[HeadDB\] Loaded [0-9,]+ heads\.' -and
            $log -match 'Done \([0-9.]+s\)!' -and
            $log -notmatch '(?im)^.*\[HeadDB\].*(ERROR|SEVERE|Exception)') {
            $status = 'PASS'
            $detail = ([regex]::Match($log, 'Loaded [0-9,]+ heads').Value)
        } elseif ($process.HasExited) {
            $detail = "Server exited with code $($process.ExitCode)"
        } else {
            $detail = "Startup exceeded $StartupTimeoutSeconds seconds"
        }
    } catch {
        $detail = $_.Exception.Message
    } finally {
        if ($null -ne $process -and -not $process.HasExited) {
            Stop-Process -Id $process.Id -Force
            $process.WaitForExit(10000) | Out-Null
        }
    }

    $result = [pscustomobject]@{
        Version = $version
        Java = Get-JavaFeature $version
        Status = $status
        Seconds = [math]::Round(((Get-Date) - $started).TotalSeconds, 1)
        Detail = $detail
    }
    $results.Add($result)
    $result | Format-Table -AutoSize
}

$csv = Join-Path $root 'results.csv'
$results | Export-Csv -LiteralPath $csv -NoTypeInformation
$results | Format-Table -AutoSize
Write-Host "Results: $csv"
if ($results.Status -contains 'FAIL') { exit 1 }
