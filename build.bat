@echo off
where gradle >nul 2>nul
if errorlevel 1 (
  echo Gradle ne naiden. Ustanovite Gradle 8.x ili otkroite proekt kak Gradle project v IDE.
  exit /b 1
)
gradle clean build
