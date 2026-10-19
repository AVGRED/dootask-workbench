/**
 * 工时分钟数的统一格式化。
 *
 * 旧代码在多个组件里各写了一份（容量汇总、任务工时、CSV 导出），格式一致但
 * 边界处理不统一；重写时这些组件会迁到 packages/ui，先把规则固定成可测函数。
 *
 * @param {number|string|null|undefined} minutes 分钟数，非法值按 0 处理
 * @param {(text: string) => string} translate 翻译函数，默认原样返回
 * @returns {string} 例如 "8小时 30分钟"
 */
export function formatWorkMinutes(minutes, translate = text => text) {
    const value = Number(minutes) || 0;
    const hours = Math.floor(value / 60);
    const remainder = value % 60;
    return `${hours}${translate('小时')} ${remainder}${translate('分钟')}`;
}

/**
 * 把一组工时记录按分钟求和，忽略非法值。
 *
 * @param {Array<number|string|{minutes: number|string}>} entries
 * @returns {number}
 */
export function sumWorkMinutes(entries) {
    if (!Array.isArray(entries)) return 0;
    return entries.reduce((total, entry) => {
        const minutes = Number(entry && typeof entry === 'object' ? entry.minutes : entry) || 0;
        return total + (Number.isFinite(minutes) ? minutes : 0);
    }, 0);
}
