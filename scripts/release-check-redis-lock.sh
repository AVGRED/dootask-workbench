#!/usr/bin/env bash

# Local smoke test for Laravel's Redis cache lock across independent PHP processes.
# This does not replace a two-application-instance or multi-host release test.
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
redis_port="${REDIS_TEST_PORT:-16379}"
temp_parent="${TMPDIR:-/tmp}"
temp_dir=""
holder_pid=""
contender_pid=""
redis_started=0

cleanup() {
    if [[ -n "$holder_pid" ]]; then kill "$holder_pid" 2>/dev/null || true; wait "$holder_pid" 2>/dev/null || true; fi
    if [[ -n "$contender_pid" ]]; then kill "$contender_pid" 2>/dev/null || true; wait "$contender_pid" 2>/dev/null || true; fi
    if [[ "$redis_started" == 1 ]]; then
        redis-cli -h 127.0.0.1 -p "$redis_port" shutdown nosave >/dev/null 2>&1 || true
    fi
    if [[ -n "$temp_dir" && "$temp_dir" == "$temp_parent"/dootask-release-redis-lock.* ]]; then
        rm -rf -- "$temp_dir"
    fi
}

trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

for command in php redis-server redis-cli; do
    command -v "$command" >/dev/null 2>&1 || { echo "Required command not found: $command" >&2; exit 2; }
done

if [[ -f "$project_root/bootstrap/cache/config.php" ]]; then
    echo "Refusing to run with cached Laravel config; clear the config cache or use a clean worktree." >&2
    exit 2
fi

if redis-cli -h 127.0.0.1 -p "$redis_port" ping >/dev/null 2>&1; then
    echo "Refusing to use Redis port $redis_port because it is already active." >&2
    exit 2
fi

temp_dir="$(mktemp -d "$temp_parent/dootask-release-redis-lock.XXXXXX")"
redis-server \
    --bind 127.0.0.1 \
    --protected-mode yes \
    --port "$redis_port" \
    --save '' \
    --appendonly no \
    --dir "$temp_dir" \
    --daemonize yes \
    --pidfile "$temp_dir/redis.pid" \
    --logfile "$temp_dir/redis.log"
redis_started=1

ready=0
for _ in $(seq 1 50); do
    if redis-cli -h 127.0.0.1 -p "$redis_port" ping >/dev/null 2>&1; then ready=1; break; fi
    sleep 0.1
done
if [[ "$ready" != 1 ]]; then
    echo "Temporary Redis did not become ready." >&2
    exit 3
fi

cat > "$temp_dir/worker.php" <<'PHP'
<?php

$root = getenv('DOOTASK_RELEASE_TEST_ROOT');
$tempDir = getenv('DOOTASK_RELEASE_TEST_DIR');
require $root . '/vendor/autoload.php';
$app = require $root . '/bootstrap/app.php';
$app->make(Illuminate\Contracts\Console\Kernel::class)->bootstrap();

$lockName = 'release-check-redis-lock-' . basename($tempDir);
$mode = $argv[1] ?? '';

if ($mode === 'holder') {
    $lock = Illuminate\Support\Facades\Cache::lock($lockName, 15);
    if (!$lock->get()) { fwrite(STDERR, "holder could not acquire lock\n"); exit(10); }
    file_put_contents($tempDir . '/holder-ready', '1');
    usleep(1200000);
    if (!$lock->release()) { fwrite(STDERR, "holder could not release lock\n"); exit(11); }
    file_put_contents($tempDir . '/holder-done', '1');
    echo "HOLDER_ACQUIRE=PASS\nHOLDER_RELEASE=PASS\n";
    exit(0);
}

if ($mode === 'contender') {
    $deadline = microtime(true) + 5;
    while (!is_file($tempDir . '/holder-ready') && microtime(true) < $deadline) { usleep(20000); }
    if (!is_file($tempDir . '/holder-ready')) { fwrite(STDERR, "holder did not acquire in time\n"); exit(12); }

    $blocked = !Illuminate\Support\Facades\Cache::lock($lockName, 15)->get();
    if (!$blocked) { fwrite(STDERR, "lock was not mutually exclusive\n"); exit(13); }

    $deadline = microtime(true) + 5;
    while (!is_file($tempDir . '/holder-done') && microtime(true) < $deadline) { usleep(20000); }
    if (!is_file($tempDir . '/holder-done')) { fwrite(STDERR, "holder did not release in time\n"); exit(14); }

    $lock = Illuminate\Support\Facades\Cache::lock($lockName, 15);
    $acquired = $lock->get();
    if ($acquired) { $lock->release(); }
    if (!$acquired) { fwrite(STDERR, "lock was not available after release\n"); exit(15); }

    echo "CONTENDER_BLOCKED=PASS\nCONTENDER_ACQUIRE_AFTER_RELEASE=PASS\n";
    exit(0);
}

if ($mode === 'ttl-holder') {
    $lock = Illuminate\Support\Facades\Cache::lock($lockName . '-ttl', 2);
    if (!$lock->get()) { fwrite(STDERR, "ttl holder could not acquire lock\n"); exit(17); }
    file_put_contents($tempDir . '/ttl-holder-ready', '1');
    echo "TTL_HOLDER_ACQUIRE=PASS\n";
    exit(0); // Deliberately leave the lock to expire after this process exits.
}

if ($mode === 'ttl-contender') {
    $deadline = microtime(true) + 5;
    while (!is_file($tempDir . '/ttl-holder-ready') && microtime(true) < $deadline) { usleep(20000); }
    if (!is_file($tempDir . '/ttl-holder-ready')) { fwrite(STDERR, "ttl holder did not acquire in time\n"); exit(18); }

    $lock = Illuminate\Support\Facades\Cache::lock($lockName . '-ttl', 2);
    if ($lock->get()) { $lock->release(); fwrite(STDERR, "ttl lock expired too early\n"); exit(19); }
    usleep(900000);
    if ($lock->get()) { $lock->release(); fwrite(STDERR, "ttl lock expired before its configured lifetime\n"); exit(20); }
    usleep(1300000);
    $acquired = $lock->get();
    if ($acquired) { $lock->release(); }
    if (!$acquired) { fwrite(STDERR, "ttl lock remained unavailable after expiry\n"); exit(21); }
    echo "TTL_BLOCKED_BEFORE_EXPIRY=PASS\nTTL_ACQUIRE_AFTER_EXPIRY=PASS\n";
    exit(0);
}

if ($mode === 'redis-outage') {
    try {
        Illuminate\Support\Facades\Cache::lock($lockName . '-outage', 15)->get();
    } catch (Throwable $exception) {
        echo 'REDIS_OUTAGE_VISIBLE=' . get_class($exception) . "\n";
        exit(0);
    }
    fwrite(STDERR, "lock call did not expose the Redis outage\n");
    exit(22);
}

if ($mode === 'redis-restored') {
    $lock = Illuminate\Support\Facades\Cache::lock($lockName . '-outage', 15);
    if (!$lock->get()) { fwrite(STDERR, "lock could not be acquired after Redis recovery\n"); exit(23); }
    $lock->release();
    echo "REDIS_RECOVERY_LOCK_ACQUIRE=PASS\n";
    exit(0);
}

fwrite(STDERR, "unknown worker mode\n");
exit(16);
PHP

export DOOTASK_RELEASE_TEST_ROOT="$project_root"
export DOOTASK_RELEASE_TEST_DIR="$temp_dir"
export APP_ENV=local
export CACHE_DRIVER=redis
export REDIS_CLIENT=phpredis
export REDIS_HOST=127.0.0.1
export REDIS_PORT="$redis_port"
export REDIS_PASSWORD=
export REDIS_DB=9
export REDIS_CACHE_DB=8
export REDIS_PREFIX="dootask_release_check_$(basename "$temp_dir")_"

php "$temp_dir/worker.php" holder > "$temp_dir/holder.log" 2>&1 &
holder_pid=$!
php "$temp_dir/worker.php" contender > "$temp_dir/contender.log" 2>&1 &
contender_pid=$!
wait "$holder_pid"
holder_pid=""
wait "$contender_pid"
contender_pid=""

cat "$temp_dir/holder.log"
cat "$temp_dir/contender.log"
php "$temp_dir/worker.php" ttl-holder > "$temp_dir/ttl-holder.log" 2>&1
cat "$temp_dir/ttl-holder.log"
php "$temp_dir/worker.php" ttl-contender > "$temp_dir/ttl-contender.log" 2>&1
cat "$temp_dir/ttl-contender.log"
echo "TWO_PROCESS_SHARED_REDIS_LOCK=PASS"

redis-cli -h 127.0.0.1 -p "$redis_port" shutdown nosave >/dev/null
for _ in $(seq 1 50); do
    if ! redis-cli -h 127.0.0.1 -p "$redis_port" ping >/dev/null 2>&1; then break; fi
    sleep 0.1
done
if redis-cli -h 127.0.0.1 -p "$redis_port" ping >/dev/null 2>&1; then
    echo "Temporary Redis did not stop for outage check." >&2
    exit 4
fi
php "$temp_dir/worker.php" redis-outage

redis-server \
    --bind 127.0.0.1 \
    --protected-mode yes \
    --port "$redis_port" \
    --save '' \
    --appendonly no \
    --dir "$temp_dir" \
    --daemonize yes \
    --pidfile "$temp_dir/redis.pid" \
    --logfile "$temp_dir/redis.log"
ready=0
for _ in $(seq 1 50); do
    if redis-cli -h 127.0.0.1 -p "$redis_port" ping >/dev/null 2>&1; then ready=1; break; fi
    sleep 0.1
done
if [[ "$ready" != 1 ]]; then
    echo "Temporary Redis did not recover." >&2
    exit 5
fi
php "$temp_dir/worker.php" redis-restored
echo "REDIS_OUTAGE_RECOVERY=PASS"
