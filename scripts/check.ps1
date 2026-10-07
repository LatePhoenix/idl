# Windows entry point for the same checks as scripts/check.sh.
# check.sh is canonical. Use this when bash is not on PATH.
#   scripts/check.ps1            unit tests + snapshot verify + lint + debug build
#   scripts/check.ps1 -Sql       + Supabase SQL suite and Kotlin-PostgREST IT (needs Docker and bash)
#   scripts/check.ps1 -Device    + instrumented tests (emulator; set ANDROID_SERIAL)
#   scripts/check.ps1 -All       everything
param(
    [switch]$Sql,
    [switch]$Device,
    [switch]$All,
    [Parameter(ValueFromRemainingArguments = $true)][string[]]$Rest
)
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")
foreach ($arg in $Rest) {
    switch ($arg) {
        "--sql" { $Sql = $true }
        "--device" { $Device = $true }
        "--all" { $All = $true }
        default { Write-Error "unknown option $arg"; exit 2 }
    }
}
if ($All) { $Sql = $true; $Device = $true }

function Invoke-Native {
    param([scriptblock]$Command)
    & $Command
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

Write-Output "== Picture pipeline"
Invoke-Native { python -m unittest discover -s tools/tests -q }
Invoke-Native { python tools/asset_pipeline.py check }
Invoke-Native { python tools/gen_asset_catalog.py check }

Write-Output "== Gradle: unit tests, snapshot verify, lint, debug build"
Invoke-Native { .\gradlew.bat verifyRoborazziDebug lintDebug assembleDebug --console=plain -q }

function Summarize([string]$Dir) {
    $code = @'
import glob, re, sys
t = f = s = 0
for x in glob.glob(sys.argv[1] + "/*.xml"):
    text = open(x, encoding="utf-8").read()
    m = re.search(r'tests="(\d+)" skipped="(\d+)" failures="(\d+)" errors="(\d+)"', text)
    if m:
        t += int(m[1]); s += int(m[2]); f += int(m[3]) + int(m[4])
print(f"   {t} tests, {f} failed, {s} skipped")
'@
    $code | python - $Dir
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
Summarize "app/build/test-results/testDebugUnitTest"
$lint = Get-ChildItem -Path app/build -Recurse -Filter lint-results-debug.txt -ErrorAction SilentlyContinue | Select-Object -First 1
if ($lint) {
    $line = Get-Content -LiteralPath $lint.FullName -Tail 1
    Write-Output "   lint: $line"
}

if ($Sql) {
    docker info *> $null
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Docker is not running: start Docker Desktop, then rerun with -Sql."
        exit 1
    }
    $gitBash = "C:\Program Files\Git\bin\bash.exe"
    if (Test-Path $gitBash) {
        $bash = $gitBash
    } else {
        $cmd = Get-Command bash -ErrorAction SilentlyContinue
        $bash = if ($cmd) { $cmd.Source } else { $null }
    }
    if (-not $bash) {
        Write-Error "bash is not on PATH. Install Git for Windows, or run scripts/check.sh."
        exit 1
    }
    Write-Output "== Supabase SQL suite + PostgREST"
    Invoke-Native { & $bash supabase/tests/run.sh --rest }
    try {
        Write-Output "== Kotlin client against PostgREST"
        Invoke-Native { .\gradlew.bat testDebugUnitTest --tests '*SupabaseRestIT*' -Pidl.postgrestUrl=http://localhost:54330 --console=plain -q }
    } finally {
        & $bash supabase/tests/run.sh --down *> $null
    }
}

function Invoke-Adb {
    if ($env:ANDROID_SERIAL) {
        & adb -s $env:ANDROID_SERIAL @args
    } else {
        & adb @args
    }
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

if ($Device) {
    Write-Output "== Wait for the device to finish booting"
    Invoke-Adb wait-for-device
    $boot = ""
    foreach ($i in 1..30) {
        $boot = (Invoke-Adb shell getprop sys.boot_completed).Trim()
        if ($boot -eq "1") { break }
        Start-Sleep -Seconds 2
    }
    if ($boot -ne "1") {
        Write-Error "device did not finish booting"
        exit 1
    }
    Invoke-Adb shell input keyevent 82
    $target = if ($env:ANDROID_SERIAL) { $env:ANDROID_SERIAL } else { "the attached device" }
    Write-Output "== Instrumented tests on $target"
    Invoke-Native { .\gradlew.bat connectedDebugAndroidTest --console=plain -q }
}

Write-Output "All checks passed."
