#!/usr/bin/env bash
set -euo pipefail
APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REDIS_VERSION="$(cat "$APP_DIR/.redis-version")"
TOOLS="$APP_DIR/.local/tools"
SOURCES="$TOOLS/sources"
INSTALL="$TOOLS/redis/$REDIS_VERSION"
if [[ -x "$INSTALL/bin/redis-server" ]] && "$INSTALL/bin/redis-server" --version | grep -Fq "v=$REDIS_VERSION "; then
  "$INSTALL/bin/redis-server" --version
  exit 0
fi
for tool in curl tar sha256sum make gcc; do
  if ! command -v "$tool" >/dev/null; then
    echo "Redis source installation needs $tool. Install the build tools or use Docker Compose." >&2
    exit 1
  fi
done
mkdir -p "$SOURCES"
cd "$SOURCES"
ARCHIVE="redis-$REDIS_VERSION.tar.gz"
if [[ ! -f "$ARCHIVE" ]]; then
  curl --fail --location --retry 3 "https://download.redis.io/releases/$ARCHIVE" -o "$ARCHIVE"
fi
# Published upstream checksum: https://github.com/redis/redis-hashes/blob/master/README
sha256sum --check "$APP_DIR/scripts/redis-source.sha256"
tar -xzf "$ARCHIVE"
make -C "redis-$REDIS_VERSION" -j"${REDIS_BUILD_JOBS:-2}" MALLOC=libc OPTIMIZATION=-O2
make -C "redis-$REDIS_VERSION" MALLOC=libc OPTIMIZATION=-O2 PREFIX="$INSTALL" install
"$INSTALL/bin/redis-server" --version
