#!/usr/bin/env node
/**
 * Static wiring check for Vue single-file components.
 *
 * Large components are routinely split into child components. The most common
 * breakage in that refactor is a template that still references state or methods
 * which moved away: ESLint's Vue plugin parses the template but does not resolve
 * identifiers, and no browser is available in the test pipeline. This script walks
 * the template expressions, tracks v-for / slot-scope locals, and reports every
 * identifier that is neither declared by the component (props/data/computed/
 * methods) nor a known global.
 *
 * Usage: node scripts/check-vue-wiring.mjs [files...]
 *        (no arguments checks resources/assets/js/**\/*.vue)
 */

import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createRequire} from 'node:module';

const require = createRequire(import.meta.url);
const {parse} = require('vue-eslint-parser');
const ROOT_DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

const GLOBAL_MIXIN_NAMES = new Set();

// Vue.mixin() in app.js exposes shared data to every component; treat those names
// as globals so a template using windowWidth is not reported as dangling.
for (const mixinFile of ['resources/assets/js/mixins/state.js']) {
    const mixinPath = path.join(ROOT_DIR, mixinFile);
    if (!fs.existsSync(mixinPath)) continue;
    const mixinSource = fs.readFileSync(mixinPath, 'utf8');
    for (const name of [
        ...objectKeys(mixinSource, 'computed'),
        ...objectKeys(mixinSource, 'methods'),
        ...dataKeys(mixinSource),
        ...mappedKeys(mixinSource),
    ]) {
        GLOBAL_MIXIN_NAMES.add(name);
    }
}

const GLOBALS = new Set([
    '$L', '$A', '$store', '$router', '$route', '$t', '$event',
    '$emit', '$slots', '$attrs', '$listeners', '$refs', '$el', '$parent', '$root',
    '$children', '$options', '$data', '$props', '$nextTick', '$set', '$delete',
    '$watch', '$forceUpdate', '$destroy', '$mount', '$createElement',
    'true', 'false', 'null', 'undefined', 'this', 'new', 'typeof', 'in', 'instanceof',
    'void', 'delete', 'return', 'yield', 'await', 'async', 'function', 'class',
    'Math', 'Date', 'JSON', 'Number', 'String', 'Boolean', 'Array', 'Object', 'RegExp',
    'parseInt', 'parseFloat', 'isNaN', 'isFinite', 'NaN', 'Infinity',
    'console', 'window', 'document', 'navigator', 'Intl', 'Set', 'Map', 'Symbol',
]);

/** Extract the top-level object keys of `name: { ... }` inside the script body. */
function objectKeys(script, propertyName) {
    const keys = new Set();
    const start = script.search(new RegExp(`\\b${propertyName}\\s*:\\s*\\{`));
    if (start < 0) return keys;
    let index = script.indexOf('{', start);
    let depth = 0;
    let lineStart = index + 1;
    let line = '';
    for (let i = index; i < script.length; i++) {
        const ch = script[i];
        if (ch === '\n') {
            lineStart = i + 1;
            line = '';
            continue;
        }
        line += ch;
        if (ch === '{') depth++;
        if (ch === '}') {
            depth--;
            if (depth === 0) break;
        }
        if (depth !== 1) continue;
        const match = line.match(/(?:^|[{,])\s*(?:async\s+)?([A-Za-z_$][\w$]*)\s*(?:\(|:)/);
        if (match && lineStart >= index) keys.add(match[1]);
        // Shorthand entries such as `transformEmojiToHtml,` expose the imported
        // function without a colon, which is allowed in Vue 2 options objects.
        const shorthand = line.match(/^\s*([A-Za-z_$][\w$]*)\s*,\s*$/);
        if (shorthand && lineStart >= index) keys.add(shorthand[1]);
    }
    return keys;
}

/** Collect keys of the object returned by data(). */
function dataKeys(script) {
    const keys = new Set();
    const start = script.search(/\bdata\s*\(\s*\)\s*\{/);
    if (start < 0) return keys;
    const returnIndex = script.indexOf('return', start);
    if (returnIndex < 0) return keys;
    const open = script.indexOf('{', returnIndex);
    let depth = 0;
    let line = '';
    for (let i = open; i < script.length; i++) {
        const ch = script[i];
        if (ch === '\n') {
            line = '';
            continue;
        }
        line += ch;
        if (ch === '{') depth++;
        if (ch === '}') {
            depth--;
            if (depth === 0) break;
        }
        if (depth !== 1) continue;
        const match = line.match(/^\s*([A-Za-z_$][\w$]*)\s*:/);
        if (match) keys.add(match[1]);
    }
    return keys;
}

/** Property names provided by ...mapState([...]) / mapGetters([...]) style spreads. */
function mappedKeys(script) {
    const keys = new Set();
    for (const match of script.matchAll(/\.\.\.map(?:State|Getters|Mutations|Actions)\(\s*\[([^\]]*)\]/g)) {
        for (const name of match[1].matchAll(/['"]([A-Za-z_$][\w$]*)['"]/g)) keys.add(name[1]);
    }
    return keys;
}

/** Props declared in object or array form. */
function propKeys(script) {
    const keys = objectKeys(script, 'props');
    const arrayForm = script.match(/\bprops\s*:\s*\[([^\]]*)\]/);
    if (arrayForm) {
        for (const name of arrayForm[1].matchAll(/['"]([A-Za-z_$][\w$]*)['"]/g)) keys.add(name[1]);
    }
    return keys;
}

function walk(node, parent, visit) {
    if (!node || typeof node !== 'object') return;
    if (Array.isArray(node)) {
        for (const child of node) walk(child, parent, visit);
        return;
    }
    if (typeof node.type === 'string') visit(node, parent);
    for (const key of Object.keys(node)) {
        if (key === 'parent' || key === 'loc' || key === 'range') continue;
        const value = node[key];
        if (!value || typeof value !== 'object') continue;
        const childParent = typeof node.type === 'string' ? node : parent;
        if (Array.isArray(value)) {
            for (const child of value) {
                if (child && typeof child === 'object' && typeof child.type === 'string') {
                    walk(child, node, visit);
                }
            }
        } else if (typeof value.type === 'string') {
            walk(value, node, visit);
        }
    }
}

function scopeNamesFromPattern(pattern, into) {
    if (!pattern) return;
    if (pattern.type === 'Identifier') {
        into.add(pattern.name);
    } else if (pattern.type === 'ObjectPattern') {
        for (const prop of pattern.properties || []) {
            if (prop.type === 'Property') scopeNamesFromPattern(prop.value, into);
            else scopeNamesFromPattern(prop.argument, into);
        }
    } else if (pattern.type === 'ArrayPattern') {
        for (const element of pattern.elements || []) scopeNamesFromPattern(element, into);
    } else if (pattern.type === 'AssignmentPattern') {
        scopeNamesFromPattern(pattern.left, into);
    } else if (pattern.type === 'RestElement') {
        scopeNamesFromPattern(pattern.argument, into);
    }
}

function isPropertyName(node, parent) {
    if (!parent) return false;
    if (parent.type === 'MemberExpression' && parent.property === node && !parent.computed) return true;
    if (parent.type === 'Property' && parent.key === node && !parent.computed) return true;
    if (parent.type === 'MethodDefinition' && parent.key === node) return true;
    return false;
}

function isFunctionParam(node, parent) {
    if (!parent) return false;
    const containers = ['FunctionExpression', 'ArrowFunctionExpression', 'FunctionDeclaration'];
    if (!containers.includes(parent.type)) return false;
    return (parent.params || []).includes(node);
}

function checkFile(file, templateStartLine) {
    const source = fs.readFileSync(file, 'utf8');
    const scriptMatch = source.match(/<script[^>]*>([\s\S]*?)<\/script>/);
    const script = scriptMatch ? scriptMatch[1] : '';
    const declared = new Set([
        ...propKeys(script),
        ...dataKeys(script),
        ...objectKeys(script, 'computed'),
        ...objectKeys(script, 'methods'),
        ...mappedKeys(script),
    ]);

    let ast;
    try {
        ast = parse(source, {sourceType: 'module', ecmaVersion: 2022});
    } catch (error) {
        return [{line: 1, name: '(parse)', message: `无法解析：${error.message}`}];
    }
    if (!ast.templateBody) return [];

    const problems = [];
    const reported = new Set();
    const visit = (node, parent) => {
        if (node.type === 'VExpressionContainer' && node.expression) {
            const scope = node._scope || new Set();
            walk(node.expression, null, (child, childParent) => {
                if (child.type !== 'Identifier') return;
                if (isPropertyName(child, childParent) || isFunctionParam(child, childParent)) return;
                const name = child.name;
                if (name.startsWith('$')) return;
                if (GLOBALS.has(name) || GLOBAL_MIXIN_NAMES.has(name) || scope.has(name) || declared.has(name)) return;
                const line = node.loc ? node.loc.start.line + templateStartLine - 1 : 1;
                const key = `${line}:${name}`;
                if (reported.has(key)) return;
                reported.add(key);
                problems.push({line, name, message: '模板引用了未声明的标识符'});
            });
        }
    };

    // Walk the template manually so v-for / slot-scope locals are in scope for
    // their subtree; vue-eslint-parser exposes the template as VElement nodes.
    const seenElements = new WeakSet();
    const walkTemplate = (element, scope) => {
        if (!element || typeof element !== 'object') return;
        if (seenElements.has(element)) return;
        seenElements.add(element);
        if (element.type === 'VExpressionContainer') {
            element._scope = scope;
            visit(element, null);
            return;
        }
        if (element.type !== 'VElement' && element.type !== 'VDocumentFragment') {
            for (const key of Object.keys(element)) {
                if (key === 'parent' || key === 'loc' || key === 'range') continue;
                const value = element[key];
                if (Array.isArray(value)) value.forEach(child => walkTemplate(child, scope));
                else if (value && typeof value === 'object' && typeof value.type === 'string') walkTemplate(value, scope);
            }
            return;
        }
        const local = new Set(scope);
        for (const attribute of element.startTag ? element.startTag.attributes : []) {
            if (!attribute.directive) continue;
            const directiveName = attribute.key.name && attribute.key.name.name;
            if (directiveName === 'for' && attribute.value && attribute.value.expression) {
                const left = attribute.value.expression.left;
                // v-for="(item, index) in list" parses as an array of patterns.
                if (Array.isArray(left)) left.forEach(pattern => scopeNamesFromPattern(pattern, local));
                else scopeNamesFromPattern(left, local);
            }
            if (['slot', 'slot-scope'].includes(directiveName) && attribute.value && attribute.value.expression) {
                const expression = attribute.value.expression;
                if (Array.isArray(expression)) expression.forEach(pattern => scopeNamesFromPattern(pattern, local));
                else scopeNamesFromPattern(expression, local);
            }
        }
        for (const child of element.children || []) walkTemplate(child, local);
        for (const attribute of element.startTag ? element.startTag.attributes : []) {
            if (attribute.value) walkTemplate(attribute.value, local);
        }
    };
    walkTemplate(ast.templateBody, new Set());
    return problems;
}

// Components covered by default. They are the files touched by the planning
// workbench feature and the ones most likely to be split further; legacy
// components still report findings that need a separate cleanup pass, so they are
// only scanned with --all until that pass happens.
const DEFAULT_TARGETS = [
    'resources/assets/js/pages/manage/components/ProjectPanel.vue',
    'resources/assets/js/pages/manage/components/ProjectGantt.vue',
    'resources/assets/js/pages/manage/components/TaskDetail.vue',
    'resources/assets/js/pages/manage/components/TaskWork.vue',
    'resources/assets/js/pages/manage/components/TaskAdd.vue',
    'resources/assets/js/pages/manage/components/TaskRow.vue',
    'resources/assets/js/pages/manage/components/TaskMove.vue',
    'resources/assets/js/pages/manage/components/TaskTemplateBrowser.vue',
    'resources/assets/js/pages/manage/components/ProjectTaskTemplate/index.vue',
    'resources/assets/js/pages/manage/components/MilestoneManager.vue',
    'resources/assets/js/pages/manage/components/ProjectCapacityOverview.vue',
    'resources/assets/js/pages/manage/components/ProjectSettingDrawer.vue',
    'resources/assets/js/pages/manage/components/ProjectInviteDialog.vue',
    'resources/assets/js/pages/manage/components/ProjectTransferDialog.vue',
    'resources/assets/js/pages/manage/components/MilestoneReminderAttempts.vue',
    'resources/assets/js/components/GanttView.vue',
    'resources/assets/js/pages/single/task.vue',
];

const scanAll = process.argv.includes('--all');
const targets = process.argv.slice(2).filter(argument => argument !== '--all');
const files = targets.length
    ? targets.map(target => path.resolve(process.cwd(), target))
    : (scanAll
        ? fs.readdirSync(path.join(ROOT_DIR, 'resources/assets/js'), {recursive: true})
            .filter(name => name.endsWith('.vue'))
            .map(name => path.join(ROOT_DIR, 'resources/assets/js', name))
        : DEFAULT_TARGETS.map(relative => path.join(ROOT_DIR, relative)));

let total = 0;
for (const file of files) {
    if (!fs.existsSync(file)) {
        console.error(`✖ 文件不存在：${file}`);
        process.exitCode = 1;
        continue;
    }
    const source = fs.readFileSync(file, 'utf8');
    const templateLine = source.slice(0, source.indexOf('<template')).split('\n').length;
    const problems = checkFile(file, templateLine);
    for (const problem of problems) {
        const relative = path.relative(ROOT_DIR, file).split(path.sep).join('/');
        console.error(`✖ ${relative}:${problem.line} ${problem.message}：${problem.name}`);
        total++;
    }
}

if (total > 0) {
    console.error(`\nVue 模板引用了 ${total} 处未声明的标识符。`);
    process.exitCode = 1;
} else {
    console.log(`Vue 模板连线检查通过（${files.length} 个组件）。`);
}
