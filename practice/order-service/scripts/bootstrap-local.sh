#!/usr/bin/env bash
set -euo pipefail
APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOOLS="$APP_DIR/.local/tools"
mkdir -p "$TOOLS/packages"
if ! command -v java >/dev/null || ! command -v node >/dev/null; then
  echo "Install Java 21+ and Node 22.12+ first." >&2
  exit 1
fi
if [[ ! -x "$TOOLS/usr/lib/postgresql/17/bin/pg_ctl" ]]; then
  if ! command -v apt-get >/dev/null || ! command -v dpkg-deb >/dev/null; then
    echo "Rootless bootstrap supports Debian with PostgreSQL 17 packages. Use Docker Compose on other systems." >&2
    exit 1
  fi
  (
    cd "$TOOLS/packages"
    apt-get download postgresql-17 postgresql-client-17 libpq5 libjemalloc2 liblzf1 libxxhash0
    for package in ./*.deb; do dpkg-deb -x "$package" "$TOOLS"; done
  )
fi
export LD_LIBRARY_PATH="$TOOLS/usr/lib/$(gcc -dumpmachine 2>/dev/null || echo x86_64-linux-gnu)${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
"$TOOLS/usr/lib/postgresql/17/bin/pg_ctl" --version
"$APP_DIR/scripts/install-redis.sh"
"$APP_DIR/scripts/install-pgadmin.sh"
(cd "$APP_DIR/frontend" && npm ci)
echo "Local tools installed under ignored .local/. Start with ./scripts/dev.sh up"
