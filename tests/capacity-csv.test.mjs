import assert from 'node:assert/strict';
import test from 'node:test';
import {
    buildCapacityCsv,
    buildCapacityCsvRows,
    escapeCsvCell,
} from '../resources/assets/js/utils/capacityCsv.js';

test('escapeCsvCell always quotes and doubles inner quotes', () => {
    assert.equal(escapeCsvCell('张三'), '"张三"');
    assert.equal(escapeCsvCell('say "hi"'), '"say ""hi"""');
    assert.equal(escapeCsvCell(''), '""');
});

test('escapeCsvCell neutralises spreadsheet formulas', () => {
    assert.equal(escapeCsvCell('=1+1'), '"\'=1+1"');
    assert.equal(escapeCsvCell('+42'), '"\'+42"');
    assert.equal(escapeCsvCell('-42'), '"\'-42"');
    assert.equal(escapeCsvCell('@SUM(A1)'), '"\'@SUM(A1)"');
    assert.equal(escapeCsvCell('  =cmd|calc'), '"\'  =cmd|calc"');
});

test('escapeCsvCell leaves already-safe values untouched apart from quoting', () => {
    assert.equal(escapeCsvCell('普通文本'), '"普通文本"');
    assert.equal(escapeCsvCell('a=b'), '"a=b"');
});

test('escapeCsvCell renders nullish values as empty cells', () => {
    assert.equal(escapeCsvCell(null), '""');
    assert.equal(escapeCsvCell(undefined), '""');
    assert.equal(escapeCsvCell(0), '"0"');
});

test('buildCapacityCsvRows joins cells with commas and rows with CRLF', () => {
    const rows = [
        ['成员', '任务数'],
        ['张三', 2],
    ];
    assert.equal(buildCapacityCsvRows(rows), '"成员","任务数"\r\n"张三","2"');
});

test('buildCapacityCsv prefixes a UTF-8 BOM exactly once', () => {
    const csv = buildCapacityCsv([['成员'], ['李四']]);
    assert.equal(csv.startsWith('\uFEFF'), true);
    assert.equal(csv.slice(1).startsWith('\uFEFF'), false);
    assert.equal(csv, '\uFEFF"成员"\r\n"李四"');
});

test('line breaks and commas stay inside a single quoted cell', () => {
    const csv = buildCapacityCsvRows([['多行\n备注, 含逗号']]);
    assert.equal(csv, '"多行\n备注, 含逗号"');
    // One row, one cell: only the surrounding quotes are commas/line-break free.
    const cells = csv.slice(1, -1);
    assert.equal(cells.includes('"'), false);
});
