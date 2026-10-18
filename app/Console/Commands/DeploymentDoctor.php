<?php

namespace App\Console\Commands;

use FFI;
use Illuminate\Console\Command;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use Throwable;

class DeploymentDoctor extends Command
{
    protected $signature = 'dootask:doctor {--json : 输出机器可读的 JSON 结果}';

    protected $description = '检查 DooTask 运行环境与部署前置条件';

    private int $failures = 0;

    /** @var array<int, array{name: string, passed: bool, details: ?string}> */
    private array $checks = [];

    public function handle(): int
    {
        $this->check('PHP 版本 >= 8.3', PHP_VERSION_ID >= 80300, PHP_VERSION);

        $composer = json_decode((string)file_get_contents(base_path('composer.json')), true);
        $requiredExtensions = array_keys(array_filter(
            $composer['require'] ?? [],
            fn ($version, $package) => str_starts_with((string)$package, 'ext-'),
            ARRAY_FILTER_USE_BOTH
        ));
        foreach ($requiredExtensions as $package) {
            $extension = substr((string)$package, 4);
            $this->check("PHP 扩展 {$extension}", extension_loaded($extension));
        }

        $this->check('Swoole 扩展', extension_loaded('swoole'));
        $this->check('LaravelS 运行依赖', class_exists('Hhxsv5\\LaravelS\\Illuminate\\LaravelSServiceProvider'));
        $libraryPath = (string)config('dootask.library_path', '/usr/lib/doo/doo.so');
        $this->check('PHP FFI 扩展', extension_loaded('FFI'));
        $libraryReadable = $libraryPath !== '' && is_file($libraryPath) && is_readable($libraryPath);
        $this->check('DooTask 授权库可读', $libraryReadable, $libraryPath);
        if (extension_loaded('FFI') && $libraryReadable) {
            try {
                FFI::cdef('void initialize(char* work, char* token, char* lang);', $libraryPath);
                $this->check('DooTask 授权库可加载', true);
            } catch (Throwable $exception) {
                $this->check('DooTask 授权库可加载', false, $exception->getMessage());
            }
        } else {
            $this->check('DooTask 授权库可加载', false, '请先修复 FFI 扩展或授权库路径');
        }

        $appKey = (string)config('app.key');
        $this->check('APP_KEY 已配置', $appKey !== '' && !Str::contains($appKey, 'SomeRandomString'));
        $this->check('APP_ENV 为 production', config('app.env') === 'production', (string)config('app.env'));
        $this->check('APP_DEBUG 已关闭', config('app.debug') === false, config('app.debug') ? '当前为开启' : '已关闭');

        $listenIp = (string)config('laravels.listen_ip', '127.0.0.1');
        $this->check('LaravelS 仅监听本机', in_array($listenIp, ['127.0.0.1', '::1', 'localhost'], true), $listenIp);

        foreach ([storage_path(), storage_path('logs'), storage_path('framework/cache'), storage_path('framework/sessions'), storage_path('framework/views'), base_path('bootstrap/cache'), public_path('uploads')] as $path) {
            $this->check('目录可写 ' . $this->relativePath($path), is_dir($path) && is_writable($path));
        }

        try {
            DB::connection()->selectOne('select 1 as dootask_doctor');
            $this->check('数据库连接', true, (string)config('database.default'));
        } catch (Throwable $exception) {
            $this->check('数据库连接', false, $exception->getMessage());
        }

        try {
            $migrator = app('migrator');
            $migrationFiles = $migrator->getMigrationFiles(database_path('migrations'));
            $ranMigrations = $migrator->getRepository()->getRan();
            $pendingMigrations = array_values(array_diff(array_keys($migrationFiles), $ranMigrations));
            $details = $pendingMigrations
                ? count($pendingMigrations) . ' 个未应用：' . implode(', ', array_slice($pendingMigrations, 0, 5))
                : '已全部应用';
            $this->check('数据库迁移状态', !$pendingMigrations, $details);
        } catch (Throwable $exception) {
            $this->check('数据库迁移状态', false, $exception->getMessage());
        }

        try {
            $connection = app('redis')->connection();
            $connection->ping();
            $this->check('Redis 连接', true, (string)config('database.redis.client'));
        } catch (Throwable $exception) {
            $this->check('Redis 连接', false, $exception->getMessage());
        }

        $this->checkCacheLock();

        $exitCode = $this->failures > 0 ? self::FAILURE : self::SUCCESS;
        if ($this->option('json')) {
            $this->output->writeln((string)json_encode([
                'status' => $exitCode === self::SUCCESS ? 'passed' : 'failed',
                'passed' => count($this->checks) - $this->failures,
                'failed' => $this->failures,
                'checks' => $this->checks,
            ], JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT | JSON_INVALID_UTF8_SUBSTITUTE));
            return $exitCode;
        }

        if ($exitCode === self::FAILURE) {
            $this->error("预检未通过：{$this->failures} 项失败。修复后重新执行 php artisan dootask:doctor。");
            return $exitCode;
        }

        $this->info('预检通过。该结果不替代 WebSocket 授权消息、数据库备份恢复和多客户端验收。');
        return $exitCode;
    }

    private function check(string $name, bool $passed, ?string $details = null): void
    {
        $this->checks[] = ['name' => $name, 'passed' => $passed, 'details' => $details];
        if ($passed) {
            if (!$this->option('json')) {
                $this->line("<info>[通过]</info> {$name}" . ($details ? "：{$details}" : ''));
            }
            return;
        }

        $this->failures++;
        if (!$this->option('json')) {
            $this->line("<error>[失败]</error> {$name}" . ($details ? "：{$details}" : ''));
        }
    }

    private function checkCacheLock(): void
    {
        $key = 'dootask:doctor:' . Str::uuid();
        $firstLock = Cache::lock($key, 10);
        $secondLock = Cache::lock($key, 10);
        $firstAcquired = false;
        $secondAcquiredWhileLocked = false;
        $secondAcquired = false;

        try {
            $firstAcquired = $firstLock->get();
            if ($firstAcquired) {
                $secondAcquiredWhileLocked = $secondLock->get();
            }
            $secondBlocked = $firstAcquired && !$secondAcquiredWhileLocked;
            $firstReleased = $firstAcquired && $firstLock->release();
            $secondAcquired = $secondAcquiredWhileLocked
                || ($firstReleased && $secondLock->get());
            $this->check('缓存锁互斥与释放', $firstAcquired && $secondBlocked && $firstReleased && $secondAcquired,
                '首次获取=' . ($firstAcquired ? '成功' : '失败')
                . '，竞争阻止=' . ($secondBlocked ? '是' : '否')
                . '，释放后重获=' . ($secondAcquired ? '成功' : '失败'));
        } catch (Throwable $exception) {
            $this->check('缓存锁互斥与释放', false, $exception->getMessage());
        } finally {
            if ($firstAcquired) {
                try {
                    $firstLock->release();
                } catch (Throwable $exception) {
                    $this->check('缓存锁清理', false, $exception->getMessage());
                }
            }
            if ($secondAcquired) {
                try {
                    $secondLock->release();
                } catch (Throwable $exception) {
                    $this->check('缓存锁清理', false, $exception->getMessage());
                }
            }
        }
    }

    private function relativePath(string $path): string
    {
        return Str::startsWith($path, base_path()) ? ltrim(substr($path, strlen(base_path())), DIRECTORY_SEPARATOR) : $path;
    }
}
