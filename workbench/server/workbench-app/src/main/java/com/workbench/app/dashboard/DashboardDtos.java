package com.workbench.app.dashboard;

import java.util.List;
import java.util.Map;

/** 仪表盘板块目录与布局。 */
public final class DashboardDtos {

    private DashboardDtos() {
    }

    public record WidgetDef(String id, String title, String group, int defaultW, int defaultH) {
    }

    public record LayoutItem(String id, int x, int y, int w, int h) {
    }

    public record DashboardView(List<WidgetDef> catalog, List<LayoutItem> layout,
                                Map<String, Object> data, String status, String statusNote) {
    }

    public record SaveLayoutRequest(List<LayoutItem> items) {
    }

    public record LayoutView(List<LayoutItem> items) {
    }
}
