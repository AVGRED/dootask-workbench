package com.workbench.planning.web;

import java.time.LocalDate;
import java.util.List;

/** 甘特图只读数据。 */
public final class GanttDtos {

    private GanttDtos() {
    }

    public record GanttTask(
            long id,
            String name,
            LocalDate startDate,
            LocalDate endDate,
            long ownerUserid,
            int pLevel,
            boolean completed,
            Long milestoneId
    ) {
    }

    public record GanttEdge(long predecessorTaskId, long successorTaskId) {
    }

    public record GanttView(
            long projectId,
            LocalDate rangeStart,
            LocalDate rangeEnd,
            List<GanttTask> tasks,
            List<GanttEdge> edges
    ) {
    }
}
