package com.workbench.task.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** 批量操作请求。 */
public final class BatchDtos {

    private BatchDtos() {
    }

    public record BatchRequest(
            @NotBlank(message = "请选择操作") String action,
            @NotEmpty(message = "请选择任务") List<Long> taskIds,
            Long ownerUserid
    ) {
    }

    public record BatchResult(int affected, String action) {
    }
}
