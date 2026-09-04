param(
    [ValidateRange(1, 3600)]
    [int]$IdleSeconds = 5
)

$ErrorActionPreference = "Stop"

$AppPath = Join-Path $env:LOCALAPPDATA "LumiToGPT\app\lumi-to-gpt.exe"
$OutputPath = Join-Path $env:LOCALAPPDATA "LumiToGPT\gpt-sovits-self-test.wav"
$LogPath = Join-Path $env:LOCALAPPDATA "LumiToGPT\gpt-sovits.log"
$env:LUMI_GPT_SOVITS_IDLE_SECONDS = $IdleSeconds.ToString()

try {
    $Process = Start-Process -FilePath $AppPath -ArgumentList "--test-gpt-sovits" `
        -PassThru -Wait -WindowStyle Hidden
    if ($Process.ExitCode -ne 0) {
        throw "GPT-SoVITS lifecycle test exited with code $($Process.ExitCode)"
    }
    $Bytes = [IO.File]::ReadAllBytes($OutputPath)
    $TtsCompleted = (Test-Path -LiteralPath $LogPath) -and
        (Select-String -LiteralPath $LogPath -SimpleMatch "POST /tts" -Quiet)
    $PortClosed = -not (Test-NetConnection 127.0.0.1 -Port 9880 `
        -InformationLevel Quiet -WarningAction SilentlyContinue)
    $RemainingApiProcesses = @(
        Get-CimInstance Win32_Process |
            Where-Object { $_.Name -eq "python.exe" -and $_.CommandLine -like "*api_v2.py*" }
    ).Count
    $Result = [pscustomobject]@{
        ExitCode              = $Process.ExitCode
        WavBytes              = $Bytes.Length
        Riff                  = [Text.Encoding]::ASCII.GetString($Bytes, 0, 4)
        Wave                  = [Text.Encoding]::ASCII.GetString($Bytes, 8, 4)
        TtsCompleted          = $TtsCompleted
        IdleClosed            = $PortClosed
        RemainingApiProcesses = $RemainingApiProcesses
    }
    $Result | ConvertTo-Json
    if ($Result.Riff -ne "RIFF" -or $Result.Wave -ne "WAVE" -or
        -not $TtsCompleted -or -not $PortClosed -or $RemainingApiProcesses -ne 0) {
        throw "Live voice lifecycle verification failed"
    }
}
finally {
    Remove-Item Env:LUMI_GPT_SOVITS_IDLE_SECONDS -ErrorAction SilentlyContinue
}
