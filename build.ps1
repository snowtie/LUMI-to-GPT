$ErrorActionPreference = "Stop"
if (Test-Path -LiteralPath variable:PSNativeCommandUseErrorActionPreference) {
    $PSNativeCommandUseErrorActionPreference = $true
}

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$TauriRoot = Join-Path $ProjectRoot "src-tauri"
$ReleaseRoot = Join-Path $ProjectRoot "release"
$Version = "0.8.3"
$ResolvedProjectRoot = [IO.Path]::GetFullPath($ProjectRoot).TrimEnd('\') + '\'
$ResolvedReleaseRoot = [IO.Path]::GetFullPath($ReleaseRoot)
if (-not $ResolvedReleaseRoot.StartsWith($ResolvedProjectRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw "release 폴더가 프로젝트 밖입니다: $ResolvedReleaseRoot"
}

Push-Location $TauriRoot
try {
    cargo tauri build --no-bundle
    if ($LASTEXITCODE -ne 0) { throw "Rust/Tauri 빌드에 실패했습니다." }
}
finally {
    Pop-Location
}

if (Test-Path -LiteralPath $ReleaseRoot) {
    Remove-Item -LiteralPath $ReleaseRoot -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $ReleaseRoot | Out-Null

$BuiltExecutable = Join-Path $TauriRoot "target\release\lumi-to-gpt.exe"
if (-not (Test-Path -LiteralPath $BuiltExecutable)) {
    throw "빌드 결과가 없습니다: $BuiltExecutable"
}
Copy-Item -LiteralPath $BuiltExecutable -Destination (Join-Path $ReleaseRoot "LUMI to GPT.exe")
Copy-Item -LiteralPath (Join-Path $ProjectRoot "README.md") -Destination $ReleaseRoot
Copy-Item -LiteralPath (Join-Path $ProjectRoot "RELEASE_NOTES.md") -Destination $ReleaseRoot
Copy-Item -LiteralPath (Join-Path $ProjectRoot "LICENSE") -Destination $ReleaseRoot
Copy-Item -LiteralPath (Join-Path $ProjectRoot "NOTICE.txt") -Destination $ReleaseRoot
Copy-Item -LiteralPath (Join-Path $ProjectRoot "install.ps1") -Destination $ReleaseRoot
Copy-Item -LiteralPath (Join-Path $ProjectRoot "INSTALL.cmd") -Destination $ReleaseRoot
$ReleasePatchRoot = Join-Path $ReleaseRoot "little-lumi-patch"
New-Item -ItemType Directory -Force -Path $ReleasePatchRoot | Out-Null
Copy-Item -LiteralPath (Join-Path $ProjectRoot "java-patch\lumi-to-gpt-little-lumi-patch.jar") -Destination $ReleasePatchRoot
Copy-Item -LiteralPath (Join-Path $ProjectRoot "java-patch\original-class-sha256.json") -Destination $ReleasePatchRoot

$WorkshopRoot = Join-Path $ReleaseRoot "workshop-content"
$WorkshopToolRoot = Join-Path $WorkshopRoot "LUMI-to-GPT"
New-Item -ItemType Directory -Force -Path $WorkshopToolRoot | Out-Null
Copy-Item -LiteralPath (Join-Path $ProjectRoot "workshop\README.txt") -Destination $WorkshopRoot
Copy-Item -LiteralPath (Join-Path $ReleaseRoot "LUMI to GPT.exe") -Destination $WorkshopToolRoot
Copy-Item -LiteralPath (Join-Path $ProjectRoot "README.md") -Destination $WorkshopToolRoot
Copy-Item -LiteralPath (Join-Path $ProjectRoot "RELEASE_NOTES.md") -Destination $WorkshopToolRoot
Copy-Item -LiteralPath (Join-Path $ProjectRoot "LICENSE") -Destination $WorkshopToolRoot
Copy-Item -LiteralPath (Join-Path $ProjectRoot "NOTICE.txt") -Destination $WorkshopToolRoot
Copy-Item -LiteralPath (Join-Path $ProjectRoot "install.ps1") -Destination $WorkshopToolRoot
Copy-Item -LiteralPath (Join-Path $ProjectRoot "INSTALL.cmd") -Destination $WorkshopToolRoot
Copy-Item -LiteralPath $ReleasePatchRoot -Destination $WorkshopToolRoot -Recurse
Copy-Item -LiteralPath (Join-Path $ProjectRoot "workshop\description.txt") -Destination (Join-Path $ReleaseRoot "workshop-description.txt")
Set-Content -LiteralPath (Join-Path $ReleaseRoot "workshop-dependency.txt") -Encoding utf8 -Value @(
    "Required Workshop item: LUMI Chat",
    "Workshop ID: 3794360578",
    "Set this item as a required item in the Steam Workshop page before publishing."
)

Add-Type -AssemblyName System.Drawing
$PreviewPath = Join-Path $ReleaseRoot "workshop-preview.png"
$Bitmap = [Drawing.Bitmap]::new(512, 512)
$Graphics = [Drawing.Graphics]::FromImage($Bitmap)
$Graphics.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::AntiAlias
$Graphics.Clear([Drawing.Color]::FromArgb(17, 24, 39))
$AccentBrush = [Drawing.SolidBrush]::new([Drawing.Color]::FromArgb(16, 163, 127))
$WhiteBrush = [Drawing.SolidBrush]::new([Drawing.Color]::White)
$MutedBrush = [Drawing.SolidBrush]::new([Drawing.Color]::FromArgb(203, 213, 225))
$TitleFont = [Drawing.Font]::new("Segoe UI", 46, [Drawing.FontStyle]::Bold)
$SubFont = [Drawing.Font]::new("Segoe UI", 19, [Drawing.FontStyle]::Regular)
try {
    $Graphics.FillEllipse($AccentBrush, 46, 54, 92, 92)
    $Graphics.DrawString("+", $TitleFont, $WhiteBrush, 66, 60)
    $Graphics.DrawString("LUMI Chat", $TitleFont, $WhiteBrush, 46, 185)
    $Graphics.DrawString("to GPT", $TitleFont, $AccentBrush, 46, 245)
    $Graphics.DrawString("ChatGPT Web bridge", $SubFont, $MutedBrush, 50, 356)
    $Graphics.DrawString("Requires LUMI Chat", $SubFont, $MutedBrush, 50, 396)
    $Bitmap.Save($PreviewPath, [Drawing.Imaging.ImageFormat]::Png)
}
finally {
    $Graphics.Dispose()
    $Bitmap.Dispose()
    $AccentBrush.Dispose()
    $WhiteBrush.Dispose()
    $MutedBrush.Dispose()
    $TitleFont.Dispose()
    $SubFont.Dispose()
}

$VersionedPackageName = "LUMI-to-GPT-v$Version-windows-x64.zip"
$VersionedPackagePath = Join-Path $ReleaseRoot $VersionedPackageName
$PackagePath = Join-Path $ReleaseRoot "LUMI-to-GPT.zip"
$PackageFiles = @(
    (Join-Path $ReleaseRoot "LUMI to GPT.exe"),
    (Join-Path $ReleaseRoot "README.md"),
    (Join-Path $ReleaseRoot "RELEASE_NOTES.md"),
    (Join-Path $ReleaseRoot "LICENSE"),
    (Join-Path $ReleaseRoot "NOTICE.txt"),
    (Join-Path $ReleaseRoot "install.ps1"),
    (Join-Path $ReleaseRoot "INSTALL.cmd"),
    $ReleasePatchRoot
)
Compress-Archive -LiteralPath $PackageFiles -DestinationPath $VersionedPackagePath -CompressionLevel Optimal
Copy-Item -LiteralPath $VersionedPackagePath -Destination $PackagePath

$ChecksumTargets = @(
    (Join-Path $ReleaseRoot "LUMI to GPT.exe"),
    (Join-Path $ReleasePatchRoot "lumi-to-gpt-little-lumi-patch.jar"),
    $VersionedPackagePath
)
$ChecksumLines = foreach ($Target in $ChecksumTargets) {
    $Hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $Target).Hash.ToLowerInvariant()
    "$Hash  $([IO.Path]::GetFileName($Target))"
}
$Utf8 = [Text.UTF8Encoding]::new($false)
[IO.File]::WriteAllLines((Join-Path $ReleaseRoot "SHA256SUMS.txt"), $ChecksumLines, $Utf8)

Write-Host "Build complete: $ReleaseRoot"
