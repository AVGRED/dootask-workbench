package com.workbench.task.web;

import java.util.List;

/** 看板接口的请求/响应模型。 */
public final class BoardDtos {

    private BoardDtos() {
    }

    public record ColumnView(long id, String name, int sort) {
    }

    public record CardView(long id, String name, String desc, long ownerUserid, int pLevel, boolean completed) {
    }

    public record ColumnWithCards(ColumnView column, List<CardView> cards) {
    }

    public record BoardView(long projectId, List<ColumnWithCards> columns) {
    }

    public record SaveColumnRequest(String name) {
    }

    public record MoveTaskRequest(Long columnId) {
    }
}
