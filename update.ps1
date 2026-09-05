param(
    [Parameter(Mandatory = $true)]
    [string]$TargetRoot,
    [string]$ReleaseApiUrl = "https://api.github.com/repos/snowtie/LUMI-to-GPT/releases/latest",
    [switch]$SkipRestart,
    [switch]$NoPause
)

$ErrorActionPreference = "Stop"
if (Test-Path -LiteralPath variable:PSNativeCommandUseErrorActionPreference) {
    $PSNativeCommandUseErrorActionPreference = $true
}

function Save-Download([string]$Url, [string]$Destination) {
    $partial = "$Destination.part"
    if (Test-Path -LiteralPath $partial) { Remove-Item -LiteralPath $partial -Force }
    try {
        $curl = Get-Command curl.exe -ErrorAction SilentlyContinue
        if ($curl) {
            & $curl.Source -L --fail --progress-bar -o $partial $Url
            if ($LASTEXITCODE -ne 0) { throw "Download failed: $Url" }
        }
        else {
            Invoke-WebRequest -Uri $Url -OutFile $partial -UseBasicParsing
        }
        Move-Item -LiteralPath $partial -Destination $Destination -Force
    }
    catch {
        if (Test-Path -LiteralPath $partial) { Remove-Item -LiteralPath $partial -Force }
        throw
    }
}

function Assert-SafeZip([string]$ArchivePath) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [IO.Compression.ZipFile]::OpenRead($ArchivePath)
    try {
        foreach ($entry in $archive.Entries) {
            $name = $entry.FullName.Replace('\', '/')
            if ($name.StartsWith('/') -or $name -match '^[A-Za-z]:' -or $name.Split('/') -contains '..') {
                throw "Unsafe archive entry: $($entry.FullName)"
            }
        }
    }
    finally {
        $archive.Dispose()
    }
}

function Get-Sha256([string]$Path) {
    $sha = [Security.Cryptography.SHA256]::Create()
    $stream = [IO.File]::OpenRead($Path)
    try {
        return ([BitConverter]::ToString($sha.ComputeHash($stream))).Replace("-", "")
    }
    finally {
        $stream.Dispose()
        $sha.Dispose()
    }
}

$UpdateExitCode = 0
$RunId = Get-Date -Format "yyyyMMdd-HHmmss-fff"
$UpdateRoot = Join-Path $env:LOCALAPPDATA "LumiToGPT\update"
$WorkRoot = Join-Path $UpdateRoot $RunId
$ResolvedTargetRoot = [IO.Path]::GetFullPath($TargetRoot)

try {
    New-Item -ItemType Directory -Force -Path $WorkRoot | Out-Null
    Write-Host "LUMI to GPT 자동 업데이트"
    Write-Host "최신 버전 정보를 확인합니다. 이 창을 닫지 마세요."

    $headers = @{ "User-Agent" = "LUMI-to-GPT-Updater" }
    $release = Invoke-RestMethod -Uri $ReleaseApiUrl -Headers $headers -UseBasicParsing
    $tag = [string]$release.tag_name
    if (-not $tag) { throw "최신 버전 번호를 확인하지 못했습니다." }

    $archiveName = "LUMI-to-GPT-$tag-windows-x64.zip"
    $archiveAsset = @($release.assets | Where-Object { $_.name -eq $archiveName } | Select-Object -First 1)
    $checksumAsset = @($release.assets | Where-Object { $_.name -eq "SHA256SUMS.txt" } | Select-Object -First 1)
    if (-not $archiveAsset) { throw "업데이트 파일을 찾지 못했습니다: $archiveName" }
    if (-not $checksumAsset) { throw "SHA256SUMS.txt를 찾지 못했습니다." }

    $archivePath = Join-Path $WorkRoot $archiveName
    $checksumPath = Join-Path $WorkRoot "SHA256SUMS.txt"
    Write-Host "$tag 업데이트를 내려받습니다."
    Save-Download ([string]$archiveAsset[0].browser_download_url) $archivePath
    Save-Download ([string]$checksumAsset[0].browser_download_url) $checksumPath

    $escapedArchiveName = [regex]::Escape($archiveName)
    $checksumLine = Get-Content -LiteralPath $checksumPath | Where-Object {
        $_ -match "(?i)^[0-9a-f]{64}\s+\*?$escapedArchiveName\s*$"
    } | Select-Object -First 1
    if (-not $checksumLine) { throw "$archiveName 체크섬을 찾지 못했습니다." }
    $expectedHash = ($checksumLine -split '\s+')[0].ToUpperInvariant()
    $actualHash = Get-Sha256 $archivePath
    if ($actualHash -ne $expectedHash) { throw "업데이트 파일의 SHA-256이 올바르지 않습니다." }

    Write-Host "검증 완료. 업데이트 파일을 준비합니다."
    Assert-SafeZip $archivePath
    $packageRoot = Join-Path $WorkRoot "package"
    Expand-Archive -LiteralPath $archivePath -DestinationPath $packageRoot -Force
    $installer = Join-Path $packageRoot "install.ps1"
    if (-not (Test-Path -LiteralPath $installer -PathType Leaf)) {
        throw "업데이트 설치기를 찾지 못했습니다."
    }

    Write-Host "설치를 시작합니다. 실행 중인 LUMI to GPT는 자동으로 종료됩니다."
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $installer `
        -TargetRoot $ResolvedTargetRoot -InstallMode AddonOnly `
        -SkipMcp -SkipShortcut -SkipLumiPatch
    if ($LASTEXITCODE -ne 0) { throw "업데이트 설치에 실패했습니다. 종료 코드: $LASTEXITCODE" }

    $installedExecutable = Join-Path $ResolvedTargetRoot "lumi-to-gpt.exe"
    if (-not (Test-Path -LiteralPath $installedExecutable -PathType Leaf)) {
        throw "업데이트된 실행 파일을 찾지 못했습니다: $installedExecutable"
    }
    Write-Host "업데이트 완료."
    if (-not $SkipRestart) {
        Start-Process -FilePath $installedExecutable -WorkingDirectory $ResolvedTargetRoot
    }
}
catch {
    $UpdateExitCode = 1
    Write-Host ""
    Write-Host "업데이트에 실패했습니다." -ForegroundColor Red
    Write-Host "오류: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "작업 폴더: $WorkRoot"
    if (-not $NoPause) { Read-Host "Enter 키를 눌러 창을 닫으세요" | Out-Null }
}

exit $UpdateExitCode
