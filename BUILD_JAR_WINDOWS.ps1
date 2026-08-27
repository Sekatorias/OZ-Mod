$ErrorActionPreference = "Stop"

Write-Host "=== OZ Fabric 1.20.1 build ===" -ForegroundColor Cyan

# Check Java
try {
    $javaVersionText = (& java -version 2>&1 | Out-String)
} catch {
    Write-Host "Java was not found. Install Java/JDK 17 and run this script again." -ForegroundColor Red
    exit 1
}

Write-Host $javaVersionText
if ($javaVersionText -notmatch 'version "17\.' -and $javaVersionText -notmatch 'openjdk version "17\.') {
    Write-Host "Warning: this project targets Java 17. If the build fails, install JDK 17 and make it the active JAVA_HOME." -ForegroundColor Yellow
}

$ProjectDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ToolsDir = Join-Path $ProjectDir ".build-tools"
$GradleVersion = "8.7"
$GradleZip = Join-Path $ToolsDir "gradle-$GradleVersion-bin.zip"
$GradleHome = Join-Path $ToolsDir "gradle-$GradleVersion"
$GradleExe = Join-Path $GradleHome "bin\gradle.bat"

New-Item -ItemType Directory -Force -Path $ToolsDir | Out-Null

if (-not (Test-Path $GradleExe)) {
    Write-Host "Downloading Gradle $GradleVersion..." -ForegroundColor Cyan
    Invoke-WebRequest -Uri "https://services.gradle.org/distributions/gradle-$GradleVersion-bin.zip" -OutFile $GradleZip
    Write-Host "Extracting Gradle..." -ForegroundColor Cyan
    Expand-Archive -Path $GradleZip -DestinationPath $ToolsDir -Force
}

Write-Host "Building and remapping Fabric mod..." -ForegroundColor Cyan
Push-Location $ProjectDir
try {
    & $GradleExe clean build --stacktrace
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed with exit code $LASTEXITCODE" }
} finally {
    Pop-Location
}

$Jar = Get-ChildItem -Path (Join-Path $ProjectDir "build\libs") -Filter "*.jar" |
    Where-Object { $_.Name -notlike "*-sources.jar" -and $_.Name -notlike "*-dev.jar" } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

if ($null -eq $Jar) {
    Write-Host "Build completed, but no remapped jar was found in build\libs." -ForegroundColor Red
    exit 1
}

Write-Host "" 
Write-Host "SUCCESS" -ForegroundColor Green
Write-Host "JAR: $($Jar.FullName)" -ForegroundColor Green
Write-Host "Put this file in the server's mods folder together with Fabric API." -ForegroundColor Green
