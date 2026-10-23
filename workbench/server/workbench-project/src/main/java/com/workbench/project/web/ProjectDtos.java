package com.workbench.project.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 项目接口的请求/响应模型。 */
public final class ProjectDtos {

    private ProjectDtos() {
    }

    public record SaveProjectRequest(
            @NotBlank(message = "请输入项目名称") @Size(max = 255) String name,
            @Size(max = 500) String desc
    ) {
    }

    public record ProjectView(
            long id,
            String name,
            String desc,
            long ownerUserid,
            boolean archived,
            long memberCount
    ) {
    }

    public record AddMemberRequest(
            @NotNull(message = "请选择成员") Long userid,
            @Pattern(regexp = "owner|admin|member|viewer", message = "角色只能是 owner/admin/member/viewer") String role
    ) {
    }

    public record MemberView(
            long userid,
            boolean owner,
            String role,
            String nickname,
            String account
    ) {
    }
}
