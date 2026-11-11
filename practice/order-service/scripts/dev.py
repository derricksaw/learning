#!/usr/bin/env python3
"""Rootless Linux development runner. Runtime state and credentials stay in .local/."""
import json
import os
from pathlib import Path
import platform
import secrets
import shlex
import signal
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
DATA = LOCAL / 'postgres'
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


def start(name, args, cwd=APP):
    if owned(name):
        return
    with (STATE / f'{name}.log').open('a') as output:
        proc = subprocess.Popen([str(arg) for arg in args], cwd=cwd, env=ENV,
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
    for name in ['frontend', 'api-b', 'api-a', 'redis']:
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


def up():
    if not (PG / 'pg_ctl').exists():
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
    start('redis', [TOOLS / 'usr/bin/redis-server', '--bind', '127.0.0.1', '--port', '6380',
                    '--save', '', '--appendonly', 'no'])
    for _ in range(40):
        ping = subprocess.run([str(TOOLS / 'usr/bin/redis-cli'), '-p', '6380', 'ping'],
                              env=ENV, capture_output=True, text=True)
        if ping.returncode == 0 and ping.stdout.strip() == 'PONG':
            break
        time.sleep(.1)
    else:
        raise RuntimeError('Redis failed to start; inspect .local/state/redis.log')
    if not owned('redis'):
        raise RuntimeError('Port 6380 belongs to another Redis. Stop the conflicting service first.')
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
    print('\nOrder Lab is running: http://127.0.0.1:5173')
    print('API A: http://127.0.0.1:8080 | API B: http://127.0.0.1:8081')
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
        for name in ['api-a', 'api-b', 'frontend', 'redis']:
            print(f'{name}: {"running" if owned(name) else "stopped"}')
    else:
        raise RuntimeError('Usage: ./scripts/dev.sh [up|down|restart|status]')


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, subprocess.CalledProcessError) as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
