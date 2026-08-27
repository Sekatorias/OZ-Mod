#!/usr/bin/env sh
set -eu
if ! command -v gradle >/dev/null 2>&1; then
  echo "Gradle не найден. Установите Gradle 8.x или откройте проект как Gradle project в IDE."
  exit 1
fi
gradle clean build
