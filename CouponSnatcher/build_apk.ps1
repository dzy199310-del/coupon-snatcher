#Requires -Version 5.1
<#
 .SYNOPSIS
   One-click build of the CouponSnatcher APK on Windows.
   Auto-downloads JDK17 + Android SDK (cmdline-tools) + Gradle, then produces app-debug.apk.
   NOTE: if your network cannot reach Google / Adoptium, this fails at the Android SDK step.
   In that case, use the GitHub cloud build described in README (section: GitHub cloud build).
#>
$ErrorActionPreference = "Stop"
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$Root   = Split-Path -Parent $MyInvocation.MyCommand.Path
$Tools  = Join-Path $Root "build-tools"
New-Item -ItemType Directory -Force -Path $Tools | Out-Null

function Download-Zip {
    param($Name, $Url)
    $zip = Join-Path $Tools "$Name.zip"
    if (Test-Path $zip) { return $zip }
    Write-Host "[download] $Name ..."
    try {
        Invoke-WebRequest -Uri $Url -OutFile $zip -UseBasicParsing
    } catch {
        Write-Host "!! CANNOT download $Name from:"
        Write-Host "   $Url"
        Write-Host "   Your network cannot reach this server."
        Write-Host "   >>> Use the GitHub cloud build instead (see README, section: GitHub cloud build). <<<"
        exit 1
    }
    return $zip
}

# 1) JDK 17 (Tsinghua mirror - reachable in China)
$jdkDir = Join-Path $Tools "jdk"
if (-not (Test-Path (Join-Path $jdkDir "bin\javac.exe"))) {
    $zip = Download-Zip "jdk" "https://mirrors.tuna.tsinghua.edu.cn/Adoptium/17/jdk/x64/windows/OpenJDK17U-jdk_x64_windows_hotspot_17.0.20_8.zip"
    Write-Host "[extract] JDK ..."
    Expand-Archive -Path $zip -DestinationPath $Tools -Force
    $ext = Get-ChildItem $Tools -Directory | Where-Object { $_.Name -like "jdk-*" } | Select-Object -First 1
    if ($ext) {
        if (Test-Path $jdkDir) { Remove-Item $jdkDir -Recurse -Force }
        Move-Item $ext.FullName $jdkDir
    }
    Remove-Item $zip
}
$env:JAVA_HOME = $jdkDir
$env:PATH = "$jdkDir\bin;" + $env:PATH
Write-Host "[OK] JAVA_HOME = $jdkDir"

# 2) Android SDK cmdline-tools (from Google; if blocked, see README GitHub cloud build)
$sdkRoot    = Join-Path $Tools "sdk"
$cmdlineDir = Join-Path $sdkRoot "cmdline-tools\latest"
if (-not (Test-Path (Join-Path $cmdlineDir "bin\sdkmanager.bat"))) {
    $zip = Download-Zip "cmdtools" "https://dl.google.com/android/repository/commandline-tools-win-11076708_latest.zip"
    Write-Host "[extract] cmdline-tools ..."
    Expand-Archive -Path $zip -DestinationPath $Tools -Force
    $src = Join-Path $Tools "cmdline-tools"
    if (Test-Path $src) {
        New-Item -ItemType Directory -Force -Path $cmdlineDir | Out-Null
        Move-Item (Join-Path $src "*") $cmdlineDir -Force
        Remove-Item $src -Recurse -Force
    }
    Remove-Item $zip
}
$env:ANDROID_HOME     = $sdkRoot
$env:ANDROID_SDK_ROOT = $sdkRoot
$sdkmanager = Join-Path $cmdlineDir "bin\sdkmanager.bat"

$licDir = Join-Path $sdkRoot "licenses"
New-Item -ItemType Directory -Force -Path $licDir | Out-Null
Set-Content (Join-Path $licDir "android-sdk-license")         "24333f8a63b6825ea9c5514f83c2829b004d1fee"
Set-Content (Join-Path $licDir "android-sdk-preview-license") "84831b9409646a918e30573bab4c9c91346d8abd"

$packages = 'platforms;android-34','build-tools;34.0.0','platform-tools'
foreach ($p in $packages) {
    Write-Host "[sdk] install $p ..."
    try {
        cmd /c "echo y | `"$sdkmanager`" --sdk_root=`"$sdkRoot`" `"$p`""
    } catch {
        Write-Host "!! SDK package install failed (network blocked)."
        Write-Host "   >>> Use the GitHub cloud build instead (see README, section: GitHub cloud build). <<<"
        exit 1
    }
}

Set-Content (Join-Path $Root "local.properties") ("sdk.dir=" + $sdkRoot.Replace('\','/'))
Write-Host "[OK] ANDROID_HOME = $sdkRoot"

# 3) Gradle 8.9 (HuaweiCloud mirror - reachable in China)
$gradleDir = Join-Path $Tools "gradle"
if (-not (Test-Path (Join-Path $gradleDir "bin\gradle.bat"))) {
    $zip = Download-Zip "gradle" "https://mirrors.huaweicloud.com/gradle/gradle-8.9-bin.zip"
    Write-Host "[extract] Gradle ..."
    Expand-Archive -Path $zip -DestinationPath $Tools -Force
    $ext = Get-ChildItem $Tools -Directory | Where-Object { $_.Name -like "gradle-8.9*" } | Select-Object -First 1
    if ($ext) {
        if (Test-Path $gradleDir) { Remove-Item $gradleDir -Recurse -Force }
        Move-Item $ext.FullName $gradleDir
    }
    Remove-Item $zip
}
$gradle = Join-Path $gradleDir "bin\gradle.bat"

# 4) Build
Write-Host ""
Write-Host "========== Building app-debug.apk =========="
cmd /c "`"$gradle`" -p `"$Root`" assembleDebug --no-daemon"

$apk = Join-Path $Root "app\build\outputs\apk\debug\app-debug.apk"
if (Test-Path $apk) {
    Write-Host ""
    Write-Host "==========================================="
    Write-Host "  BUILD SUCCESS! APK created at:"
    Write-Host "  $apk"
    Write-Host "==========================================="
    try { Invoke-Item (Split-Path $apk) } catch {}
} else {
    Write-Host "!! BUILD FAILED - check the gradle output above."
    Write-Host "   If it failed downloading dependencies, use the GitHub cloud build (README GitHub cloud build)."
    exit 1
}
