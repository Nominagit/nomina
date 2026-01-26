#!/usr/bin/env bash
set -euo pipefail

# директория репозитория = место, где лежит этот скрипт
REPO_DIR="$(cd "$(dirname "$0")" && pwd)"

LOG_DIR="$REPO_DIR/logs"
STATE_DIR="$REPO_DIR/scrape-state"
LOG_FILE="$LOG_DIR/aipa.log"

mkdir -p "$LOG_DIR" "$STATE_DIR"

# простой лок (чтобы два запуска не шли параллельно)
LOCKDIR="$STATE_DIR/aipa.lock"
if ! mkdir "$LOCKDIR" 2>/dev/null; then
  echo "$(date) AIPA already running, skip" >> "$LOG_FILE"
  exit 0
fi
trap 'rmdir "$LOCKDIR"' EXIT

# cron не читает твой .zshrc/.bash_profile → задаём PATH (подкрути при необходимости)
export PATH="/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:$PATH"

# (опционально) зафиксировать Java, если надо
# export JAVA_HOME=$(/usr/libexec/java_home -v 17)

cd "$REPO_DIR"

{
  echo "===== $(date) START AIPA ====="
  mvn -q -Dtest=UpdateDataTest#scrapeAipa test
  echo "===== $(date) END AIPA ====="
  echo
} >> "$LOG_FILE" 2>&1
