param(
    [string]$BaseJar = "D:\Steam\steamapps\common\Little LUMI\app\Shimeji-ee.jar",
    [string]$Javac
)

$ErrorActionPreference = "Stop"
$PatchRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $PatchRoot
if (-not $Javac) {
    $Javac = Join-Path $ProjectRoot "..\..\work\java-tools\jdk25\bin\javac.exe"
}
$JarTool = Join-Path (Split-Path -Parent $Javac) "jar.exe"
$BuildRoot = Join-Path $PatchRoot "build"
$ClassesRoot = Join-Path $BuildRoot "classes"
$PatchJar = Join-Path $PatchRoot "lumi-to-gpt-little-lumi-patch.jar"
$MarkerPath = Join-Path $PatchRoot "META-INF\lumi-to-gpt-patch.properties"
$HashPath = Join-Path $PatchRoot "original-class-sha256.json"
$Version = "1.0.4"

foreach ($required in @($BaseJar, $Javac, $JarTool)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "필요한 파일이 없습니다: $required"
    }
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$ReferenceJar = $BaseJar
$BaseArchive = [IO.Compression.ZipFile]::OpenRead([IO.Path]::GetFullPath($BaseJar))
try {
    if ($BaseArchive.GetEntry("META-INF/lumi-to-gpt-patch.properties")) {
        $OriginalBackup = "$BaseJar.lumi-to-gpt.bak"
        if (-not (Test-Path -LiteralPath $OriginalBackup -PathType Leaf)) {
            throw "패치되지 않은 Little LUMI 원본 백업을 찾지 못했습니다: $OriginalBackup"
        }
        $ReferenceJar = $OriginalBackup
    }
}
finally {
    $BaseArchive.Dispose()
}

$ResolvedPatchRoot = [IO.Path]::GetFullPath($PatchRoot).TrimEnd('\') + '\'
$ResolvedBuildRoot = [IO.Path]::GetFullPath($BuildRoot)
if (-not $ResolvedBuildRoot.StartsWith($ResolvedPatchRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw "빌드 폴더가 java-patch 밖입니다: $ResolvedBuildRoot"
}
if (Test-Path -LiteralPath $BuildRoot) {
    Remove-Item -LiteralPath $BuildRoot -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $ClassesRoot | Out-Null

$Sources = Get-ChildItem -LiteralPath (Join-Path $PatchRoot "src") -Recurse -Filter "*.java" | ForEach-Object FullName
& $Javac -encoding UTF-8 -cp $ReferenceJar -d $ClassesRoot $Sources
if ($LASTEXITCODE -ne 0) {
    throw "Little LUMI 연동 클래스 컴파일에 실패했습니다."
}

$Entries = @(
    "com/group_finity/mascot/lumi/SpeechDirector.class",
    "com/group_finity/mascot/lumi/ai/AiSettings.class",
    "com/group_finity/mascot/lumi/ai/AiSettingsDialog.class",
    'com/group_finity/mascot/lumi/ai/AiSettingsDialog$Page.class',
    'com/group_finity/mascot/lumi/ai/AiSettingsDialog$TabShell.class',
    "com/group_finity/mascot/lumi/ai/TtsClient.class",
    'com/group_finity/mascot/lumi/ai/TtsClient$Audio.class'
)

$Archive = [IO.Compression.ZipFile]::OpenRead([IO.Path]::GetFullPath($ReferenceJar))
$Hashes = try {
    foreach ($EntryName in $Entries) {
        $Entry = $Archive.GetEntry($EntryName)
        if (-not $Entry) { throw "원본 JAR에 클래스가 없습니다: $EntryName" }
        $Sha = [Security.Cryptography.SHA256]::Create()
        $Stream = $Entry.Open()
        try {
            $Hash = ([BitConverter]::ToString($Sha.ComputeHash($Stream))).Replace("-", "")
        }
        finally {
            $Stream.Dispose()
            $Sha.Dispose()
        }
        [ordered]@{ entry = $EntryName; sha256 = $Hash }
    }
}
finally {
    $Archive.Dispose()
}

$Utf8 = [Text.UTF8Encoding]::new($false)
[IO.File]::WriteAllText($HashPath, (($Hashes | ConvertTo-Json) + "`n"), $Utf8)
$BaseHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $ReferenceJar).Hash
$Marker = "name=LUMI to GPT Little LUMI integration`nversion=$Version`nbaseJarSha256=$BaseHash`n"
[IO.File]::WriteAllText($MarkerPath, $Marker, $Utf8)
if (Test-Path -LiteralPath $PatchJar) {
    Remove-Item -LiteralPath $PatchJar -Force
}
& $JarTool --create --file $PatchJar -C $ClassesRoot . -C $PatchRoot "META-INF/lumi-to-gpt-patch.properties"
if ($LASTEXITCODE -ne 0) {
    throw "Little LUMI 연동 패치 JAR 생성에 실패했습니다."
}

Write-Host "Java patch complete: $PatchJar"
