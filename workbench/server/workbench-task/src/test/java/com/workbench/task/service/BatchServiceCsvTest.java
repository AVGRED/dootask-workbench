package com.workbench.task.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BatchServiceCsvTest {

    @Test
    void escapesCommaQuotesAndNewlines() {
        assertEquals("\"a,b\"", BatchService.escape("a,b"));
        assertEquals("\"he said \"\"hi\"\"\"", BatchService.escape("he said \"hi\""));
    }

    @Test
    void neutralisesFormulaInjection() {
        assertEquals("'=1+1", BatchService.escape("=1+1"));
        assertEquals("'+SUM(A1)", BatchService.escape("+SUM(A1)"));
        assertEquals("'-2", BatchService.escape("-2"));
        assertEquals("'@cmd", BatchService.escape("@cmd"));
    }

    @Test
    void keepsPlainNamesUntouched() {
        assertEquals("普通任务", BatchService.escape("普通任务"));
        assertEquals("", BatchService.escape(null));
    }
}
