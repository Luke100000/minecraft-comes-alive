param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$gradlePropsPath = Join-Path $repoRoot "gradle.properties"
$localSourcesRoot = Join-Path $repoRoot "local-sources"
$tempRoot = Join-Path $env:TEMP "mca-local-sources"

function Read-GradleProperties {
    param([string]$Path)

    $properties = @{}
    foreach ($line in Get-Content -Path $Path) {
        $trimmed = $line.Trim()
        if ([string]::IsNullOrWhiteSpace($trimmed) -or $trimmed.StartsWith("#")) {
            continue
        }

        $separator = $trimmed.IndexOf("=")
        if ($separator -lt 0) {
            continue
        }

        $key = $trimmed.Substring(0, $separator).Trim()
        $value = $trimmed.Substring($separator + 1).Trim()
        $properties[$key] = $value
    }

    return $properties
}

function Get-SingleMatchOrNull {
    param(
        [string]$BasePath,
        [string]$Filter
    )

    if (!(Test-Path -LiteralPath $BasePath)) {
        return $null
    }

    $matchingFiles = @(Get-ChildItem -Path $BasePath -Recurse -File -Filter $Filter | Sort-Object FullName)
    if ($matchingFiles.Count -eq 0) {
        return $null
    }

    if ($matchingFiles.Count -gt 1) {
        $matchesList = ($matchingFiles | ForEach-Object { " - $($_.FullName)" }) -join [Environment]::NewLine
        throw "Multiple matches found for '$Filter' under '$BasePath'. Refusing to guess between:$([Environment]::NewLine)$matchesList"
    }

    return $matchingFiles[0].FullName
}

function New-DownloadedArchive {
    param(
        [string]$Url,
        [string]$FileName
    )

    if (!(Test-Path -LiteralPath $tempRoot)) {
        New-Item -ItemType Directory -Path $tempRoot | Out-Null
    }

    $downloadPath = Join-Path $tempRoot $FileName
    if (Test-Path -LiteralPath $downloadPath) {
        Remove-Item -LiteralPath $downloadPath -Force
    }

    Write-Host "Downloading $Url"
    & curl.exe -L --fail --silent --show-error -o $downloadPath $Url
    if ($LASTEXITCODE -ne 0 -or !(Test-Path -LiteralPath $downloadPath)) {
        throw "Failed to download archive from '$Url'."
    }

    return $downloadPath
}

function Resolve-ArchivePath {
    param(
        [string]$CacheBasePath,
        [string]$Filter,
        [string]$DownloadUrl,
        [string]$DownloadName
    )

    $cachedPath = Get-SingleMatchOrNull -BasePath $CacheBasePath -Filter $Filter
    if ($null -ne $cachedPath) {
        return $cachedPath
    }

    return New-DownloadedArchive -Url $DownloadUrl -FileName $DownloadName
}

function Get-ArchiveFingerprint {
    param([string]$ArchivePath)

    $archive = Get-Item -LiteralPath $ArchivePath
    $hash = Get-FileHash -LiteralPath $ArchivePath -Algorithm SHA256
    return @{
        ArchiveName = $archive.Name
        ArchiveLength = $archive.Length
        ArchiveSha256 = $hash.Hash
    }
}

function Get-ExtractionMetadataPath {
    param([string]$DestinationPath)

    return Join-Path $DestinationPath ".refresh-local-sources.json"
}

function Test-ExistingExtractionMatches {
    param(
        [string]$ArchivePath,
        [string]$DestinationPath
    )

    if (!(Test-Path -LiteralPath $DestinationPath)) {
        return $false
    }

    $metadataPath = Get-ExtractionMetadataPath -DestinationPath $DestinationPath
    if (!(Test-Path -LiteralPath $metadataPath)) {
        return $false
    }

    try {
        $expected = Get-ArchiveFingerprint -ArchivePath $ArchivePath
        $actual = Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json

        return $actual.ArchiveName -eq $expected.ArchiveName `
            -and $actual.ArchiveLength -eq $expected.ArchiveLength `
            -and $actual.ArchiveSha256 -eq $expected.ArchiveSha256
    } catch {
        return $false
    }
}

function Update-ExtractedSources {
    param(
        [string]$ArchivePath,
        [string]$DestinationPath
    )

    if (!(Test-Path -LiteralPath $ArchivePath)) {
        throw "Archive not found: $ArchivePath"
    }

    if (Test-ExistingExtractionMatches -ArchivePath $ArchivePath -DestinationPath $DestinationPath) {
        Write-Host "Up to date: $DestinationPath"
        return
    }

    if (Test-Path -LiteralPath $DestinationPath) {
        Write-Host "Refreshing extracted sources: $DestinationPath"
        Remove-Item -LiteralPath $DestinationPath -Recurse -Force
    }

    $parent = Split-Path -Parent $DestinationPath
    if (!(Test-Path -LiteralPath $parent)) {
        New-Item -ItemType Directory -Path $parent | Out-Null
    }

    Write-Host "Extracting $ArchivePath -> $DestinationPath"
    [System.IO.Compression.ZipFile]::ExtractToDirectory($ArchivePath, $DestinationPath)

    $metadataPath = Get-ExtractionMetadataPath -DestinationPath $DestinationPath
    Get-ArchiveFingerprint -ArchivePath $ArchivePath |
        ConvertTo-Json |
        Set-Content -LiteralPath $metadataPath -NoNewline
}

function Remove-StaleDirectories {
    param(
        [string]$ParentPath,
        [string]$Pattern,
        [string]$KeepName
    )

    if (!(Test-Path -LiteralPath $ParentPath)) {
        return
    }

    Get-ChildItem -Path $ParentPath -Directory -Filter $Pattern | ForEach-Object {
        if ($_.Name -ne $KeepName) {
            Write-Host "Removing stale source tree: $($_.FullName)"
            Remove-Item -LiteralPath $_.FullName -Recurse -Force
        }
    }
}

Add-Type -AssemblyName System.IO.Compression.FileSystem

$props = Read-GradleProperties -Path $gradlePropsPath
$neoForgeVersion = $props["neoforge_version"]
$neoFormVersion = $props["neo_form_version"]

$neoForgeDirName = "neoforge-$neoForgeVersion-sources"
$neoFormDirName = "neoform-$neoFormVersion"

$neoForgeArchive = Resolve-ArchivePath `
    -CacheBasePath (Join-Path $env:USERPROFILE ".gradle\caches\modules-2\files-2.1\net.neoforged\neoforge\$neoForgeVersion") `
    -Filter "neoforge-$neoForgeVersion-sources.jar" `
    -DownloadUrl "https://maven.neoforged.net/releases/net/neoforged/neoforge/$neoForgeVersion/neoforge-$neoForgeVersion-sources.jar" `
    -DownloadName "neoforge-$neoForgeVersion-sources.jar"

$neoFormArchive = Resolve-ArchivePath `
    -CacheBasePath (Join-Path $env:USERPROFILE ".gradle\caches\modules-2\files-2.1\net.neoforged\neoform\$neoFormVersion") `
    -Filter "neoform-$neoFormVersion.zip" `
    -DownloadUrl "https://maven.neoforged.net/releases/net/neoforged/neoform/$neoFormVersion/neoform-$neoFormVersion.zip" `
    -DownloadName "neoform-$neoFormVersion.zip"

Update-ExtractedSources -ArchivePath $neoForgeArchive -DestinationPath (Join-Path $localSourcesRoot $neoForgeDirName)
Update-ExtractedSources -ArchivePath $neoFormArchive -DestinationPath (Join-Path $localSourcesRoot $neoFormDirName)

Remove-StaleDirectories -ParentPath $localSourcesRoot -Pattern "neoforge-*-sources" -KeepName $neoForgeDirName
Remove-StaleDirectories -ParentPath $localSourcesRoot -Pattern "neoform-*" -KeepName $neoFormDirName

if (Test-Path -LiteralPath $tempRoot) {
    Remove-Item -LiteralPath $tempRoot -Recurse -Force
}

Write-Host "Local source refresh complete."
