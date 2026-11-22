#!/usr/bin/env python3
"""Rootless Linux development runner. Runtime state and credentials stay in .local/."""
import json
import os
from pathlib import Path
import platform
import secrets
import shlex
import signal
import socket
import sqlite3
import subprocess
import sys
import time
import urllib.error
import urllib.request

APP = Path(__file__).resolve().parent.parent
LOCAL = APP / '.local'
STATE = LOCAL / 'state'
TOOLS = LOCAL / 'tools'
PG = TOOLS / 'usr/lib/postgresql/17/bin'
REDIS_VERSION = (APP / '.redis-version').read_text().strip()
REDIS = TOOLS / 'redis' / REDIS_VERSION / 'bin'
DATA = LOCAL / 'postgres'
PGADMIN_VERSION = (APP / '.pgadmin-version').read_text().strip()
PGADMIN_PYTHON = TOOLS / 'pgadmin' / PGADMIN_VERSION / 'bin/python'
ENV = os.environ.copy()
arch = {'x86_64': 'x86_64-linux-gnu', 'aarch64': 'aarch64-linux-gnu'}.get(platform.machine(), platform.machine())
ENV['LD_LIBRARY_PATH'] = str(TOOLS / 'usr/lib' / arch) + ':' + ENV.get('LD_LIBRARY_PATH', '')
ENV['PATH'] = str(PG) + ':' + str(TOOLS / 'usr/bin') + ':' + ENV['PATH']
STATE.mkdir(parents=True, exist_ok=True)


def signature(pid):
    try:
        fields = Path(f'/proc/{pid}/stat').read_text().rsplit(')', 1)[1].split()
        return fields[19] if fields[0] != 'Z' else None
    except (OSError, IndexError):
        return None


def owned(name):
    path = STATE / f'{name}.pid'
    if not path.exists():
        return None
    data = json.loads(path.read_text())
    current = signature(data['pid'])
    return data['pid'] if current is not None and current == data['signature'] else None


def run(*args, **kwargs):
    return subprocess.run([str(arg) for arg in args], cwd=APP, env=ENV, check=True, **kwargs)


def start(name, args, cwd=APP, env=None):
    if owned(name):
        return
    with (STATE / f'{name}.log').open('a') as output:
        proc = subprocess.Popen([str(arg) for arg in args], cwd=cwd, env=ENV if env is None else env,
                                stdout=output, stderr=subprocess.STDOUT, start_new_session=True)
    (STATE / f'{name}.pid').write_text(json.dumps({'pid': proc.pid, 'signature': signature(proc.pid)}))


def wait_ready(name, url, seconds=60):
    for _ in range(seconds * 4):
        if not owned(name):
            raise RuntimeError(f'{name} exited. Inspect {STATE / (name + ".log")}')
        try:
            with urllib.request.urlopen(url, timeout=2) as response:
                if response.status == 200:
                    return
        except (OSError, urllib.error.URLError):
            pass
        time.sleep(.25)
    raise RuntimeError(f'{name} was not ready at {url}. Inspect its log in {STATE}')


def stop():
    for name in ['pgadmin', 'frontend', 'api-b', 'api-a', 'redis']:
        pid = owned(name)
        if pid:
            try:
                os.killpg(pid, signal.SIGTERM)
            except ProcessLookupError:
                continue
            for _ in range(40):
                if not owned(name):
                    break
                time.sleep(.1)
            if owned(name):
                os.killpg(pid, signal.SIGKILL)
        (STATE / f'{name}.pid').unlink(missing_ok=True)
    if (DATA / 'PG_VERSION').exists():
        subprocess.run([str(PG / 'pg_ctl'), '-D', str(DATA), '-m', 'fast', 'stop'],
                       cwd=APP, env=ENV, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    print('Workspace stopped. Database files are preserved in .local/postgres/.')


def start_pgadmin(password):
    if owned('pgadmin'):
        return
    with socket.socket() as listener:
        try:
            listener.bind(('127.0.0.1', 5050))
        except OSError as error:
            raise RuntimeError('Port 5050 is already in use; stop the conflicting service before starting pgAdmin.') from error
    run(APP / 'scripts/install-pgadmin.sh')
    pgadmin_state = STATE / 'pgadmin'
    pgadmin_state.mkdir(mode=0o700, exist_ok=True)
    pgadmin_state.chmod(0o700)
    passfile = pgadmin_state / 'pgpass'
    escaped = password.replace(chr(92), chr(92) * 2).replace(':', chr(92) + ':')
    descriptor = os.open(passfile, os.O_CREAT | os.O_TRUNC | os.O_WRONLY, 0o600)
    with os.fdopen(descriptor, 'w') as output:
        output.write('127.0.0.1:5433:*:orders:' + escaped + chr(10))
    passfile.chmod(0o600)
    servers = pgadmin_state / 'servers.json'
    servers.write_text(json.dumps({'Servers': {'1': {
        'Name': 'Order Lab', 'Group': 'Local development',
        'Host': '127.0.0.1', 'Port': 5433, 'Username': 'orders',
        'MaintenanceDB': 'orders', 'DBRestriction': 'orders',
        'ConnectionParameters': {'sslmode': 'prefer', 'passfile': str(passfile)},
    }}}))
    pgadmin_env = ENV.copy()
    pgadmin_env.pop('PGPASSWORD', None)
    pgadmin_env.pop('DB_PASSWORD', None)
    pgadmin_env['CONFIG_DISTRO_FILE_PATH'] = str(APP / 'scripts/pgadmin-config.py')
    package_dir = subprocess.run(
        [str(PGADMIN_PYTHON), '-c',
         'import importlib.util; print(next(iter(importlib.util.find_spec("pgadmin4").submodule_search_locations)))'],
        env=pgadmin_env, check=True, capture_output=True, text=True).stdout.strip()
    config_db = pgadmin_state / 'pgadmin4.db'
    if not config_db.exists():
        subprocess.run([str(PGADMIN_PYTHON), str(Path(package_dir) / 'setup.py'),
                        'setup-db'], cwd=APP, env=pgadmin_env, check=True)

    def registered():
        with sqlite3.connect(config_db) as database:
            return database.execute(
                "SELECT 1 FROM server WHERE name = ? AND host = ? AND port = ? AND username = ?",
                ('Order Lab', '127.0.0.1', 5433, 'orders')).fetchone() is not None

    # Add only the supplied connection; preserve user-created registrations on restart.
    if not registered():
        subprocess.run([str(PGADMIN_PYTHON), str(Path(package_dir) / 'setup.py'),
                        'load-servers', str(servers)], cwd=APP, env=pgadmin_env, check=True)
        if not registered():
            raise RuntimeError('pgAdmin could not import the Order Lab connection; inspect startup output.')
    start('pgadmin', [PGADMIN_PYTHON, Path(package_dir) / 'pgAdmin4.py'], env=pgadmin_env)
    wait_ready('pgadmin', 'http://127.0.0.1:5050/misc/ping')


def up():
    if not (PG / 'pg_ctl').exists() or not (REDIS / 'redis-server').exists():
        raise RuntimeError('Run ./scripts/bootstrap-local.sh first, or use Docker Compose.')
    password_file = STATE / 'database-password'
    if not password_file.exists():
        descriptor = os.open(password_file, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
        with os.fdopen(descriptor, 'w') as output:
            output.write(secrets.token_hex(24))
    password = password_file.read_text().strip()
    ENV.update(DB_PASSWORD=password, PGPASSWORD=password,
               DB_URL='jdbc:postgresql://127.0.0.1:5433/orders', DB_USERNAME='orders', REDIS_PORT='6380')
    fresh = not (DATA / 'PG_VERSION').exists()
    if fresh:
        run(PG / 'initdb', '-D', DATA, '-U', 'orders', '-A', 'scram-sha-256',
            '--pwfile=' + str(password_file), '--encoding=UTF8', '--locale=C.UTF-8',
            '-L', TOOLS / 'usr/share/postgresql/17')
    status = subprocess.run([str(PG / 'pg_ctl'), '-D', str(DATA), 'status'], env=ENV,
                            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    if status.returncode:
        run(PG / 'pg_ctl', '-D', DATA, '-l', STATE / 'postgres.log',
            '-o', f'-h 127.0.0.1 -p 5433 -k {shlex.quote(str(STATE))}', '-w', 'start')
    database = run(PG / 'psql', '-h', '127.0.0.1', '-p', '5433', '-U', 'orders', '-d', 'postgres',
                   '-tAc', "SELECT 1 FROM pg_database WHERE datname = 'orders'", capture_output=True, text=True)
    if database.stdout.strip() != '1':
        run(PG / 'createdb', '-h', '127.0.0.1', '-p', '5433', '-U', 'orders', 'orders')
    start('redis', [REDIS / 'redis-server', '--bind', '127.0.0.1', '--port', '6380',
                    '--save', '', '--appendonly', 'no'])
    for _ in range(40):
        ping = subprocess.run([str(REDIS / 'redis-cli'), '-p', '6380', 'ping'],
                              env=ENV, capture_output=True, text=True)
        if ping.returncode == 0 and ping.stdout.strip() == 'PONG':
            break
        time.sleep(.1)
    else:
        raise RuntimeError('Redis failed to start; inspect .local/state/redis.log')
    if not owned('redis'):
        raise RuntimeError('Port 6380 belongs to another Redis. Stop the conflicting service first.')
    server_info = run(REDIS / 'redis-cli', '-p', '6380', 'INFO', 'server', capture_output=True, text=True)
    if f'redis_version:{REDIS_VERSION}' not in server_info.stdout.splitlines():
        raise RuntimeError(f'Running Redis is not {REDIS_VERSION}; run ./scripts/dev.sh restart after bootstrap.')
    if not owned('api-a') and not owned('api-b'):
        run('./mvnw', '-DskipTests', 'package')
    jar = APP / 'target/order-service-0.0.1-SNAPSHOT.jar'
    for name, port in [('api-a', 8080), ('api-b', 8081)]:
        start(name, ['java', '-Xmx256m', '-XX:ActiveProcessorCount=2', '-jar', jar,
                     '--spring.profiles.active=dev', f'--server.port={port}'])
        wait_ready(name, f'http://127.0.0.1:{port}/actuator/health')
    if not (APP / 'frontend/node_modules').exists():
        run('npm', '--prefix', 'frontend', 'ci')
    start('frontend', ['npm', 'run', 'dev'], APP / 'frontend')
    wait_ready('frontend', 'http://127.0.0.1:5173/')
    start_pgadmin(password)
    print('\nOrder Lab is running: http://127.0.0.1:5173')
    print('API A: http://127.0.0.1:8080 | API B: http://127.0.0.1:8081')
    print('pgAdmin: http://127.0.0.1:5050 | Server: Order Lab | Database: orders')
    print('Logs: practice/order-service/.local/state/ | Stop: ./scripts/dev.sh down')


def main():
    command = sys.argv[1] if len(sys.argv) > 1 else 'up'
    if command == 'up':
        up()
    elif command == 'down':
        stop()
    elif command == 'restart':
        stop()
        up()
    elif command == 'status':
        for name in ['api-a', 'api-b', 'frontend', 'redis', 'pgadmin']:
            print(f'{name}: {"running" if owned(name) else "stopped"}')
    else:
        raise RuntimeError('Usage: ./scripts/dev.sh [up|down|restart|status]')


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, subprocess.CalledProcessError) as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
