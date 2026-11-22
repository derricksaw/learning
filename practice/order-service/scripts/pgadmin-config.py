"""Loopback-only, single-user pgAdmin for Order Lab's rootless runner."""
from pathlib import Path

_APP = Path(__file__).resolve().parent.parent
SERVER_MODE = False
DEFAULT_SERVER = '127.0.0.1'
DEFAULT_SERVER_PORT = 5050
DATA_DIR = str(_APP / '.local/state/pgadmin')
MASTER_PASSWORD_REQUIRED = False
USE_OS_SECRET_STORAGE = False
UPGRADE_CHECK_ENABLED = False
DEFAULT_BINARY_PATHS = {'pg': str(_APP / '.local/tools/usr/lib/postgresql/17/bin')}
