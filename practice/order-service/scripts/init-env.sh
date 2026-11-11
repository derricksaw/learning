#!/usr/bin/env bash
set -euo pipefail
APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
python3 - "$APP_DIR/.env" <<'PY'
import os
from pathlib import Path
import secrets
import sys
path = Path(sys.argv[1])
if not path.exists():
    descriptor = os.open(path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    with os.fdopen(descriptor, 'w') as output:
        output.write('DB_PASSWORD=' + secrets.token_hex(24) + '\n')
print('Compose configuration is ready. The local .env is ignored by Git.')
PY
