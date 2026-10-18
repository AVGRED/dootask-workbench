<?php

use App\Exceptions\ApiException;
use App\Exceptions\ImagePathHandler;
use App\Module\Base;
use Illuminate\Database\Eloquent\ModelNotFoundException;
use Illuminate\Foundation\Application;
use Illuminate\Foundation\Configuration\Exceptions;
use Illuminate\Foundation\Configuration\Middleware;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Log;
use Symfony\Component\HttpKernel\Exception\NotFoundHttpException;

return Application::configure(basePath: $_ENV['APP_BASE_PATH'] ?? dirname(__DIR__))
    ->withRouting(
        web: __DIR__.'/../routes/web.php',
        api: __DIR__.'/../routes/api.php',
        apiPrefix: 'api',
        commands: __DIR__.'/../routes/console.php',
    )
    ->withMiddleware(function (Middleware $middleware): void {
        // Reject arbitrary Host values before URL generation. APP_URL and its subdomains
        // are trusted by default; deployments with additional exact aliases can list them
        // in APP_TRUSTED_HOSTS.
        $middleware->trustHosts(at: static function (): array {
            return array_map(
                static fn (string $host) => '^' . preg_quote(strtolower($host), '/') . '$',
                config('app.trusted_hosts', [])
            );
        });

        // PHP（Swoole）只在内网被 nginx 访问，外部无法直连，故信任内网代理。
        // 只采信 X-Forwarded-Proto：nginx 已用本机 scheme 或显式 APP_SCHEME 覆盖该头，
        // 据此让 url() 实时跟随 https；host/for 一律不信，避免 Host 注入与 IP 伪造。
        // Base::getIp() 仍读取 Client-IP/X-Forwarded-For，边缘 nginx 必须清空前者并覆盖后者。
        $middleware->trustProxies(at: '*', headers: Request::HEADER_X_FORWARDED_PROTO);

        $middleware->trimStrings(except: [
            'current_password',
            'password',
            'password_confirmation',
        ]);

        $middleware->validateCsrfTokens(except: [
            // 接口部分
            'api/*',
            'dav',
            'dav/*',

            // 发布桌面端
            'desktop/publish/',
        ]);

        // api 组限流（限流规则定义在 AppServiceProvider::boot）
        $middleware->throttleApi();

        $middleware->alias([
            'webapi' => \App\Http\Middleware\WebApi::class,
        ]);

        $middleware->redirectGuestsTo('/login');
        $middleware->redirectUsersTo('/home');
    })
    ->withExceptions(function (Exceptions $exceptions): void {
        // /uploads/**.png/crop/... 动态裁剪与缩略图（命中则返回图片，否则走默认 404）
        $exceptions->render(function (NotFoundHttpException $e, Request $request) {
            return ImagePathHandler::render($request);
        });

        $exceptions->render(function (ApiException $e) {
            return response()->json(Base::retError($e->getMessage(), $e->getData(), $e->getCode()));
        });

        $exceptions->render(function (ModelNotFoundException $e) {
            return response()->json(Base::retError('Interface error'));
        });

        // ApiException 按 isWriteLog 决定是否记录，且不走默认 report
        $exceptions->report(function (ApiException $e) {
            if ($e->isWriteLog()) {
                Log::error($e->getMessage(), [
                    'code' => $e->getCode(),
                    'data' => $e->getData(),
                    'exception' => ' at ' . $e->getFile() . ':' . $e->getLine()
                ]);
            }
        })->stop();
    })->create();
