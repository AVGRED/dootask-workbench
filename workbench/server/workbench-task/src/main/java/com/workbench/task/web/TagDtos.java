package com.workbench.task.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 标签接口的请求/响应模型。 */
public final class TagDtos {

    private TagDtos() {
    }

    public record SaveTagRequest(
            @NotBlank(message = "请填写标签名") @Size(max = 50) String name,
            @Size(max = 20) String color
    ) {
    }

    public record TagView(long id, String name, String color) {
    }

    public record AttachTagRequest(@NotNull(message = "请选择标签") Long tagId) {
    }

    public record TagList(List<TagView> tags) {
    }
}
