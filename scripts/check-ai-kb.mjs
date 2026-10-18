#!/usr/bin/env node
/**
 * Validate the AI knowledge base chunks under resources/ai-kb/.
 *
 * Mirrors the lint rules documented in resources/ai-kb/_schema/frontmatter.md
 * without requiring the Python plugin or a YAML dependency:
 *   - frontmatter must be strict YAML (flat scalar/list subset only)
 *   - required fields, enums, id uniqueness and id/feature/type consistency
 *   - feature vocabulary from _meta/feature-map.yaml
 *   - related_tools vocabulary from _meta/tool-binding.yaml
 *   - body length, cross-chunk [[id]] references and forbidden references
 *
 * Usage: node scripts/check-ai-kb.mjs [--verbose]
 * Exit code 1 on any blocking error.
 */

import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const ROOT_DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const KB_DIR = path.join(ROOT_DIR, 'resources/ai-kb');
const FEATURE_MAP = path.join(KB_DIR, '_meta/feature-map.yaml');
const TOOL_BINDING = path.join(KB_DIR, '_meta/tool-binding.yaml');
const VERBOSE = process.argv.includes('--verbose');

const TYPES = new Set(['concept', 'howto', 'faq', 'menu-map', 'glossary', 'shortcut']);
const SCOPES = new Set(['end-user', 'admin', 'super-admin']);
const LOCALES = new Set(['zh', 'en']);
const REQUIRED = ['id', 'title', 'type', 'feature', 'scope', 'locale', 'aliases', 'last_verified'];
const KNOWN_KEYS = new Set([
    'id', 'title', 'type', 'feature', 'scope', 'locale', 'aliases', 'related_tools',
    'related_pages', 'prerequisites', 'negative', 'last_verified',
]);
const FORBIDDEN_PHRASES = ['如上图', '如上所述', '如前文', '在前面的', '参见上节'];
const MIN_BODY = 200;
const MAX_BODY = 1500;

const errors = [];
const warnings = [];

const rel = (file) => path.relative(ROOT_DIR, file).split(path.sep).join('/');
const kbRel = (file) => path.relative(KB_DIR, file).split(path.sep).join('/');
const fail = (file, line, message) => errors.push({file: rel(file), line, message});
const warn = (file, line, message) => warnings.push({file: rel(file), line, message});

function walk(dir, out = []) {
    for (const entry of fs.readdirSync(dir, {withFileTypes: true})) {
        const full = path.join(dir, entry.name);
        if (entry.isDirectory()) {
            if (['_schema', '_meta', '_eval'].includes(entry.name)) continue;
            walk(full, out);
        } else if (entry.name.endsWith('.md') && entry.name !== 'README.md') {
            out.push(full);
        }
    }
    return out;
}

/**
 * Parse the strict flat subset of YAML used by chunk frontmatter:
 *   key: scalar        key: []        key: [a, b]
 *   key:
 *     - item
 * Anything else (nested maps, block scalars, anchors, indented maps) is rejected
 * on purpose: a silent fallback is what lets a mis-indented list item slip through.
 */
function parseFrontmatter(lines) {
    const data = {};
    const problems = [];
    for (let i = 0; i < lines.length; i++) {
        const line = lines[i];
        if (!line.trim()) continue;
        if (line.includes('\t')) {
            problems.push([i + 1, 'frontmatter must use spaces, not tabs']);
            continue;
        }
        if (/^\s/.test(line)) {
            problems.push([i + 1, 'unexpected indented line outside a list']);
            continue;
        }
        const match = line.match(/^([A-Za-z_][A-Za-z0-9_]*):(?:[ \t]+(.*))?$/);
        if (!match) {
            problems.push([i + 1, `unparsable frontmatter line: ${JSON.stringify(line)}`]);
            continue;
        }
        const [, key, rawValue = ''] = match;
        if (Object.prototype.hasOwnProperty.call(data, key)) {
            problems.push([i + 1, `duplicate key "${key}"`]);
            continue;
        }
        const value = rawValue.trim();
        if (value === '') {
            const items = [];
            let j = i + 1;
            for (; j < lines.length; j++) {
                const itemLine = lines[j];
                if (!itemLine.trim()) continue;
                const item = itemLine.match(/^ {2}-[ \t]+(.+)$/);
                if (!item) break;
                items.push(unquote(item[1].trim()));
            }
            if (items.length === 0) {
                problems.push([i + 1, `"${key}" has no value and no list items`]);
            }
            data[key] = items;
            i = j - 1;
            continue;
        }
        if (value === '[]') {
            data[key] = [];
            continue;
        }
        const inline = value.match(/^\[(.*)\]$/);
        if (inline) {
            data[key] = inline[1].trim() === ''
                ? []
                : inline[1].split(',').map((part) => unquote(part.trim()));
            continue;
        }
        if (/^[[{]/.test(value) && !/^\[.*\]$/.test(value)) {
            problems.push([i + 1, `"${key}" uses unsupported inline YAML`]);
            continue;
        }
        data[key] = unquote(value);
    }
    return {data, problems};
}

function unquote(value) {
    if (/^".*"$/.test(value) || /^'.*'$/.test(value)) return value.slice(1, -1);
    return value;
}

function readVocab(file, pattern, minimum) {
    const source = fs.readFileSync(file, 'utf8').replace(/^\uFEFF/, '');
    const values = new Set();
    for (const line of source.split(/\r?\n/)) {
        const match = line.match(pattern);
        if (match) values.add(match[1]);
    }
    if (values.size < minimum) {
        errors.push({
            file: rel(file),
            line: 1,
            message: `could not read the vocabulary (${values.size} entries, expected at least ${minimum})`,
        });
    }
    return values;
}

const features = readVocab(FEATURE_MAP, /^ {2}- id:[ \t]*([a-z0-9-]+)[ \t]*$/, 10);
const tools = readVocab(TOOL_BINDING, /^ {2}([A-Za-z0-9_-]+):[ \t]*$/, 10);

const files = walk(KB_DIR);
const seenIds = new Map();
const bodies = new Map();
let bodyTotal = 0;

for (const file of files) {
    const text = fs.readFileSync(file, 'utf8').replace(/^\uFEFF/, '');
    const lines = text.split(/\r?\n/);
    if (lines[0] !== '---') {
        fail(file, 1, 'file must start with a YAML frontmatter fence (---)');
        continue;
    }
    const closing = lines.indexOf('---', 1);
    if (closing < 0) {
        fail(file, 1, 'frontmatter is not closed with ---');
        continue;
    }
    if (closing + 1 < lines.length && lines[closing + 1].trim() !== '') {
        fail(file, closing + 1, 'a blank line is required between frontmatter and the body');
    }

    const {data, problems} = parseFrontmatter(lines.slice(1, closing));
    for (const [line, message] of problems) fail(file, line + 1, message);

    const body = lines.slice(closing + 1).join('\n').trim();
    bodyTotal += 1;

    for (const key of REQUIRED) {
        if (data[key] === undefined || data[key] === '' || (Array.isArray(data[key]) && data[key].length === 0)) {
            fail(file, closing + 1, `missing required field "${key}"`);
        }
    }
    if (data.id && !/^[a-z0-9][a-z0-9-]*(\.[a-z0-9-]+)+$/.test(data.id)) {
        fail(file, closing + 1, `id "${data.id}" is not in kebab.dot form`);
    }
    if (data.title && [...data.title].length > 60) {
        warn(file, closing + 1, `title is longer than 60 characters (${[...data.title].length})`);
    }
    if (data.type && !TYPES.has(data.type)) {
        fail(file, closing + 1, `unknown type "${data.type}"`);
    }
    if (data.scope && !SCOPES.has(data.scope)) {
        fail(file, closing + 1, `unknown scope "${data.scope}"`);
    }
    if (data.locale && !LOCALES.has(data.locale)) {
        fail(file, closing + 1, `unknown locale "${data.locale}"`);
    }
    if (data.feature && !features.has(data.feature)) {
        fail(file, closing + 1, `feature "${data.feature}" is not declared in _meta/feature-map.yaml`);
    }
    if (data.last_verified && !/^v\d+\.\d+\.\d+$/.test(data.last_verified)) {
        fail(file, closing + 1, `last_verified "${data.last_verified}" must look like v1.2.3`);
    }
    if (Array.isArray(data.related_tools)) {
        for (const tool of data.related_tools) {
            if (!tools.has(tool)) fail(file, closing + 1, `related_tools entry "${tool}" is not declared in _meta/tool-binding.yaml`);
        }
    }
    if (data.aliases !== undefined && !Array.isArray(data.aliases)) {
        fail(file, closing + 1, 'aliases must be a list');
    } else if (Array.isArray(data.aliases)) {
        for (const alias of data.aliases) {
            if ([...String(alias)].length < 2) fail(file, closing + 1, `alias "${alias}" is shorter than 2 characters`);
        }
    }

    const parts = kbRel(file).split('/');
    if (data.locale && parts[0] !== data.locale) {
        fail(file, 1, `locale "${data.locale}" does not match the "${parts[0]}/" directory`);
    }
    if (data.type && parts[1] !== data.type) {
        fail(file, 1, `type "${data.type}" does not match the "${parts[1]}/" directory`);
    }
    if (data.id && data.feature && !data.id.startsWith(`${data.feature}.`)) {
        fail(file, 1, `id "${data.id}" must start with the feature name "${data.feature}."`);
    }
    if (data.id && data.type && !data.id.split('.').includes(data.type)) {
        fail(file, 1, `id "${data.id}" must contain the type segment "${data.type}"`);
    }
    if (data.id) {
        if (seenIds.has(data.id)) {
            fail(file, 1, `duplicate id "${data.id}" (also used by ${seenIds.get(data.id)})`);
        } else {
            seenIds.set(data.id, rel(file));
        }
    }
    for (const key of Object.keys(data)) {
        if (!KNOWN_KEYS.has(key)) warn(file, 1, `unknown frontmatter field "${key}"`);
    }

    const bodyLength = [...body].length;
    if (bodyLength < MIN_BODY) {
        fail(file, closing + 1, `body is ${bodyLength} characters, below the ${MIN_BODY} minimum`);
    } else if (bodyLength > MAX_BODY) {
        warn(file, closing + 1, `body is ${bodyLength} characters, above the ${MAX_BODY} guideline`);
    }
    for (const phrase of FORBIDDEN_PHRASES) {
        if (body.includes(phrase)) fail(file, closing + 1, `body uses the forbidden cross-chunk phrase "${phrase}"`);
    }
    for (const reference of body.matchAll(/\[\[([^\]]+)\]\]/g)) {
        const target = reference[1].trim();
        if (!/^[a-z0-9][a-z0-9-]*(\.[a-z0-9-]+)+$/.test(target)) {
            fail(file, closing + 1, `[[${target}]] is not a valid chunk id`);
        }
    }
    bodies.set(file, body);
}

// Resolve [[id]] references once every chunk id is known, so forward references stay legal.
for (const [file, body] of bodies) {
    for (const reference of body.matchAll(/\[\[([^\]]+)\]\]/g)) {
        const target = reference[1].trim();
        if (/^[a-z0-9][a-z0-9-]*(\.[a-z0-9-]+)+$/.test(target) && !seenIds.has(target)) {
            fail(file, 1, `[[${target}]] does not resolve to any chunk id`);
        }
    }
}

const locales = new Map();
for (const file of files) {
    const locale = kbRel(file).split('/')[0];
    locales.set(locale, (locales.get(locale) ?? 0) + 1);
}

for (const item of errors) {
    console.error(`✖ ${item.file}:${item.line} ${item.message}`);
}
if (VERBOSE) {
    for (const item of warnings) {
        console.warn(`⚠ ${item.file}:${item.line} ${item.message}`);
    }
}

const summary = `${bodyTotal} chunks (${[...locales].map(([k, v]) => `${k} ${v}`).join(', ')}), ${features.size} features, ${tools.size} tools`;
if (errors.length > 0) {
    console.error(`\nAI knowledge base check failed: ${errors.length} error(s), ${warnings.length} warning(s) — ${summary}.`);
    process.exitCode = 1;
} else {
    console.log(`AI knowledge base is valid — ${summary}${warnings.length ? `; ${warnings.length} warning(s), rerun with --verbose to list them` : ''}.`);
}
