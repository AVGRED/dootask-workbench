import assert from 'node:assert/strict';
import test from 'node:test';
import {parseScheduleHolidays, parseScheduleWorkdays} from '../resources/assets/js/utils/projectSchedule.js';

test('parseScheduleWorkdays normalizes numbers, strings and duplicates', () => {
    assert.deepEqual(parseScheduleWorkdays(['1', '2', 2, '7']), [1, 2, 7]);
    assert.deepEqual(parseScheduleWorkdays('["1","3"]'), [1, 3]);
    assert.deepEqual(parseScheduleWorkdays([3, 1]), [3, 1]);
});

test('parseScheduleWorkdays falls back to Monday-Friday', () => {
    for (const value of [null, undefined, '', 'not-json', [], '[]']) {
        assert.deepEqual(parseScheduleWorkdays(value), [1, 2, 3, 4, 5]);
    }
});

test('parseScheduleWorkdays drops out-of-range days instead of padding them', () => {
    assert.deepEqual(parseScheduleWorkdays([0, 8, 9]), []);
    assert.deepEqual(parseScheduleWorkdays(['0', '9']), []);
});

test('parseScheduleHolidays accepts newline, comma and semicolon separated text', () => {
    assert.deepEqual(parseScheduleHolidays('2026-10-02\n2026-10-01'), ['2026-10-01', '2026-10-02']);
    assert.deepEqual(parseScheduleHolidays('b, a; c'), ['a', 'b', 'c']);
    assert.deepEqual(parseScheduleHolidays('  2026-10-01 ,, 2026-10-01 '), ['2026-10-01']);
});

test('parseScheduleHolidays parses JSON arrays and ignores junk', () => {
    assert.deepEqual(parseScheduleHolidays('["2026-10-03","2026-10-01"]'), ['2026-10-01', '2026-10-03']);
    assert.deepEqual(parseScheduleHolidays(['2026-10-03', ' 2026-10-01 ']), ['2026-10-01', '2026-10-03']);
    assert.deepEqual(parseScheduleHolidays(null), []);
    assert.deepEqual(parseScheduleHolidays({}), []);
});
