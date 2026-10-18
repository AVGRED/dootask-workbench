#!/usr/bin/env bash

# Run a PHPUnit suite against disposable local MariaDB and Redis instances.
# Both services are created inside a temporary directory and removed on exit.
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
suite="${1:-Unit}"
shift || true
filter=""
if [[ "${1:-}" == "--filter" ]]; then
    filter="${2:-}"
fi
db_port="${DOOTASK_UNIT_TEST_DB_PORT:-13311}"
redis_port="${DOOTASK_UNIT_TEST_REDIS_PORT:-16380}"
temp_dir=""
db_started=0
redis_started=0
socket=""

cleanup() {
    if [[ "$db_started" == 1 ]]; then
        mariadb-admin --no-defaults --socket="$socket" -uroot shutdown >/dev/null 2>&1 || true
    fi
    if [[ "$redis_started" == 1 ]]; then
        redis-cli -h 127.0.0.1 -p "$redis_port" shutdown nosave >/dev/null 2>&1 || true
    fi
    if [[ -n "$temp_dir" && "$temp_dir" == /tmp/dootask-unit-tests.* ]]; then
        rm -rf -- "$temp_dir"
    fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

if [[ "$suite" != Unit && "$suite" != Feature ]]; then
    echo "Usage: $0 [Unit|Feature] [--filter <pattern>]" >&2
    exit 2
fi
if [[ "${1:-}" == "--filter" && -z "$filter" ]]; then
    echo "Usage: $0 [Unit|Feature] [--filter <pattern>]" >&2
    exit 2
fi

for command in php mariadb-install-db mariadbd mariadb mariadb-admin redis-server redis-cli; do
    command -v "$command" >/dev/null 2>&1 || { echo "Required command not found: $command" >&2; exit 2; }
done

if [[ -f "$project_root/bootstrap/cache/config.php" ]]; then
    echo "Refusing to run with cached Laravel config; clear the config cache or use a clean worktree." >&2
    exit 2
fi
if mariadb-admin --no-defaults -h 127.0.0.1 -P "$db_port" ping >/dev/null 2>&1; then
    echo "Refusing to use database port $db_port because it is already active." >&2
    exit 2
fi
if redis-cli -h 127.0.0.1 -p "$redis_port" ping >/dev/null 2>&1; then
    echo "Refusing to use Redis port $redis_port because it is already active." >&2
    exit 2
fi

temp_dir="$(mktemp -d /tmp/dootask-unit-tests.XXXXXX)"
socket="$temp_dir/mariadb.sock"
mariadb-install-db --no-defaults --user=root --datadir="$temp_dir/data" >"$temp_dir/install.log" 2>&1
nohup mariadbd --no-defaults --user=root \
    --datadir="$temp_dir/data" \
    --socket="$socket" \
    --port="$db_port" \
    --bind-address=127.0.0.1 \
    --pid-file="$temp_dir/mariadb.pid" \
    --log-error="$temp_dir/mariadb.log" \
    --skip-name-resolve >/dev/null 2>&1 &
db_started=1

ready=0
for _ in $(seq 1 100); do
    if mariadb-admin --no-defaults --socket="$socket" -uroot ping >/dev/null 2>&1; then ready=1; break; fi
    sleep 0.1
done
if [[ "$ready" != 1 ]]; then
    cat "$temp_dir/mariadb.log" >&2 || true
    echo "Temporary MariaDB did not become ready." >&2
    exit 3
fi

mariadb --no-defaults --socket="$socket" -uroot <<SQL
CREATE DATABASE dootask_unit_tests CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'dootask_unit_tests'@'127.0.0.1' IDENTIFIED BY 'disposable-local-unit-test';
GRANT ALL PRIVILEGES ON dootask_unit_tests.* TO 'dootask_unit_tests'@'127.0.0.1';
FLUSH PRIVILEGES;
SQL

export DB_CONNECTION=mysql
export DB_HOST=127.0.0.1
export DB_PORT="$db_port"
export DB_DATABASE=dootask_unit_tests
export DB_USERNAME=dootask_unit_tests
export DB_PASSWORD=disposable-local-unit-test
export APP_ENV=testing

mkdir -p "$temp_dir/redis"
redis-server \
    --bind 127.0.0.1 \
    --protected-mode yes \
    --port "$redis_port" \
    --save '' \
    --appendonly no \
    --dir "$temp_dir/redis" \
    --daemonize yes \
    --pidfile "$temp_dir/redis.pid" \
    --logfile "$temp_dir/redis.log"
redis_started=1

redis_ready=0
for _ in $(seq 1 50); do
    if redis-cli -h 127.0.0.1 -p "$redis_port" ping >/dev/null 2>&1; then redis_ready=1; break; fi
    sleep 0.1
done
if [[ "$redis_ready" != 1 ]]; then
    cat "$temp_dir/redis.log" >&2 || true
    echo "Temporary Redis did not become ready." >&2
    exit 3
fi

export REDIS_HOST=127.0.0.1
export REDIS_PORT="$redis_port"
export REDIS_DB=0
export REDIS_CACHE_DB=1
echo "ISOLATED_REDIS=PASS"

# Feature tests that boot the HTTP middleware or save models go through Doo::load(),
# which normally loads the licensed FFI library. Fall back to a local stub so the
# suite stays runnable on machines without a license; never use this outside tests.
if [[ -z "${DOOTASK_LIBRARY_PATH:-}" && ! -f /usr/lib/doo/doo.so ]]; then
    if command -v gcc >/dev/null 2>&1; then
        DOOTASK_LIBRARY_PATH="$(bash "$project_root/scripts/build-test-doo-so.sh" "$temp_dir/doo-stub")"
        export DOOTASK_LIBRARY_PATH
        echo "ISOLATED_DOO_STUB=PASS"
    else
        echo "No licensed doo.so and no gcc; Doo-dependent feature tests will fail." >&2
    fi
fi

cd "$project_root"
php artisan migrate --force --no-ansi --quiet
echo "ISOLATED_MIGRATIONS=PASS"

if [[ "$suite" == "Feature" ]]; then
    # A few suites rely on the demo users and system settings created by the
    # default seeders (the CI job runs migrate --seed as well).
    php artisan db:seed --class=UsersTableSeeder --force --no-ansi --quiet
    php artisan db:seed --class=SettingsTableSeeder --force --no-ansi --quiet
    echo "ISOLATED_SEED=PASS"
fi
phpunit_args=(--testsuite "$suite")
if [[ -n "$filter" ]]; then
    phpunit_args+=(--filter "$filter")
fi
vendor/bin/phpunit "${phpunit_args[@]}"
