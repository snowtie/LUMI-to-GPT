param(
    [string]$TargetRoot = (Join-Path $env:LOCALAPPDATA "LumiToGPT\app"),
    [ValidateSet("AddonOnly", "WithTts", "TtsOnly")]
    [string]$InstallMode = "AddonOnly",
    [string]$GptSovitsArchive,
    [string]$VoiceWeightsArchive,
    [string]$CodexAppServerArchive,
    [string]$ReferenceAudio,
    [string]$ReferenceText,
    [string]$TtsDataRoot = (Join-Path $env:LOCALAPPDATA "LumiToGPT"),
    [switch]$SkipMcp,
    [switch]$SkipShortcut,
    [switch]$SkipLumiPatch
)

$ErrorActionPreference = "Stop"
if (Test-Path -LiteralPath variable:PSNativeCommandUseErrorActionPreference) {
    $PSNativeCommandUseErrorActionPreference = $true
}

$SourceRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$BridgeSource = Join-Path $SourceRoot "LUMI to GPT.exe"
$GptSovitsUrl = "https://huggingface.co/lj1995/GPT-SoVITS-windows-package/resolve/main/GPT-SoVITS-v2-240821.7z?download=true"
$GptSovitsSha256 = "9D9BA79DE6ACA0CF28A3635CCB1DBBB08B6AEF362C4352E32FAD99BB49E3000A"
$CodexAppServerVersion = "0.153.4"
$CodexAppServerUrl = "https://github.com/openai/codex/releases/download/rust-v0.153.4/codex-app-server-x86_64-pc-windows-msvc.exe.zip"
$CodexAppServerSha256 = "B944B854A150BD3C269D9F17CF58756BC29FA248E93D9E6CD1DAC3CEE1D8A774"
$VoiceWeightsUrl = "https://github.com/snowtie/LUMI-to-GPT/releases/download/v0.9.0/GPT_weights_v2.7z"
$VoiceWeightsSha256 = "4A0FF7071C3D0D4C56A48016D8BC66CA5C8C626D599C0E71300F0DE3AFA14E79"
$InstallRunId = Get-Date -Format "yyyyMMdd-HHmmss-fff"
$LogBase = if ($env:LOCALAPPDATA) { $env:LOCALAPPDATA } else { [IO.Path]::GetTempPath() }
$LogRoot = Join-Path $LogBase "LumiToGPT\logs"
$LogPath = Join-Path $LogRoot "install-$InstallRunId.log"
$CurrentStep = "설치 준비"
$TranscriptStarted = $false

function Set-InstallStep([string]$Step) {
    $script:CurrentStep = $Step
    Write-Host ""
    Write-Host "[$Step]"
}

function Get-InstalledBridgeProcesses([string]$Executable) {
    $resolvedExecutable = [IO.Path]::GetFullPath($Executable)
    @(
        Get-CimInstance -ClassName Win32_Process -Filter "Name = 'lumi-to-gpt.exe'" -ErrorAction SilentlyContinue |
            Where-Object {
                $_.ExecutablePath -and
                [String]::Equals(
                    [IO.Path]::GetFullPath($_.ExecutablePath),
                    $resolvedExecutable,
                    [StringComparison]::OrdinalIgnoreCase
                )
            }
    )
}

function Stop-InstalledBridge([string]$Executable) {
    $processes = @(Get-InstalledBridgeProcesses $Executable)
    if ($processes.Count -eq 0) { return }

    Write-Host "실행 중인 기존 LUMI to GPT를 종료합니다."
    foreach ($process in $processes) {
        Stop-Process -Id $process.ProcessId -Force -ErrorAction Stop
    }

    $deadline = [DateTime]::UtcNow.AddSeconds(10)
    do {
        Start-Sleep -Milliseconds 100
        $remaining = @(Get-InstalledBridgeProcesses $Executable)
    } while ($remaining.Count -gt 0 -and [DateTime]::UtcNow -lt $deadline)

    if ($remaining.Count -gt 0) {
        throw "실행 중인 기존 LUMI to GPT를 종료하지 못했습니다. 작업 관리자에서 종료한 뒤 다시 시도해 주세요: $Executable"
    }
}

function Invoke-BridgeSetup([string]$Executable, [string]$Argument, [string]$Label) {
    $safeLabel = $Label -replace '[^A-Za-z0-9_-]', '-'
    $stdoutPath = Join-Path $LogRoot "$InstallRunId-$safeLabel.stdout.log"
    $stderrPath = Join-Path $LogRoot "$InstallRunId-$safeLabel.stderr.log"
    $process = Start-Process -FilePath $Executable -ArgumentList $Argument -Wait -PassThru -WindowStyle Hidden `
        -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath
    if ($process.ExitCode -eq 0) {
        Remove-Item -LiteralPath $stdoutPath, $stderrPath -Force -ErrorAction SilentlyContinue
        return
    }
    $details = @(Get-Content -LiteralPath $stderrPath -Tail 40 -ErrorAction SilentlyContinue) -join " | "
    if (-not $details) {
        $details = @(Get-Content -LiteralPath $stdoutPath -Tail 40 -ErrorAction SilentlyContinue) -join " | "
    }
    if (-not $details) { $details = "추가 출력 없음" }
    throw "$Label 실패 (종료 코드 $($process.ExitCode)): $details"
}

function Find-LittleLumiApp {
    if ($env:LUMI_APP_DIR) {
        $configured = [IO.Path]::GetFullPath($env:LUMI_APP_DIR)
        if (Test-Path -LiteralPath (Join-Path $configured "Shimeji-ee.jar")) { return $configured }
    }
    foreach ($drive in [char[]](67..90)) {
        foreach ($suffix in @(
            "Steam\steamapps\common\Little LUMI\app",
            "Program Files (x86)\Steam\steamapps\common\Little LUMI\app",
            "Program Files\Steam\steamapps\common\Little LUMI\app"
        )) {
            $candidate = "${drive}:\$suffix"
            if (Test-Path -LiteralPath (Join-Path $candidate "Shimeji-ee.jar")) { return $candidate }
        }
    }
    throw "Little LUMI 설치 폴더를 찾지 못했습니다."
}

function Assert-SafeArchive([string]$ArchivePath) {
    $entries = & tar.exe -tf $ArchivePath
    if ($LASTEXITCODE -ne 0) { throw "압축 파일을 읽지 못했습니다: $ArchivePath" }
    foreach ($entry in $entries) {
        $normalized = $entry.Replace('\', '/')
        if ($normalized.StartsWith('/') -or $normalized -match '^[A-Za-z]:' -or
            $normalized.Split('/') -contains '..') {
            throw "안전하지 않은 압축 경로가 있습니다: $entry"
        }
    }
}

function Expand-SafeArchive([string]$ArchivePath, [string]$Destination) {
    Assert-SafeArchive $ArchivePath
    New-Item -ItemType Directory -Force -Path $Destination | Out-Null
    & tar.exe -xf $ArchivePath -C $Destination
    if ($LASTEXITCODE -ne 0) { throw "압축 해제에 실패했습니다: $ArchivePath" }
}

function Find-GptSovitsRuntime([string]$Root) {
    if (-not (Test-Path -LiteralPath $Root -PathType Container)) { return $null }
    foreach ($candidate in @($Root) + @(Get-ChildItem -LiteralPath $Root -Directory -ErrorAction SilentlyContinue | ForEach-Object FullName)) {
        if ((Test-Path -LiteralPath (Join-Path $candidate "api_v2.py") -PathType Leaf) -and
            (Test-Path -LiteralPath (Join-Path $candidate "runtime\python.exe") -PathType Leaf)) {
            return $candidate
        }
    }
    return $null
}

function Save-Download([string]$Url, [string]$Destination, [string]$FailureMessage) {
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Destination) | Out-Null
    $partial = "$Destination.part"
    if (Test-Path -LiteralPath $partial) { Remove-Item -LiteralPath $partial -Force }
    try {
        $curl = Get-Command curl.exe -ErrorAction SilentlyContinue
        if ($curl) {
            & $curl.Source -L --fail --progress-bar -o $partial $Url
            if ($LASTEXITCODE -ne 0) { throw $FailureMessage }
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

function Resolve-GptSovitsArchive([string]$RequestedPath, [string]$DataRoot) {
    if ($RequestedPath) {
        $resolved = [IO.Path]::GetFullPath($RequestedPath)
        if (-not (Test-Path -LiteralPath $resolved -PathType Leaf)) {
            throw "GPT-SoVITS 통합판을 찾지 못했습니다: $resolved"
        }
        return $resolved
    }

    $downloadRoot = Join-Path $DataRoot "downloads"
    $archive = Join-Path $downloadRoot "GPT-SoVITS-v2-240821.7z"
    if (-not (Test-Path -LiteralPath $archive -PathType Leaf)) {
        Write-Host "GPT-SoVITS 공식 통합판을 내려받습니다. 약 5.7GB입니다."
        Save-Download $GptSovitsUrl $archive "GPT-SoVITS 다운로드에 실패했습니다."
    }
    $actualHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $archive).Hash
    if ($actualHash -ne $GptSovitsSha256) {
        throw "GPT-SoVITS 통합판의 SHA-256이 올바르지 않습니다: $archive"
    }
    return $archive
}

function Resolve-VoiceWeightsArchive([string]$RequestedPath, [string]$DataRoot) {
    if ($RequestedPath) {
        $resolved = [IO.Path]::GetFullPath($RequestedPath)
        if (-not (Test-Path -LiteralPath $resolved -PathType Leaf)) {
            throw "음성 가중치를 찾지 못했습니다: $resolved"
        }
        return $resolved
    }

    $downloadRoot = Join-Path $DataRoot "downloads"
    $archive = Join-Path $downloadRoot "GPT_weights_v2.7z"
    if (-not (Test-Path -LiteralPath $archive -PathType Leaf)) {
        Write-Host "LUMI 음성 가중치를 내려받습니다. 약 420MB입니다."
        Save-Download $VoiceWeightsUrl $archive "LUMI 음성 가중치 다운로드에 실패했습니다."
    }
    $actualHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $archive).Hash
    if ($actualHash -ne $VoiceWeightsSha256) {
        throw "LUMI 음성 가중치의 SHA-256이 올바르지 않습니다: $archive"
    }
    return $archive
}

function Resolve-CodexAppServerArchive([string]$RequestedPath, [string]$DataRoot) {
    if ($RequestedPath) {
        $resolved = [IO.Path]::GetFullPath($RequestedPath)
        if (-not (Test-Path -LiteralPath $resolved -PathType Leaf)) {
            throw "Codex App Server 압축 파일을 찾지 못했습니다: $resolved"
        }
        return $resolved
    }

    $downloadRoot = Join-Path $DataRoot "downloads"
    $archive = Join-Path $downloadRoot "codex-app-server-$CodexAppServerVersion-windows-x64.zip"
    if (-not (Test-Path -LiteralPath $archive -PathType Leaf)) {
        Write-Host "공식 Codex App Server를 내려받습니다. 약 75MB입니다."
        Save-Download $CodexAppServerUrl $archive "Codex App Server 다운로드에 실패했습니다."
    }
    $actualHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $archive).Hash
    if ($actualHash -ne $CodexAppServerSha256) {
        throw "Codex App Server 압축 파일의 SHA-256이 올바르지 않습니다: $archive"
    }
    return $archive
}

function Find-PreferredFile([string]$Root, [string]$PreferredName, [string]$Filter) {
    $files = @(Get-ChildItem -LiteralPath $Root -Recurse -File -Filter $Filter -ErrorAction SilentlyContinue)
    $preferred = $files | Where-Object Name -eq $PreferredName | Select-Object -First 1
    if ($preferred) { return $preferred.FullName }
    if ($files.Count -eq 1) { return $files[0].FullName }
    throw "사용할 $Filter 파일을 하나로 결정하지 못했습니다."
}

function Find-ReferenceVoice([string]$LumiApp, [string]$Audio, [string]$Text) {
    if ($Audio -or $Text) {
        if (-not $Audio -or -not $Text) { throw "참조 음성과 참조 대사는 함께 지정해 주세요." }
        $resolvedAudio = [IO.Path]::GetFullPath($Audio)
        if (-not (Test-Path -LiteralPath $resolvedAudio -PathType Leaf)) {
            throw "참조 음성을 찾지 못했습니다: $resolvedAudio"
        }
        return @{ Audio = $resolvedAudio; Text = $Text }
    }

    $indexes = @(Get-ChildItem -LiteralPath (Join-Path $LumiApp "voice\Lumi") -Recurse -File -Filter "index.tsv" -ErrorAction SilentlyContinue)
    foreach ($index in $indexes) {
        $rows = @(Get-Content -LiteralPath $index.FullName -Encoding UTF8 | Where-Object { $_ -and -not $_.StartsWith('#') })
        $preferredRows = @($rows | Where-Object { $_.StartsWith("0001f2f71d2f937f`t") }) + $rows
        foreach ($row in $preferredRows) {
            $columns = $row.Split("`t", 5)
            if ($columns.Count -lt 5 -or -not $columns[4].Trim()) { continue }
            $audioPath = Join-Path $index.DirectoryName $columns[3]
            if (Test-Path -LiteralPath $audioPath -PathType Leaf) {
                return @{ Audio = $audioPath; Text = $columns[4].Trim() }
            }
        }
    }
    throw "참조 음성을 찾지 못했습니다. LUMI Voice Pack을 설치하거나 -ReferenceAudio와 -ReferenceText를 지정해 주세요."
}

function Install-CodexAppServer([string]$Target, [string]$DataRoot) {
    $targetExecutable = Join-Path $Target "codex-app-server.exe"
    $versionMarker = Join-Path $Target "codex-app-server.version"
    if ((Test-Path -LiteralPath $targetExecutable -PathType Leaf) -and
        (Test-Path -LiteralPath $versionMarker -PathType Leaf) -and
        ((Get-Content -LiteralPath $versionMarker -Raw).Trim() -eq $CodexAppServerVersion)) {
        return $targetExecutable
    }

    $archive = Resolve-CodexAppServerArchive $CodexAppServerArchive $DataRoot
    $extractRoot = Join-Path $DataRoot "downloads\codex-app-server-$CodexAppServerVersion"
    $sourceExecutable = Get-ChildItem -LiteralPath $extractRoot -Recurse -File -Filter "codex-app-server-x86_64-pc-windows-msvc.exe" -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $sourceExecutable) {
        Expand-SafeArchive $archive $extractRoot
        $sourceExecutable = Get-ChildItem -LiteralPath $extractRoot -Recurse -File -Filter "codex-app-server-x86_64-pc-windows-msvc.exe" -ErrorAction SilentlyContinue | Select-Object -First 1
    }
    if (-not $sourceExecutable) {
        throw "압축 파일에서 Codex App Server 실행 파일을 찾지 못했습니다."
    }
    Copy-Item -LiteralPath $sourceExecutable.FullName -Destination $targetExecutable -Force
    Set-Content -LiteralPath $versionMarker -Encoding Ascii -Value $CodexAppServerVersion
    return $targetExecutable
}

function Install-GptSovits([string]$LumiApp, [string]$DataRoot) {
    $resolvedDataRoot = [IO.Path]::GetFullPath($DataRoot)
    New-Item -ItemType Directory -Force -Path $resolvedDataRoot | Out-Null

    $runtimeRoot = Join-Path $resolvedDataRoot "gpt-sovits"
    $runtime = Find-GptSovitsRuntime $runtimeRoot
    if (-not $runtime) {
        if ((Test-Path -LiteralPath $runtimeRoot) -and @(Get-ChildItem -LiteralPath $runtimeRoot -Force).Count -gt 0) {
            throw "완전하지 않은 GPT-SoVITS 폴더가 있습니다: $runtimeRoot"
        }
        $runtimeArchive = Resolve-GptSovitsArchive $GptSovitsArchive $resolvedDataRoot
        Write-Host "GPT-SoVITS 통합판의 압축을 해제합니다. 오래 걸릴 수 있으니 CMD 창을 닫지 마세요."
        Expand-SafeArchive $runtimeArchive $runtimeRoot
        $runtime = Find-GptSovitsRuntime $runtimeRoot
        if (-not $runtime) { throw "압축 파일에서 GPT-SoVITS 실행 환경을 찾지 못했습니다." }
    }

    $modelRoot = Join-Path $resolvedDataRoot "models\LUMI-v2"
    $gptWeight = Get-ChildItem -LiteralPath $modelRoot -Recurse -File -Filter "LUMI-e10.ckpt" -ErrorAction SilentlyContinue | Select-Object -First 1
    $sovitsWeight = Get-ChildItem -LiteralPath $modelRoot -Recurse -File -Filter "LUMI_e8_s880.pth" -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $gptWeight -or -not $sovitsWeight) {
        $weightsArchive = Resolve-VoiceWeightsArchive $VoiceWeightsArchive $resolvedDataRoot
        Write-Host "LUMI 음성 가중치를 설치합니다. 완료 메시지가 나올 때까지 기다려 주세요."
        Expand-SafeArchive $weightsArchive $modelRoot
    }
    $gptWeight = Find-PreferredFile $modelRoot "LUMI-e10.ckpt" "*.ckpt"
    $sovitsWeight = Find-PreferredFile $modelRoot "LUMI_e8_s880.pth" "*.pth"
    $reference = Find-ReferenceVoice $LumiApp $ReferenceAudio $ReferenceText

    return @{
        Runtime = $runtimeRoot
        GptWeight = $gptWeight
        SovitsWeight = $sovitsWeight
        ReferenceAudio = $reference.Audio
        ReferenceText = $reference.Text
    }
}

function Get-ZipEntryHash([IO.Compression.ZipArchiveEntry]$Entry) {
    $sha = [Security.Cryptography.SHA256]::Create()
    $stream = $Entry.Open()
    try {
        return ([BitConverter]::ToString($sha.ComputeHash($stream))).Replace("-", "")
    }
    finally {
        $stream.Dispose()
        $sha.Dispose()
    }
}

function Get-ZipEntryText([IO.Compression.ZipArchiveEntry]$Entry) {
    $stream = $Entry.Open()
    $reader = [IO.StreamReader]::new($stream, [Text.Encoding]::UTF8)
    try { return $reader.ReadToEnd() }
    finally {
        $reader.Dispose()
        $stream.Dispose()
    }
}

function Test-LittleLumiPatchInstalled([string]$LumiApp) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $targetJar = Join-Path $LumiApp "Shimeji-ee.jar"
    try {
        $archive = [IO.Compression.ZipFile]::OpenRead($targetJar)
        try { return $null -ne $archive.GetEntry("META-INF/lumi-to-gpt-patch.properties") }
        finally { $archive.Dispose() }
    }
    catch {
        return $false
    }
}

function Install-LittleLumiPatch([string]$LumiApp) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $patchRoot = Join-Path $SourceRoot "little-lumi-patch"
    $patchJar = Join-Path $patchRoot "lumi-to-gpt-little-lumi-patch.jar"
    $hashFile = Join-Path $patchRoot "original-class-sha256.json"
    if (-not (Test-Path -LiteralPath $patchJar) -or -not (Test-Path -LiteralPath $hashFile)) {
        throw "Little LUMI 연동 패치 파일이 없습니다."
    }
    if (Get-Process -Name "LittleLumiModel" -ErrorAction SilentlyContinue) {
        throw "Little LUMI를 완전히 종료한 뒤 다시 설치해 주세요."
    }

    $targetJar = Join-Path $LumiApp "Shimeji-ee.jar"
    $backupJar = "$targetJar.lumi-to-gpt.bak"
    $patchArchive = [IO.Compression.ZipFile]::OpenRead($patchJar)
    try {
        $patchMarker = Get-ZipEntryText $patchArchive.GetEntry("META-INF/lumi-to-gpt-patch.properties")
    }
    finally {
        $patchArchive.Dispose()
    }
    $target = [IO.Compression.ZipFile]::OpenRead($targetJar)
    try {
        $installedMarkerEntry = $target.GetEntry("META-INF/lumi-to-gpt-patch.properties")
        $installedMarker = if ($installedMarkerEntry) { Get-ZipEntryText $installedMarkerEntry } else { $null }
        if ($installedMarker -eq $patchMarker) {
            return
        }
    }
    finally {
        $target.Dispose()
    }

    $validationJar = if ($installedMarker) { $backupJar } else { $targetJar }
    if (-not (Test-Path -LiteralPath $validationJar)) {
        throw "기존 Little LUMI 원본 백업을 찾지 못했습니다: $validationJar"
    }
    $validation = [IO.Compression.ZipFile]::OpenRead($validationJar)
    try {
        foreach ($expected in (Get-Content -LiteralPath $hashFile -Raw | ConvertFrom-Json)) {
            $entry = $validation.GetEntry($expected.entry)
            if (-not $entry -or (Get-ZipEntryHash $entry) -ne $expected.sha256) {
                throw "현재 Little LUMI 버전과 연동 패치가 맞지 않습니다: $($expected.entry)"
            }
        }
    }
    finally {
        $validation.Dispose()
    }

    if (-not $installedMarker) {
        Copy-Item -LiteralPath $targetJar -Destination $backupJar -Force
    }
    elseif (-not (Test-Path -LiteralPath $backupJar)) {
        throw "현재 Little LUMI 원본 백업을 찾지 못했습니다: $backupJar"
    }
    try {
        $patch = [IO.Compression.ZipFile]::OpenRead($patchJar)
        $target = [IO.Compression.ZipFile]::Open($targetJar, [IO.Compression.ZipArchiveMode]::Update)
        try {
            foreach ($patchEntry in $patch.Entries) {
                if ($patchEntry.FullName -eq "META-INF/MANIFEST.MF" -or $patchEntry.FullName.EndsWith("/")) { continue }
                $existing = $target.GetEntry($patchEntry.FullName)
                if ($existing) { $existing.Delete() }
                $replacement = $target.CreateEntry($patchEntry.FullName, [IO.Compression.CompressionLevel]::Optimal)
                $source = $patchEntry.Open()
                $destination = $replacement.Open()
                try { $source.CopyTo($destination) }
                finally {
                    $destination.Dispose()
                    $source.Dispose()
                }
            }
        }
        finally {
            $target.Dispose()
            $patch.Dispose()
        }
    }
    catch {
        Copy-Item -LiteralPath $backupJar -Destination $targetJar -Force
        throw
    }
}

$InstallExitCode = 0
$CodexCommand = $null
try {
    New-Item -ItemType Directory -Force -Path $LogRoot | Out-Null
    [IO.File]::WriteAllText($LogPath, "", [Text.UTF8Encoding]::new($true))
    Start-Transcript -LiteralPath $LogPath -Append -Force | Out-Null
    $TranscriptStarted = $true
    Write-Host "LUMI to GPT 설치 로그"
    Write-Host "모드: $InstallMode"
    Write-Host "로그: $LogPath"
    if ($InstallMode -in @("WithTts", "TtsOnly")) {
        Write-Host ""
        Write-Host "중요: 대용량 압축 해제와 음성 가중치 준비 중에는 한동안 새 출력이 없을 수 있습니다." -ForegroundColor Yellow
        Write-Host "'설치 완료' 메시지가 나올 때까지 이 CMD 창을 닫지 말고 기다려 주세요." -ForegroundColor Yellow
        Write-Host ""
    }

    Set-InstallStep "애드온 파일 확인"
    $ResolvedTargetRoot = [IO.Path]::GetFullPath($TargetRoot)
    $BridgeExecutable = Join-Path $ResolvedTargetRoot "lumi-to-gpt.exe"
    if ($InstallMode -eq "TtsOnly") {
        if (-not (Test-Path -LiteralPath $BridgeExecutable -PathType Leaf)) {
            throw "기존 LUMI to GPT 설치를 찾지 못했습니다: $BridgeExecutable. 먼저 1번 또는 2번으로 애드온을 설치해 주세요."
        }
    }
    Stop-InstalledBridge $BridgeExecutable

    if ($InstallMode -ne "TtsOnly") {
        if (-not (Test-Path -LiteralPath $BridgeSource -PathType Leaf)) {
            throw "설치 파일이 없습니다: $BridgeSource"
        }
        New-Item -ItemType Directory -Force -Path $ResolvedTargetRoot | Out-Null
        Copy-Item -LiteralPath $BridgeSource -Destination $BridgeExecutable -Force
        Copy-Item -LiteralPath (Join-Path $SourceRoot "README.md") -Destination $ResolvedTargetRoot -Force

        Set-InstallStep "Codex App Server 설치"
        Install-CodexAppServer $ResolvedTargetRoot ([IO.Path]::GetFullPath($TtsDataRoot)) | Out-Null
    }

    Set-InstallStep "Little LUMI 확인"
    $LumiApp = Find-LittleLumiApp
    if ($InstallMode -eq "TtsOnly") {
        if (-not $SkipLumiPatch -and -not (Test-LittleLumiPatchInstalled $LumiApp)) {
            throw "Little LUMI 연동 패치가 설치되어 있지 않습니다. 먼저 1번 또는 2번으로 애드온을 설치해 주세요."
        }
    }
    elseif (-not $SkipLumiPatch) {
        Set-InstallStep "Little LUMI 연동 패치"
        Install-LittleLumiPatch $LumiApp
    }

    if ($InstallMode -ne "TtsOnly") {
        Set-InstallStep "LUMI Chat 연결 설정"
        Invoke-BridgeSetup $BridgeExecutable "--configure-lumi-chat" "LUMI Chat 연결 설정"
    }

    if ($InstallMode -in @("WithTts", "TtsOnly")) {
        Set-InstallStep "GPT-SoVITS와 LUMI 음성 설치"
        $voice = Install-GptSovits $LumiApp $TtsDataRoot
        $environmentNames = @(
            "LUMI_TTS_RUNTIME",
            "LUMI_TTS_GPT_WEIGHTS",
            "LUMI_TTS_SOVITS_WEIGHTS",
            "LUMI_TTS_REFERENCE_AUDIO",
            "LUMI_TTS_REFERENCE_TEXT"
        )
        $previousEnvironment = @{}
        foreach ($name in $environmentNames) { $previousEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, "Process") }
        try {
            $env:LUMI_TTS_RUNTIME = $voice.Runtime
            $env:LUMI_TTS_GPT_WEIGHTS = $voice.GptWeight
            $env:LUMI_TTS_SOVITS_WEIGHTS = $voice.SovitsWeight
            $env:LUMI_TTS_REFERENCE_AUDIO = $voice.ReferenceAudio
            $env:LUMI_TTS_REFERENCE_TEXT = $voice.ReferenceText
            Set-InstallStep "GPT-SoVITS 설정 적용"
            Invoke-BridgeSetup $BridgeExecutable "--configure-gpt-sovits" "GPT-SoVITS 설정 적용"
        }
        finally {
            foreach ($name in $environmentNames) {
                [Environment]::SetEnvironmentVariable($name, $previousEnvironment[$name], "Process")
            }
        }
    }

    if ($InstallMode -ne "TtsOnly") {
        Set-InstallStep "Codex MCP 등록"
        $CodexCommand = Get-Command codex -ErrorAction SilentlyContinue
        if (-not $SkipMcp -and $CodexCommand) {
            & $CodexCommand.Source mcp get lumi *> $null
            if ($LASTEXITCODE -eq 0) {
                & $CodexCommand.Source mcp remove lumi
                if ($LASTEXITCODE -ne 0) { throw "기존 Codex MCP 제거에 실패했습니다." }
            }
            & $CodexCommand.Source mcp add lumi -- $BridgeExecutable --mcp
            if ($LASTEXITCODE -ne 0) { throw "Codex MCP 등록에 실패했습니다." }
        }

        if (-not $SkipShortcut) {
            Set-InstallStep "바탕화면 바로가기 생성"
            $ShortcutPath = Join-Path ([Environment]::GetFolderPath("Desktop")) "LUMI to GPT.lnk"
            $Shell = New-Object -ComObject WScript.Shell
            $Shortcut = $Shell.CreateShortcut($ShortcutPath)
            $Shortcut.TargetPath = $BridgeExecutable
            $Shortcut.WorkingDirectory = $ResolvedTargetRoot
            $Shortcut.IconLocation = "$BridgeExecutable,0"
            $Shortcut.Description = "Little LUMI를 ChatGPT 계정에 연결"
            $Shortcut.Save()
            try {
                $IconRefresh = Join-Path $env:SystemRoot "System32\ie4uinit.exe"
                if (Test-Path -LiteralPath $IconRefresh -PathType Leaf) {
                    & $IconRefresh -ClearIconCache | Out-Null
                    & $IconRefresh -show | Out-Null
                }
                Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;
public static class LumiToGptShellRefresh {
    [DllImport("shell32.dll")]
    public static extern void SHChangeNotify(uint eventId, uint flags, IntPtr item1, IntPtr item2);
}
"@
                [LumiToGptShellRefresh]::SHChangeNotify(0x08000000, 0, [IntPtr]::Zero, [IntPtr]::Zero)
            }
            catch {
                Write-Warning "바로가기 아이콘 새로 고침에 실패했습니다: $($_.Exception.Message)"
            }
        }
    }

    Set-InstallStep "설치 완료"
    Write-Host "설치 폴더: $ResolvedTargetRoot"
    if ($InstallMode -eq "TtsOnly") {
        Write-Host "GPT-SoVITS와 LUMI 음성만 추가했습니다."
        Write-Host "Little LUMI를 다시 시작하고 루미 AI 설정의 목소리 탭을 확인하세요."
    }
    else {
        Write-Host "1. Little LUMI를 다시 시작하세요."
        Write-Host "2. 바탕화면의 'LUMI to GPT'를 실행하세요."
        Write-Host "3. 계정 연결 창에서 'ChatGPT 계정 연결'을 누르고 브라우저 로그인을 완료하세요."
        Write-Host "4. Little LUMI의 '루미 AI 설정'에서 두뇌는 'ChatGPT (OAuth)'를 확인하세요."
        Write-Host "5. 기본 모델은 GPT-5.6 Luna이며 혼잣말과 화면 구경은 처음부터 꺼져 있습니다."
        Write-Host "6. 꼬미를 더블클릭하거나 우클릭 -> '말 걸기'로 대화하세요."
        if ($InstallMode -eq "WithTts") {
            Write-Host "GPT-SoVITS와 LUMI 음성 설정도 적용했습니다."
        }
        if ($CodexCommand -and -not $SkipMcp) {
            Write-Host "7. Codex 앱을 재시작하면 완료 알림 MCP가 적용됩니다."
        }
    }
    Write-Host "상세 로그: $LogPath"
}
catch {
    $InstallExitCode = 1
    $failure = $_
    Write-Host ""
    Write-Host "설치에 실패했습니다." -ForegroundColor Red
    Write-Host "실패 단계: $CurrentStep" -ForegroundColor Red
    Write-Host "오류: $($failure.Exception.Message)" -ForegroundColor Red
    Write-Host "오류 종류: $($failure.Exception.GetType().FullName)"
    if ($failure.InvocationInfo.PositionMessage) {
        Write-Host "발생 위치: $($failure.InvocationInfo.PositionMessage)"
    }
    if ($failure.ScriptStackTrace) {
        Write-Host "스크립트 호출 경로: $($failure.ScriptStackTrace)"
    }
    Write-Host "상세 로그: $LogPath" -ForegroundColor Yellow
}
finally {
    if ($TranscriptStarted) {
        try { Stop-Transcript | Out-Null } catch {}
    }
}

if ($InstallExitCode -ne 0) { exit $InstallExitCode }
