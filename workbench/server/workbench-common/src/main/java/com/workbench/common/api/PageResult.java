package com.workbench.common.api;

/**
 * 统一分页返回：{@code {list, total, page, size}}。
 *
 * <p>与旧系统各列表接口的字段保持一致，前端无需按接口写不同的取数逻辑。</p>
 */
public record PageResult<T>(java.util.List<T> list, long total, int page, int size) {

    public static <T> PageResult<T> of(java.util.List<T> list, long total, int page, int size) {
        return new PageResult<>(list, total, page, size);
    }

    public static <T> PageResult<T> empty(int page, int size) {
        return new PageResult<>(java.util.List.of(), 0, page, size);
    }
}
