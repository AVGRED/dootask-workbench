/**
 * Helpers for the capacity CSV export.
 *
 * The export runs in the browser, but the escaping rules are easy to get wrong
 * (spreadsheet formula injection, embedded quotes and line breaks), so they live
 * in a plain module that the node test suite can exercise directly.
 */

/**
 * Escape one CSV cell.
 *
 * Every cell is quoted, inner quotes are doubled, and values that start with a
 * formula trigger are prefixed with an apostrophe so spreadsheets treat them as
 * text instead of executing them.
 */
export function escapeCsvCell(value) {
    let text = String(value ?? '');
    if (/^\s*[=+\-@]/.test(text)) text = `'${text}`;
    return `"${text.replace(/"/g, '""')}"`;
}

/** Join rows into CSV text with CRLF line endings (no BOM). */
export function buildCapacityCsvRows(rows) {
    return rows.map(row => row.map(escapeCsvCell).join(',')).join('\r\n');
}

/** Build the downloadable CSV body: UTF-8 BOM plus CRLF-joined rows. */
export function buildCapacityCsv(rows) {
    return `\uFEFF${buildCapacityCsvRows(rows)}`;
}
