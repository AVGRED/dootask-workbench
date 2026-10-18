#!/usr/bin/env node
/** Check API message source, translation rows, and positional runtime locale arrays. */

import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const LOCALES = ['key', 'zh', 'zh-CHT', 'en', 'ko', 'ja', 'de', 'fr', 'id', 'ru'];
const normalize = value => value.replace(/\(%T\d+\)/g, '(*)').replace(/\(%M\d+\)/g, '(**)');
const readJson = file => JSON.parse(fs.readFileSync(path.join(ROOT, file), 'utf8'));
const errors = [];

const sourceKeys = [...new Set(readJsonLines('language/original-api.txt').map(normalize))];
const translations = readJson('language/translate.json');
const translationByKey = new Map();
for (const [index, row] of translations.entries()) {
    if (!row || typeof row.key !== 'string') {
        errors.push(`language/translate.json[${index}] has no string key`);
        continue;
    }
    const key = normalize(row.key);
    if (translationByKey.has(key)) errors.push(`Duplicate normalized API translation key: ${key}`);
    else translationByKey.set(key, row);
    for (const locale of LOCALES) {
        if (typeof row[locale] !== 'string') errors.push(`language/translate.json[${index}].${locale} must be a string`);
    }
}

let runtimeKeys = [];
try {
    runtimeKeys = readJson('public/language/api/key.json');
    if (!Array.isArray(runtimeKeys) || runtimeKeys.some(key => typeof key !== 'string')) {
        errors.push('public/language/api/key.json must be an array of strings');
        runtimeKeys = [];
    }
} catch (error) {
    errors.push(`public/language/api/key.json: ${error.message}`);
}

const normalizedRuntimeKeys = runtimeKeys.map(normalize);
if (new Set(normalizedRuntimeKeys).size !== normalizedRuntimeKeys.length) {
    errors.push('public/language/api/key.json contains duplicate normalized keys');
}
const sourceSet = new Set(sourceKeys);
const runtimeSet = new Set(normalizedRuntimeKeys);
for (const key of sourceKeys) if (!runtimeSet.has(key)) errors.push(`API runtime key missing from original-api.txt: ${key}`);
for (const key of runtimeSet) if (!sourceSet.has(key)) errors.push(`API runtime key missing from original-api.txt: ${key}`);

for (const locale of LOCALES) {
    let values;
    try {
        values = readJson(`public/language/api/${locale}.json`);
    } catch (error) {
        errors.push(`public/language/api/${locale}.json: ${error.message}`);
        continue;
    }
    if (!Array.isArray(values)) {
        errors.push(`public/language/api/${locale}.json must be an array`);
        continue;
    }
    if (values.length !== runtimeKeys.length) {
        errors.push(`public/language/api/${locale}.json has ${values.length} entries; expected ${runtimeKeys.length}`);
        continue;
    }
    runtimeKeys.forEach((key, index) => {
        const translation = translationByKey.get(normalize(key));
        if (!translation) {
            errors.push(`No translation row for API key at ${locale}[${index}]: ${key}`);
            return;
        }
        const expected = locale === 'key' ? key : (translation[locale] || '');
        if (values[index] !== expected) {
            errors.push(`public/language/api/${locale}.json[${index}] does not match translation for: ${key}`);
        }
    });
}

if (errors.length) {
    console.error(`API language check failed with ${errors.length} error(s):`);
    for (const error of errors) console.error(`- ${error}`);
    process.exitCode = 1;
} else {
    console.log(`API language arrays match ${runtimeKeys.length} messages across ${LOCALES.length} locales.`);
}

function readJsonLines(file) {
    return fs.readFileSync(path.join(ROOT, file), 'utf8')
        .split(/\r?\n/)
        .map(line => line.trim())
        .filter(Boolean);
}
