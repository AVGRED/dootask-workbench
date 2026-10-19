package com.workbench.user.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/** 系统设置与字典的请求/响应模型。 */
public final class SystemDtos {

    private SystemDtos() {
    }

    public record SettingView(String key, String value, String description, LocalDateTime updatedAt) {
    }

    public record SaveSettingRequest(
            @NotBlank(message = "设置值不能为空") @Size(max = 5000) String value,
            @Size(max = 255) String description
    ) {
    }

    public record PublicSettingsView(List<SettingView> items) {
    }

    public record DictItemView(long id, String type, String code, String label, int sort, boolean enabled) {
    }

    public record SaveDictRequest(
            @NotBlank(message = "请填写字典类型") @Size(max = 50) String type,
            @NotBlank(message = "请填写编码") @Size(max = 50) String code,
            @NotBlank(message = "请填写显示名") @Size(max = 100) String label,
            Integer sort,
            Boolean enabled
    ) {
    }

    public record ChangeRoleRequest(
            @NotBlank(message = "请选择角色") String role
    ) {
    }
}
