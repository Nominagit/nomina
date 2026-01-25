#!/usr/bin/env bash
set -euo pipefail

# перейти в папку, где лежит сам скрипт (то есть корень репо)
cd "$(cd "$(dirname "$0")" && pwd)"

mkdir -p logs
exec >> "logs/wipo.log" 2>&1

echo "===== $(date) START WIPO ====="

# простой лок на macOS без flock
LOCKDIR="/tmp/wipo_scraper_lock"
if ! mkdir "$LOCKDIR" 2>/dev/null; then
  echo "Another run is in progress. Exit."
  exit 0
fi
trap 'rmdir "$LOCKDIR"' EXIT

# ВАЖНО: добавь путь к brew (если mvn/java установлены через Homebrew)
export PATH="/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:$PATH"

# запуск теста (Maven)
mvn -q -Dtest=UpdateDataTest#scrapeWipoARM test

echo "===== $(date) END WIPO ====="
