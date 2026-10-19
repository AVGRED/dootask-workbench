package com.workbench.app.search;

import java.util.List;

/** 全局搜索结果。 */
public record SearchResult(
        List<Item> projects,
        List<Item> tasks,
        List<Item> users
) {

    public record Item(long id, String title, String subtitle, String type) {
    }

    public static SearchResult empty() {
        return new SearchResult(List.of(), List.of(), List.of());
    }
}
