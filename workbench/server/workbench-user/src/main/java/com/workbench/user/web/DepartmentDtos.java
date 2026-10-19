package com.workbench.user.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 部门接口的请求/响应模型。 */
public final class DepartmentDtos {

    private DepartmentDtos() {
    }

    public record SaveDepartmentRequest(
            @NotBlank(message = "请输入部门名称") @Size(max = 100) String name,
            Long parentId,
            Long ownerUserid
    ) {
    }

    public record DepartmentView(
            long id,
            String name,
            long parentId,
            long ownerUserid,
            long childrenCount
    ) {
    }
}
