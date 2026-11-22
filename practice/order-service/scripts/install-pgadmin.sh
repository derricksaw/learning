#!/usr/bin/env bash
set -euo pipefail
APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PGADMIN_VERSION="$(cat "$APP_DIR/.pgadmin-version")"
PGADMIN_ENV="$APP_DIR/.local/tools/pgadmin/$PGADMIN_VERSION"
PGADMIN_PYTHON="$PGADMIN_ENV/bin/python"

if [[ -x "$PGADMIN_PYTHON" ]] && "$PGADMIN_PYTHON" -c \
  'import importlib.metadata, sys; sys.exit(importlib.metadata.version("pgadmin4") != sys.argv[1])' \
  "$PGADMIN_VERSION" 2>/dev/null; then
  echo "pgAdmin $PGADMIN_VERSION is ready."
  exit 0
fi

if command -v uv >/dev/null; then
  [[ -x "$PGADMIN_PYTHON" ]] || uv venv "$PGADMIN_ENV"
  uv pip install --python "$PGADMIN_PYTHON" "pgadmin4==$PGADMIN_VERSION"
else
  if ! python3 -m venv "$PGADMIN_ENV"; then
    echo "pgAdmin needs Python venv support (python3-venv on Debian), or uv. Install either and retry." >&2
    exit 1
  fi
  "$PGADMIN_PYTHON" -m pip install "pgadmin4==$PGADMIN_VERSION"
fi
