/**
 * Parsers for the project schedule settings.
 *
 * The API stores workdays as a JSON array and holidays as either a JSON array or a
 * free-form text list (one date per line). The settings drawer and the project panel
 * both need the normalized forms, so they live here and are unit-tested directly.
 */

/** Normalize workdays to a sorted-unique list of ISO weekday numbers (1 = Monday). */
export function parseScheduleWorkdays(value) {
    let days = value;
    if (typeof days === 'string') {
        try {
            days = JSON.parse(days);
        } catch (e) {
            days = null;
        }
    }
    return Array.isArray(days) && days.length
        ? [...new Set(days.map(Number).filter(day => Number.isInteger(day) && day >= 1 && day <= 7))]
        : [1, 2, 3, 4, 5];
}

/** Normalize holidays to a sorted list of non-empty date strings. */
export function parseScheduleHolidays(value) {
    let dates = value;
    if (typeof dates === 'string') {
        try {
            const parsed = JSON.parse(dates);
            dates = Array.isArray(parsed) ? parsed : dates;
        } catch (e) {
            // Not JSON: fall through to the delimited text form.
        }
        if (typeof dates === 'string') dates = dates.split(/[\s,;]+/);
    }
    if (!Array.isArray(dates)) return [];
    return [...new Set(dates.map(date => String(date).trim()).filter(Boolean))].sort();
}
