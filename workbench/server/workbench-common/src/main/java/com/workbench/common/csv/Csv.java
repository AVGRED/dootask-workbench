package com.workbench.common.csv;

/** CSV 单元格转义：处理逗号/引号/换行，并阻断 Excel 公式注入。 */
public final class Csv {

    private Csv() {
    }

    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        String text = value.replace("\"", "\"\"");
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        if (text.contains(",") || text.contains("\n") || text.contains("\"")) {
            return "\"" + text + "\"";
        }
        return text;
    }
}
