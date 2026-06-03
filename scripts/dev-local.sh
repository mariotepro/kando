#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

SPRING_PROFILE="${SPRING_PROFILE:-local}"
SPRING_BOOT_CMD=(mvn spring-boot:run "-Dspring-boot.run.profiles=${SPRING_PROFILE}")

if ! command -v entr >/dev/null 2>&1; then
  echo "[dev] Missing required dependency: entr" >&2
  echo "[dev] Install it with: brew install entr" >&2
  exit 1
fi

watch_and_compile() {
  while true; do
    {
      printf '%s\n' pom.xml
      find src/main/java -type f
      find src/main/resources -type f \( -name 'application*.properties' -o -path 'src/main/resources/db/migration/*' \)
    } | sort | entr -d sh -c '
      echo "[dev] Recompiling changed sources..."
      mvn -q -DskipTests compile
    '
  done
}

kill_tree() {
  local pid="$1"
  local child_pid

  while read -r child_pid; do
    [[ -n "$child_pid" ]] || continue
    kill_tree "$child_pid"
  done < <(pgrep -P "$pid" || true)

  kill "$pid" 2>/dev/null || true
}

cleanup() {
  trap - EXIT INT TERM

  if [[ -n "${APP_PID:-}" ]] && kill -0 "$APP_PID" 2>/dev/null; then
    kill_tree "$APP_PID"
    wait "$APP_PID" 2>/dev/null || true
  fi

  if [[ -n "${WATCHER_PID:-}" ]] && kill -0 "$WATCHER_PID" 2>/dev/null; then
    kill_tree "$WATCHER_PID"
    wait "$WATCHER_PID" 2>/dev/null || true
  fi
}

trap cleanup EXIT INT TERM

echo "[dev] Starting background compiler watcher..."
watch_and_compile &
WATCHER_PID=$!

echo "[dev] Starting Spring Boot with profile '${SPRING_PROFILE}' and in-place resources..."
"${SPRING_BOOT_CMD[@]}" &
APP_PID=$!
wait "$APP_PID"
