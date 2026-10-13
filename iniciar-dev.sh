#!/usr/bin/env bash
set -euo pipefail
PROJECT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
if ! command -v python3 >/dev/null 2>&1; then
  echo "Error: se requiere Python 3.10 o posterior para los scripts de operación." >&2
  exit 1
fi
exec python3 "$PROJECT_DIR/scripts/manage.py" dev "$@"
