import assert from 'node:assert/strict';
import test from 'node:test';
import {formatWorkMinutes, sumWorkMinutes} from '../resources/assets/js/utils/workMinutes.js';

const zh = text => text;

test('formatWorkMinutes renders hours and minutes', () => {
    assert.equal(formatWorkMinutes(0, zh), '0小时 0分钟');
    assert.equal(formatWorkMinutes(59, zh), '0小时 59分钟');
    assert.equal(formatWorkMinutes(60, zh), '1小时 0分钟');
    assert.equal(formatWorkMinutes(90, zh), '1小时 30分钟');
    assert.equal(formatWorkMinutes(1440, zh), '24小时 0分钟');
});

test('formatWorkMinutes treats invalid input as zero', () => {
    for (const value of [null, undefined, '', 'abc', NaN]) {
        assert.equal(formatWorkMinutes(value, zh), '0小时 0分钟');
    }
    assert.equal(formatWorkMinutes('120', zh), '2小时 0分钟');
});

test('formatWorkMinutes uses the translator for unit labels', () => {
    const calls = [];
    const translate = text => {
        calls.push(text);
        return text === '小时' ? 'h' : 'm';
    };
    assert.equal(formatWorkMinutes(90, translate), '1h 30m');
    assert.deepEqual(calls, ['小时', '分钟']);
});

test('sumWorkMinutes accepts numbers, objects and skips junk', () => {
    assert.equal(sumWorkMinutes([30, '45', {minutes: 60}]), 135);
    assert.equal(sumWorkMinutes([null, undefined, '', 'x', {minutes: 'bad'}]), 0);
    assert.equal(sumWorkMinutes([]), 0);
    assert.equal(sumWorkMinutes(null), 0);
    assert.equal(sumWorkMinutes('not-an-array'), 0);
});
