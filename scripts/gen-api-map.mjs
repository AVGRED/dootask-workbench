#!/usr/bin/env node
/**
 * Generate routes/api-map.md from the dynamic API routes and controller @api docblocks.
 * The output mirrors `php artisan doc:api-map` without booting Laravel or requiring PHP.
 * Usage: node scripts/gen-api-map.mjs [--check]
 */

import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const ROOT_DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const ROUTES_FILE = path.join(ROOT_DIR, 'routes/web.php');
const OUTPUT_FILE = path.join(ROOT_DIR, 'routes/api-map.md');
const CONTROLLERS_DIR = path.join(ROOT_DIR, 'app/Http/Controllers/Api');
const CHECK_ONLY = process.argv.includes('--check');

function collectApiControllers(routesSource) {
    const controllers = new Map();
    let inApiGroup = false;

    for (const line of routesSource.split(/\r?\n/)) {
        if (!inApiGroup && /Route::prefix\(['"]api['"]\)/.test(line)) {
            inApiGroup = true;
            continue;
        }
        if (!inApiGroup) continue;
        if (/^\s*\}\);\s*$/.test(line)) break;

        const route = line.match(/Route::\w+\(\s*['"]([^'"]+)['"]\s*,\s*(\w+Controller)::class\)(?:->defaults\(['"]method['"]\s*,\s*['"]([^'"]+)['"]\))?/);
        if (!route) continue;

        const [, uri, controller, fixedMethod = null] = route;
        const methodRoute = uri.match(/^(.+)\/\{method\}$/);
        const actionRoute = uri.match(/^(.+)\/\{action\}$/);
        const prefix = methodRoute?.[1] || (fixedMethod ? actionRoute?.[1] : null);
        if (!prefix) continue;
        controllers.set(prefix, {controller, fixedMethod});
    }

    return controllers;
}

function docBlockBefore(source, position) {
    const end = source.lastIndexOf('*/', position);
    const start = end >= 0 ? source.lastIndexOf('/**', end) : -1;
    if (start < 0 || source.slice(end + 2, position).trim()) return '';
    return source.slice(start, end + 2);
}

function collectMethods(prefix, controller, fixedMethod) {
    const sourcePath = path.join(CONTROLLERS_DIR, `${controller}.php`);
    if (!fs.existsSync(sourcePath)) {
        throw new Error(`API controller source not found: ${path.relative(ROOT_DIR, sourcePath)}`);
    }
    const source = fs.readFileSync(sourcePath, 'utf8').replace(/^\uFEFF/, '');
    const methods = [];
    const methodPattern = /public\s+function\s+([A-Za-z0-9_]+)\s*\(/g;
    let match;

    while ((match = methodPattern.exec(source)) !== null) {
        const methodName = match[1];
        // ReflectionMethod::IS_PUBLIC in the Artisan generator excludes static methods.
        const declarationStart = source.lastIndexOf('\n', match.index) + 1;
        if (/\bstatic\s+$/.test(source.slice(declarationStart, match.index))) continue;
        if (methodName.startsWith('__')) continue;

        let urlMethod = methodName.replaceAll('__', '/');
        if (fixedMethod) {
            const fixedPrefix = `${fixedMethod}__`;
            if (!methodName.startsWith(fixedPrefix)) continue;
            urlMethod = methodName.slice(fixedPrefix.length);
        }

        const apiDoc = docBlockBefore(source, match.index).match(/@api\s+\{(\w+)}\s+(\S+)(?:[ \t]+([^\r\n*]+))?/);
        methods.push({
            url: `api/${prefix}/${urlMethod}`,
            method: `${methodName}()`,
            http: apiDoc ? apiDoc[1].toLowerCase() : 'any',
            title: apiDoc?.[3]?.trim() || '',
        });
    }

    return methods;
}

function renderSection(prefix, controller, methods) {
    const lines = [
        `## ${prefix}（${controller}）`,
        '',
        '| URL | 方法名 | HTTP | 说明 |',
        '| --- | --- | --- | --- |',
        ...methods.map(({url, method, http, title}) => `| ${url} | ${method} | ${http} | ${title} |`),
        '',
    ];
    return lines.join('\n');
}

function renderHeader(total) {
    return [
        '# API 路由对照表',
        '',
        '> 此文件可由 `php artisan doc:api-map` 或 `npm run api:map` 生成，勿手改。',
        '',
        `接口总数：${total}`,
        '',
        '## 路由规则',
        '',
        'API 使用动态路由（见 `routes/web.php`），URL 段映射为控制器方法名：',
        '',
        '- `api/{controller}/{method}` → `{method}()`，如 `api/project/lists` → `ProjectController::lists()`',
        '- `api/{controller}/{method}/{action}` → `{method}__{action}()`（双下划线连接），如 `api/project/invite/join` → `ProjectController::invite__join()`',
        '- 路由最多两段，方法名最多一个双下划线',
        '',
        '',
    ].join('\n');
}

const routesSource = fs.readFileSync(ROUTES_FILE, 'utf8').replace(/^\uFEFF/, '');
const controllers = collectApiControllers(routesSource);
if (controllers.size === 0) throw new Error('No dynamic API controllers were found in routes/web.php');

let total = 0;
const sections = [];
for (const [prefix, {controller, fixedMethod}] of controllers) {
    const methods = collectMethods(prefix, controller, fixedMethod);
    total += methods.length;
    sections.push(renderSection(prefix, controller, methods));
}

const current = fs.existsSync(OUTPUT_FILE) ? fs.readFileSync(OUTPUT_FILE, 'utf8').replace(/^\uFEFF/, '').replace(/\r\n/g, '\n') : '';
const output = `${renderHeader(total)}${sections.join('\n')}`;
if (CHECK_ONLY) {
    if (current.replace(/\r\n/g, '\n') !== output) {
        const normalized = current.replace(/\r\n/g, '\n');
        let mismatch = 0;
        while (mismatch < normalized.length && mismatch < output.length && normalized[mismatch] === output[mismatch]) mismatch++;
        const line = normalized.slice(0, mismatch).split('\n').length;
        console.error(`API route map is stale at line ${line}; run npm run api:map (${controllers.size} controllers, ${total} methods).`);
        process.exitCode = 1;
    } else {
        console.log(`API route map is current (${controllers.size} controllers, ${total} methods).`);
    }
} else {
    fs.writeFileSync(OUTPUT_FILE, output, 'utf8');
    console.log(`Generated routes/api-map.md (${controllers.size} controllers, ${total} methods).`);
}
