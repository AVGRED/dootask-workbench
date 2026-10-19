package com.workbench.app.migrate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.workbench.app.migrate.MigrationPlan.TableStep;
import java.util.List;
import org.junit.jupiter.api.Test;

class MigrationPlanTest {

    @Test
    void tablesAreOrderedByDependency() {
        List<String> order = MigrationPlan.steps().stream().map(TableStep::targetTable).toList();

        assertEquals(8, order.size());
        assertEquals("users", order.get(0));
        assertEquals("departments", order.get(1));
        assertTrue(order.indexOf("projects") < order.indexOf("project_users"));
        assertTrue(order.indexOf("projects") < order.indexOf("project_milestones"));
        assertTrue(order.indexOf("project_milestones") < order.indexOf("project_tasks"));
        assertTrue(order.indexOf("project_tasks") < order.indexOf("project_task_dependencies"));
        assertTrue(order.indexOf("project_tasks") < order.indexOf("project_task_work_logs"));
    }

    @Test
    void everyStepHasColumnsAndDescription() {
        for (TableStep step : MigrationPlan.steps()) {
            assertFalse(step.columns().isEmpty(), step.targetTable());
            assertTrue(step.columns().contains("id") || step.columns().contains("userid"), step.targetTable());
            assertFalse(step.description().isBlank(), step.targetTable());
        }
    }

    @Test
    void summaryListsEveryTableAndTotal() {
        List<TableStep> steps = MigrationPlan.steps();
        List<long[]> counts = steps.stream().map(step -> new long[]{3, 2}).toList();

        String summary = MigrationPlan.formatSummary(steps, counts, true);

        assertTrue(summary.startsWith("MIGRATION DRY-RUN"));
        assertTrue(summary.contains("users"));
        assertTrue(summary.contains("project_task_work_logs"));
        assertTrue(summary.contains("TOTAL copied=16"));
    }
}
