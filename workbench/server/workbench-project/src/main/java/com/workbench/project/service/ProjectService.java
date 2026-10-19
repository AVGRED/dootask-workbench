package com.workbench.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workbench.common.api.BusinessException;
import com.workbench.common.api.PageResult;
import com.workbench.project.entity.Project;
import com.workbench.project.entity.ProjectMember;
import com.workbench.project.mapper.ProjectMapper;
import com.workbench.project.mapper.ProjectMemberMapper;
import com.workbench.project.web.ProjectDtos.AddMemberRequest;
import com.workbench.project.web.ProjectDtos.MemberView;
import com.workbench.project.web.ProjectDtos.ProjectView;
import com.workbench.project.web.ProjectDtos.SaveProjectRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 项目与成员。 */
@Service
public class ProjectService {

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper memberMapper;
    private final ActivityService activityService;

    public ProjectService(ProjectMapper projectMapper, ProjectMemberMapper memberMapper,
                          ActivityService activityService) {
        this.projectMapper = projectMapper;
        this.memberMapper = memberMapper;
        this.activityService = activityService;
    }

    public PageResult<ProjectView> page(long currentUserid, int page, int size, String keyword) {
        List<Long> memberProjectIds = memberMapper
                .selectList(new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getUserid, currentUserid))
                .stream()
                .map(ProjectMember::getProjectId)
                .toList();

        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<Project>()
                .and(query -> {
                    query.eq(Project::getUserid, currentUserid);
                    if (!memberProjectIds.isEmpty()) {
                        query.or().in(Project::getId, memberProjectIds);
                    }
                })
                .orderByDesc(Project::getId);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(Project::getName, keyword);
        }

        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<Project> result = projectMapper.selectPage(new Page<>(Math.max(page, 1), safeSize), wrapper);
        List<Long> ids = result.getRecords().stream().map(Project::getId).toList();
        Map<Long, Long> counts = ids.isEmpty()
                ? Map.of()
                : memberMapper.selectList(new LambdaQueryWrapper<ProjectMember>().in(ProjectMember::getProjectId, ids))
                        .stream()
                        .collect(Collectors.groupingBy(ProjectMember::getProjectId, Collectors.counting()));
        List<ProjectView> list = result.getRecords().stream()
                .map(project -> toView(project, counts.getOrDefault(project.getId(), 0L)))
                .toList();
        return PageResult.of(list, result.getTotal(), (int) result.getCurrent(), (int) result.getSize());
    }

    @Transactional
    public ProjectView create(long currentUserid, SaveProjectRequest request) {
        Project project = new Project();
        project.setName(request.name());
        project.setDesc(request.desc() == null ? "" : request.desc());
        project.setUserid(currentUserid);
        project.setDialogId(0L);
        project.setArchivedUserid(0L);
        project.setCreatedAt(LocalDateTime.now());
        project.setUpdatedAt(LocalDateTime.now());
        projectMapper.insert(project);

        ProjectMember member = new ProjectMember();
        member.setProjectId(project.getId());
        member.setUserid(currentUserid);
        member.setOwner(1);
        member.setRole("owner");
        member.setCreatedAt(LocalDateTime.now());
        member.setUpdatedAt(LocalDateTime.now());
        memberMapper.insert(member);

        activityService.record(project.getId(), null, currentUserid, "project.create", "project", project.getId(),
                "创建项目：" + project.getName());
        return toView(project, 1);
    }

    public ProjectView update(long currentUserid, long projectId, SaveProjectRequest request) {
        Project project = requireProjectAdmin(currentUserid, projectId);
        project.setName(request.name());
        if (request.desc() != null) project.setDesc(request.desc());
        project.setUpdatedAt(LocalDateTime.now());
        projectMapper.updateById(project);
        return toView(project, countMembers(projectId));
    }

    public void archive(long currentUserid, long projectId, boolean archived) {
        Project project = requireProjectAdmin(currentUserid, projectId);
        LocalDateTime now = LocalDateTime.now();
        if (archived) {
            project.setArchivedAt(now);
            project.setArchivedUserid(currentUserid);
            project.setUpdatedAt(now);
            projectMapper.updateById(project);
        } else {
            // updateById 忽略 null 字段，恢复归档必须显式置空。
            projectMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Project>()
                    .eq(Project::getId, projectId)
                    .set(Project::getArchivedAt, null)
                    .set(Project::getArchivedUserid, 0L)
                    .set(Project::getUpdatedAt, now));
        }
        activityService.record(projectId, null, currentUserid, archived ? "project.archive" : "project.restore",
                "project", projectId, archived ? "归档项目" : "恢复项目");
    }

    @Transactional
    public void delete(long currentUserid, long projectId) {
        requireOwner(currentUserid, projectId);
        memberMapper.delete(new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getProjectId, projectId));
        projectMapper.deleteById(projectId);
    }

    public List<MemberView> members(long projectId) {
        return memberMapper
                .selectList(new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getProjectId, projectId))
                .stream()
                .map(member -> new MemberView(member.getUserid(), Objects.equals(member.getOwner(), 1),
                        roleOf(member)))
                .toList();
    }

    public MemberView addMember(long currentUserid, long projectId, AddMemberRequest request) {
        requireProjectAdmin(currentUserid, projectId);
        Long exists = memberMapper.selectCount(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getUserid, request.userid()));
        if (exists != null && exists > 0) {
            throw new BusinessException("该成员已在项目中");
        }
        String role = request.role() == null ? "member" : request.role();
        ProjectMember member = new ProjectMember();
        member.setProjectId(projectId);
        member.setUserid(request.userid());
        member.setOwner(0);
        member.setRole(role);
        member.setCreatedAt(LocalDateTime.now());
        member.setUpdatedAt(LocalDateTime.now());
        memberMapper.insert(member);
        return new MemberView(member.getUserid(), false, role);
    }

    public void removeMember(long currentUserid, long projectId, long userid) {
        Project project = requireProjectAdmin(currentUserid, projectId);
        if (Objects.equals(project.getUserid(), userid)) {
            throw new BusinessException("不能移除项目负责人");
        }
        memberMapper.delete(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getUserid, userid));
    }

    private static String roleOf(ProjectMember member) {
        if (Objects.equals(member.getOwner(), 1)) {
            return "owner";
        }
        return member.getRole() == null || member.getRole().isBlank() ? "member" : member.getRole();
    }

    /** 项目管理员：负责人，或成员角色为 owner/admin。 */
    private Project requireProjectAdmin(long currentUserid, long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException("项目不存在");
        }
        if (Objects.equals(project.getUserid(), currentUserid)) {
            return project;
        }
        ProjectMember member = memberMapper.selectOne(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getUserid, currentUserid)
                .last("limit 1"));
        String role = member == null ? "" : roleOf(member);
        if ("owner".equals(role) || "admin".equals(role)) {
            return project;
        }
        throw new BusinessException(403, "需要项目管理员权限");
    }

    private Project requireOwner(long currentUserid, long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException("项目不存在");
        }
        if (!Objects.equals(project.getUserid(), currentUserid)) {
            throw new BusinessException("无权操作该项目");
        }
        return project;
    }

    private long countMembers(long projectId) {
        Long count = memberMapper.selectCount(
                new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getProjectId, projectId));
        return count == null ? 0 : count;
    }

    private static ProjectView toView(Project project, long memberCount) {
        return new ProjectView(
                project.getId(),
                project.getName(),
                project.getDesc(),
                project.getUserid(),
                project.getArchivedAt() != null,
                memberCount);
    }
}
