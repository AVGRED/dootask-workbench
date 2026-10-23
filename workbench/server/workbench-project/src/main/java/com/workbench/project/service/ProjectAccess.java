package com.workbench.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workbench.common.api.BusinessException;
import com.workbench.project.entity.Project;
import com.workbench.project.entity.ProjectMember;
import com.workbench.project.mapper.ProjectMapper;
import com.workbench.project.mapper.ProjectMemberMapper;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 项目访问控制：角色 owner / admin / member / viewer。
 *
 * <p>读：任何项目成员；写：除 viewer 外的成员；管理：owner 或 admin。</p>
 */
@Component
public class ProjectAccess {

    public static final String ROLE_OWNER = "owner";
    public static final String ROLE_ADMIN = "admin";
    public static final String ROLE_MEMBER = "member";
    public static final String ROLE_VIEWER = "viewer";

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper memberMapper;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    public ProjectAccess(ProjectMapper projectMapper,
                         ProjectMemberMapper memberMapper,
                         org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        this.projectMapper = projectMapper;
        this.memberMapper = memberMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 系统管理员对任何项目都有完整权限（管理端要能改所有内容）。 */
    private boolean isSystemAdmin(long userid) {
        try {
            String role = jdbcTemplate.queryForObject(
                    "select coalesce(role, 'staff') from users where userid = ? and deleted_at is null",
                    String.class, userid);
            return "admin".equals(role);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /** 非成员返回 null；项目不存在抛业务异常。 */
    public String roleOf(long projectId, long userid) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException("项目不存在");
        }
        if (Objects.equals(project.getUserid(), userid) || isSystemAdmin(userid)) {
            return ROLE_OWNER;
        }
        ProjectMember member = memberMapper.selectOne(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getUserid, userid)
                .last("limit 1"));
        if (member == null) {
            return null;
        }
        if (Objects.equals(member.getOwner(), 1)) {
            return ROLE_OWNER;
        }
        return member.getRole() == null || member.getRole().isBlank() ? ROLE_MEMBER : member.getRole();
    }

    public void requireAccess(long projectId, long userid) {
        if (roleOf(projectId, userid) == null) {
            throw new BusinessException(403, "无权访问该项目");
        }
    }

    public void requireWrite(long projectId, long userid) {
        String role = roleOf(projectId, userid);
        if (role == null) {
            throw new BusinessException(403, "无权访问该项目");
        }
        if (ROLE_VIEWER.equals(role)) {
            throw new BusinessException(403, "只读成员不能修改该项目的任务");
        }
    }
}
