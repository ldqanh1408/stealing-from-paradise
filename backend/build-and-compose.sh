#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_FILE="$ROOT_DIR/docker-compose.yml"

log() {
  echo "[INFO] $1"
}

ok() {
  echo "[OK] $1"
}

fail() {
  echo "[ERROR] $1" >&2
  exit 1
}

if [[ ! -f "$SCRIPT_DIR/pom.xml" ]]; then
  fail "Khong tim thay pom.xml trong backend"
fi

if [[ ! -f "$COMPOSE_FILE" ]]; then
  fail "Khong tim thay docker-compose.yml o project cha"
fi

if ! command -v mvn >/dev/null 2>&1; then
  fail "Maven (mvn) chua co trong PATH"
fi

if ! command -v docker >/dev/null 2>&1; then
  fail "Docker chua co trong PATH"
fi

COMPOSE_CMD=(docker compose)
if ! docker compose version >/dev/null 2>&1; then
  if command -v docker-compose >/dev/null 2>&1; then
    COMPOSE_CMD=(docker-compose)
  else
    fail "Khong tim thay docker compose hoac docker-compose"
  fi
fi

log "Build backend Maven projects..."
cd "$SCRIPT_DIR"
mvn clean install -DskipTests -U
ok "Backend build thanh cong"

log "Chay Docker Compose o project cha..."
cd "$ROOT_DIR"
"${COMPOSE_CMD[@]}" -f "$COMPOSE_FILE" down -v --remove-orphans
"${COMPOSE_CMD[@]}" -f "$COMPOSE_FILE" up -d --build
ok "Docker Compose da duoc khoi dong"

log "Trang thai container hien tai:"
"${COMPOSE_CMD[@]}" -f "$COMPOSE_FILE" ps
