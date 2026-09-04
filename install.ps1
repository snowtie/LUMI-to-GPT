param(
    [string]$TargetRoot = (Join-Path $env:LOCALAPPDATA "LumiToGPT\app"),
    [ValidateSet("AddonOnly", "WithTts")]
    [string]$InstallMode = "AddonOnly",
    [string]$GptSovitsArchive,
    [string]$VoiceWeightsArchive,
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
$VoiceWeightsUrl = "https://github.com/snowtie/LUMI-to-GPT/releases/download/v0.9.0/GPT_weights_v2.7z"
$VoiceWeightsSha256 = "4A0FF7071C3D0D4C56A48016D8BC66CA5C8C626D599C0E71300F0DE3AFA14E79"
if (-not (Test-Path -LiteralPath $BridgeSource)) {
    throw "설치 파일이 없습니다: $BridgeSource"
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
        Write-Host "GPT-SoVITS 통합판을 설치합니다."
        Expand-SafeArchive $runtimeArchive $runtimeRoot
        $runtime = Find-GptSovitsRuntime $runtimeRoot
        if (-not $runtime) { throw "압축 파일에서 GPT-SoVITS 실행 환경을 찾지 못했습니다." }
    }

    $modelRoot = Join-Path $resolvedDataRoot "models\LUMI-v2"
    $gptWeight = Get-ChildItem -LiteralPath $modelRoot -Recurse -File -Filter "LUMI-e10.ckpt" -ErrorAction SilentlyContinue | Select-Object -First 1
    $sovitsWeight = Get-ChildItem -LiteralPath $modelRoot -Recurse -File -Filter "LUMI_e8_s880.pth" -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $gptWeight -or -not $sovitsWeight) {
        $weightsArchive = Resolve-VoiceWeightsArchive $VoiceWeightsArchive $resolvedDataRoot
        Write-Host "LUMI 음성 가중치를 설치합니다."
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

$ResolvedTargetRoot = [IO.Path]::GetFullPath($TargetRoot)
New-Item -ItemType Directory -Force -Path $ResolvedTargetRoot | Out-Null
$BridgeExecutable = Join-Path $ResolvedTargetRoot "lumi-to-gpt.exe"
Copy-Item -LiteralPath $BridgeSource -Destination $BridgeExecutable -Force
Copy-Item -LiteralPath (Join-Path $SourceRoot "README.md") -Destination $ResolvedTargetRoot -Force

$LumiApp = Find-LittleLumiApp
if (-not $SkipLumiPatch) {
    Install-LittleLumiPatch $LumiApp
}

$ConfigureProcess = Start-Process -FilePath $BridgeExecutable -ArgumentList "--configure-lumi-chat" -Wait -PassThru -WindowStyle Hidden
if ($ConfigureProcess.ExitCode -ne 0) {
    throw "필수 창작마당 항목 'LUMI Chat'을 구독하고 Little LUMI를 한 번 실행한 뒤 다시 설치해 주세요."
}

if ($InstallMode -eq "WithTts") {
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
        $VoiceProcess = Start-Process -FilePath $BridgeExecutable -ArgumentList "--configure-gpt-sovits" -Wait -PassThru -WindowStyle Hidden
        if ($VoiceProcess.ExitCode -ne 0) { throw "GPT-SoVITS 설정 적용에 실패했습니다." }
    }
    finally {
        foreach ($name in $environmentNames) {
            [Environment]::SetEnvironmentVariable($name, $previousEnvironment[$name], "Process")
        }
    }
}

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
    $ShortcutPath = Join-Path ([Environment]::GetFolderPath("Desktop")) "LUMI to GPT.lnk"
    $Shell = New-Object -ComObject WScript.Shell
    $Shortcut = $Shell.CreateShortcut($ShortcutPath)
    $Shortcut.TargetPath = $BridgeExecutable
    $Shortcut.WorkingDirectory = $ResolvedTargetRoot
    $Shortcut.Description = "Little LUMI를 ChatGPT 웹에 연결"
    $Shortcut.Save()
}

Write-Host ""
Write-Host "LUMI to GPT 설치를 완료했습니다."
Write-Host "설치 폴더: $ResolvedTargetRoot"
Write-Host "1. Little LUMI를 다시 시작하세요."
Write-Host "2. 바탕화면의 'LUMI to GPT'를 실행하세요."
Write-Host "3. 열린 ChatGPT 창에서 한 번 로그인하세요."
Write-Host "4. ChatGPT에서 LUMI 프로젝트를 만들고 오른쪽 위 'LUMI 프로젝트'에서 연결하세요."
Write-Host "5. Little LUMI의 '루미 AI 설정'에서 두뇌는 'GPT Web', 목소리는 'GPT-SoVITS'를 선택하세요."
Write-Host "6. 꼬미를 더블클릭하거나 우클릭 -> '말 걸기'로 대화하세요."
if ($InstallMode -eq "WithTts") {
    Write-Host "GPT-SoVITS와 LUMI 음성 설정도 적용했습니다."
}
if ($CodexCommand -and -not $SkipMcp) {
    Write-Host "7. Codex 앱을 재시작하면 완료 알림 MCP가 적용됩니다."
}
