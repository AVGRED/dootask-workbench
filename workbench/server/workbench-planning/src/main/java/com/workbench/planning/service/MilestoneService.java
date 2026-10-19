package com.workbench.planning.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workbench.common.api.BusinessException;
import com.workbench.planning.entity.Milestone;
import com.workbench.planning.mapper.MilestoneMapper;
import com.workbench.planning.web.MilestoneDtos.MilestoneView;
import com.workbench.planning.web.MilestoneDtos.SaveMilestoneRequest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 里程碑：列表（带任务计数）、创建、修改、删除（解绑任务）。
 *
 * <p>任务关联数用 {@link JdbcTemplate} 直接聚合，避免跨模块依赖 Task 实体。</p>
 */
@Service
public class MilestoneService {

    private final MilestoneMapper milestoneMapper;
    private final JdbcTemplate jdbcTemplate;

    public MilestoneService(MilestoneMapper milestoneMapper, JdbcTemplate jdbcTemplate) {
        this.milestoneMapper = milestoneMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<MilestoneView> list(long projectId) {
        List<Milestone> milestones = milestoneMapper.selectList(new LambdaQueryWrapper<Milestone>()
                .eq(Milestone::getProjectId, projectId)
                .orderByAsc(Milestone::getSort)
                .orderByAsc(Milestone::getId));
        Map<Long, long[]> counts = taskCounts(projectId);
        return milestones.stream()
                .map(milestone -> toView(milestone, counts.getOrDefault(milestone.getId(), new long[]{0, 0})))
                .toList();
    }

    public MilestoneView create(long currentUserid, long projectId, SaveMilestoneRequest request) {
        Milestone milestone = new Milestone();
        milestone.setProjectId(projectId);
        milestone.setName(request.name());
        milestone.setDescription(request.description() == null ? "" : request.description());
        milestone.setOwnerUserid(request.ownerUserid() == null ? 0L : request.ownerUserid());
        milestone.setTargetAt(request.targetAt());
        milestone.setStatus(request.status() == null ? "open" : request.status());
        milestone.setReminderDays(request.reminderDays() == null ? -1 : request.reminderDays());
        milestone.setSort(nextSort(projectId));
        milestone.setCreatedBy(currentUserid);
        milestone.setCreatedAt(LocalDateTime.now());
        milestone.setUpdatedAt(LocalDateTime.now());
        milestoneMapper.insert(milestone);
        return toView(milestone, new long[]{0, 0});
    }

    public MilestoneView update(long projectId, long id, SaveMilestoneRequest request) {
        Milestone milestone = require(projectId, id);
        milestone.setName(request.name());
        if (request.description() != null) milestone.setDescription(request.description());
        if (request.ownerUserid() != null) milestone.setOwnerUserid(request.ownerUserid());
        if (request.targetAt() != null) milestone.setTargetAt(request.targetAt());
        if (request.status() != null) milestone.setStatus(request.status());
        if (request.reminderDays() != null) milestone.setReminderDays(request.reminderDays());
        milestone.setUpdatedAt(LocalDateTime.now());
        milestoneMapper.updateById(milestone);
        return toView(milestone, taskCounts(projectId).getOrDefault(id, new long[]{0, 0}));
    }

    /** 删除时把所有关联任务的 milestone_id 置空，任务本身保留。 */
    @Transactional
    public void delete(long projectId, long id) {
        require(projectId, id);
        jdbcTemplate.update("update project_tasks set milestone_id = null where project_id = ? and milestone_id = ?",
                projectId, id);
        milestoneMapper.deleteById(id);
    }

    private Milestone require(long projectId, long id) {
        Milestone milestone = milestoneMapper.selectById(id);
        if (milestone == null || !Long.valueOf(projectId).equals(milestone.getProjectId())) {
            throw new BusinessException("里程碑不存在");
        }
        return milestone;
    }

    private int nextSort(long projectId) {
        List<Milestone> all = milestoneMapper.selectList(
                new LambdaQueryWrapper<Milestone>().eq(Milestone::getProjectId, projectId));
        return all.stream().mapToInt(item -> item.getSort() == null ? 0 : item.getSort()).max().orElse(0) + 1;
    }

    private Map<Long, long[]> taskCounts(long projectId) {
        Map<Long, long[]> counts = new HashMap<>();
        jdbcTemplate.query(
                "select milestone_id, count(*) as total, sum(case when complete_at is not null then 1 else 0 end) as done "
                        + "from project_tasks where project_id = ? and milestone_id is not null and deleted_at is null "
                        + "group by milestone_id",
                resultSet -> {
                    long milestoneId = resultSet.getLong("milestone_id");
                    counts.put(milestoneId, new long[]{resultSet.getLong("total"), resultSet.getLong("done")});
                },
                projectId);
        return counts;
    }

    private static MilestoneView toView(Milestone milestone, long[] counts) {
        return new MilestoneView(
                milestone.getId(),
                milestone.getName(),
                milestone.getDescription(),
                milestone.getOwnerUserid() == null ? 0L : milestone.getOwnerUserid(),
                milestone.getTargetAt(),
                milestone.getStatus(),
                milestone.getReminderDays() == null ? -1 : milestone.getReminderDays(),
                milestone.getSort() == null ? 0 : milestone.getSort(),
                counts[0],
                counts[1]);
    }
}
