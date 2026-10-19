package com.workbench.planning.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workbench.common.api.BusinessException;
import com.workbench.planning.entity.TaskDependency;
import com.workbench.planning.mapper.TaskDependencyMapper;
import com.workbench.planning.web.DependencyDtos.AddDependencyRequest;
import com.workbench.planning.web.DependencyDtos.DependencyView;
import com.workbench.planning.web.DependencyDtos.RiskSummaryView;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 任务依赖：新增（含 BFS 成环检测与同项目校验）、删除、列表与风险汇总。
 *
 * <p>成环判定：新增边 P→S 时，若从 S 沿「后置」方向能回到 P，则该边会成环。</p>
 */
@Service
public class DependencyService {

    private final TaskDependencyMapper dependencyMapper;
    private final JdbcTemplate jdbcTemplate;

    public DependencyService(TaskDependencyMapper dependencyMapper, JdbcTemplate jdbcTemplate) {
        this.dependencyMapper = dependencyMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DependencyView> list(long projectId) {
        return edges(projectId).stream()
                .map(edge -> new DependencyView(edge.getId(), edge.getPredecessorTaskId(), edge.getSuccessorTaskId()))
                .toList();
    }

    public DependencyView add(long currentUserid, long projectId, AddDependencyRequest request) {
        long predecessor = request.predecessorTaskId();
        long successor = request.successorTaskId();
        if (predecessor == successor) {
            throw new BusinessException("不能把任务依赖到自己");
        }
        requireTaskInProject(projectId, predecessor);
        requireTaskInProject(projectId, successor);

        List<TaskDependency> existing = edges(projectId);
        boolean duplicated = existing.stream().anyMatch(edge ->
                Objects.equals(edge.getPredecessorTaskId(), predecessor)
                        && Objects.equals(edge.getSuccessorTaskId(), successor));
        if (duplicated) {
            throw new BusinessException("该依赖已存在");
        }
        if (reaches(existing, successor, predecessor)) {
            throw new BusinessException("依赖成环，无法添加");
        }

        TaskDependency dependency = new TaskDependency();
        dependency.setProjectId(projectId);
        dependency.setPredecessorTaskId(predecessor);
        dependency.setSuccessorTaskId(successor);
        dependency.setCreatedBy(currentUserid);
        dependency.setCreatedAt(LocalDateTime.now());
        dependency.setUpdatedAt(LocalDateTime.now());
        dependencyMapper.insert(dependency);
        return new DependencyView(dependency.getId(), predecessor, successor);
    }

    public void remove(long projectId, long id) {
        TaskDependency dependency = dependencyMapper.selectById(id);
        if (dependency == null || !Long.valueOf(projectId).equals(dependency.getProjectId())) {
            throw new BusinessException("依赖不存在");
        }
        dependencyMapper.deleteById(id);
    }

    public RiskSummaryView riskSummary(long projectId) {
        List<TaskDependency> all = edges(projectId);
        long blocked = 0;
        if (!all.isEmpty()) {
            Long count = jdbcTemplate.queryForObject(
                    "select count(*) from project_task_dependencies d "
                            + "join project_tasks s on s.id = d.successor_task_id "
                            + "join project_tasks p on p.id = d.predecessor_task_id "
                            + "where d.project_id = ? and s.complete_at is null and p.complete_at is null",
                    Long.class,
                    projectId);
            blocked = count == null ? 0 : count;
        }
        Long unowned = jdbcTemplate.queryForObject(
                "select count(*) from project_tasks where project_id = ? and complete_at is null "
                        + "and deleted_at is null and (owner_userid is null or owner_userid = 0)",
                Long.class,
                projectId);
        Long overdue = jdbcTemplate.queryForObject(
                "select count(*) from project_tasks where project_id = ? and complete_at is null "
                        + "and deleted_at is null and end_at is not null and end_at < now()",
                Long.class,
                projectId);
        return new RiskSummaryView(
                all.size(),
                blocked,
                unowned == null ? 0 : unowned,
                overdue == null ? 0 : overdue);
    }

    private List<TaskDependency> edges(long projectId) {
        return dependencyMapper.selectList(
                new LambdaQueryWrapper<TaskDependency>().eq(TaskDependency::getProjectId, projectId));
    }

    /** 从 from 出发沿后置方向能否到达 target。 */
    private boolean reaches(List<TaskDependency> edges, long from, long target) {
        Map<Long, List<Long>> adjacency = new HashMap<>();
        for (TaskDependency edge : edges) {
            adjacency.computeIfAbsent(edge.getPredecessorTaskId(), key -> new ArrayList<>())
                    .add(edge.getSuccessorTaskId());
        }
        Deque<Long> queue = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        queue.add(from);
        visited.add(from);
        while (!queue.isEmpty()) {
            long current = queue.poll();
            if (current == target) {
                return true;
            }
            for (Long next : adjacency.getOrDefault(current, List.of())) {
                if (visited.add(next)) {
                    queue.add(next);
                }
            }
        }
        return false;
    }

    private void requireTaskInProject(long projectId, long taskId) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from project_tasks where id = ? and project_id = ? and deleted_at is null",
                Long.class,
                taskId,
                projectId);
        if (count == null || count == 0) {
            throw new BusinessException("任务不存在或不属于该项目");
        }
    }
}
