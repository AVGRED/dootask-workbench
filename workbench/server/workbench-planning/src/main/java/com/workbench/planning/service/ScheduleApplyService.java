package com.workbench.planning.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workbench.common.api.BusinessException;
import com.workbench.planning.entity.ScheduleBatch;
import com.workbench.planning.entity.ScheduleBatchItem;
import com.workbench.planning.mapper.ScheduleBatchItemMapper;
import com.workbench.planning.mapper.ScheduleBatchMapper;
import com.workbench.planning.schedule.ScheduleCalculator;
import com.workbench.planning.schedule.ScheduleCalculator.Edge;
import com.workbench.planning.schedule.ScheduleCalculator.TaskSlot;
import com.workbench.planning.web.ScheduleApplyDtos.ApplyRequest;
import com.workbench.planning.web.ScheduleApplyDtos.BatchView;
import com.workbench.planning.web.ScheduleDtos.PreviewRequest;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 排期应用与撤销：apply 把预演结果落库并记录前后值，undo 按批次还原。
 *
 * <p>{@code request_id} 与 (project, request) 唯一键保证重复提交不会重复移动任务。</p>
 */
@Service
public class ScheduleApplyService {

    private final ScheduleBatchMapper batchMapper;
    private final ScheduleBatchItemMapper itemMapper;
    private final TaskScheduleService scheduleService;
    private final JdbcTemplate jdbcTemplate;

    public ScheduleApplyService(ScheduleBatchMapper batchMapper, ScheduleBatchItemMapper itemMapper,
                                TaskScheduleService scheduleService, JdbcTemplate jdbcTemplate) {
        this.batchMapper = batchMapper;
        this.itemMapper = itemMapper;
        this.scheduleService = scheduleService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public BatchView apply(long currentUserid, long projectId, ApplyRequest request) {
        ScheduleBatch existing = batchMapper.selectOne(new LambdaQueryWrapper<ScheduleBatch>()
                .eq(ScheduleBatch::getProjectId, projectId)
                .eq(ScheduleBatch::getRequestId, request.requestId())
                .last("limit 1"));
        if (existing != null) {
            return toView(existing);
        }

        PreviewRequest previewRequest = new PreviewRequest(request.anchorTaskId(), request.anchorStart(),
                request.anchorEnd(), request.workdays());
        var preview = scheduleService.preview(projectId, previewRequest);

        LocalDateTime now = LocalDateTime.now();
        ScheduleBatch batch = new ScheduleBatch();
        batch.setProjectId(projectId);
        batch.setUserid(currentUserid);
        batch.setRequestId(request.requestId());
        batch.setChangeCount(preview.changeCount());
        batch.setCreatedAt(now);
        batchMapper.insert(batch);

        for (var change : preview.changes()) {
            ScheduleBatchItem item = new ScheduleBatchItem();
            item.setBatchId(batch.getId());
            item.setTaskId(change.taskId());
            item.setOldStart(change.oldStart());
            item.setOldEnd(change.oldEnd());
            item.setNewStart(change.newStart());
            item.setNewEnd(change.newEnd());
            itemMapper.insert(item);
            jdbcTemplate.update(
                    "update project_tasks set start_at = ?, end_at = ?, updated_at = ? where id = ?",
                    change.newStart(), change.newEnd(), now, change.taskId());
        }
        return toView(batch);
    }

    @Transactional
    public BatchView undo(long currentUserid, long batchId) {
        ScheduleBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new BusinessException("批次不存在");
        }
        if (batch.getUndoneAt() != null) {
            throw new BusinessException("该批次已撤销");
        }
        List<ScheduleBatchItem> items = itemMapper.selectList(new LambdaQueryWrapper<ScheduleBatchItem>()
                .eq(ScheduleBatchItem::getBatchId, batchId));
        LocalDateTime now = LocalDateTime.now();
        for (ScheduleBatchItem item : items) {
            // 旧值可能为 null（任务原本没有日期），必须显式置空。
            jdbcTemplate.update(
                    "update project_tasks set start_at = ?, end_at = ?, updated_at = ? where id = ?",
                    item.getOldStart(), item.getOldEnd(), now, item.getTaskId());
        }
        jdbcTemplate.update("update schedule_batches set undone_at = ? where id = ?", now, batchId);
        batch.setUndoneAt(now);
        return toView(batch);
    }

    public List<BatchView> batches(long projectId) {
        return batchMapper.selectList(new LambdaQueryWrapper<ScheduleBatch>()
                        .eq(ScheduleBatch::getProjectId, projectId)
                        .orderByDesc(ScheduleBatch::getId))
                .stream()
                .map(ScheduleApplyService::toView)
                .toList();
    }

    private static BatchView toView(ScheduleBatch batch) {
        return new BatchView(batch.getId(), batch.getRequestId(),
                batch.getChangeCount() == null ? 0 : batch.getChangeCount(),
                batch.getCreatedAt(), batch.getUndoneAt() != null);
    }

    /** 供测试：把整数工作日列表转成 DayOfWeek 集合。 */
    static Set<DayOfWeek> toWorkdays(List<Integer> workdays) {
        if (workdays == null || workdays.isEmpty()) {
            return Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
                    DayOfWeek.FRIDAY);
        }
        Set<DayOfWeek> result = new HashSet<>();
        for (Integer day : workdays) {
            if (day != null && day >= 1 && day <= 7) {
                result.add(DayOfWeek.of(day));
            }
        }
        return result;
    }

    /** 供测试：纯函数入口，避免为了验证算法而启动 Spring。 */
    public static List<ScheduleCalculator.Change> compute(List<TaskSlot> slots, List<Edge> edges, long anchorId,
                                                          LocalDateTime start, LocalDateTime end,
                                                          List<Integer> workdays) {
        return ScheduleCalculator.preview(slots, edges, anchorId, start, end, toWorkdays(workdays));
    }
}
