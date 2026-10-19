package com.workbench.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.workbench.common.api.BusinessException;
import com.workbench.project.service.ProjectAccess;
import com.workbench.task.entity.Task;
import com.workbench.task.entity.TaskColumn;
import com.workbench.task.mapper.TaskColumnMapper;
import com.workbench.task.mapper.TaskMapper;
import com.workbench.task.web.BoardDtos.BoardView;
import com.workbench.task.web.BoardDtos.CardView;
import com.workbench.task.web.BoardDtos.ColumnView;
import com.workbench.task.web.BoardDtos.ColumnWithCards;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 看板：按列聚合任务、拖拽换列。
 *
 * <p>项目第一次打开看板时自动创建「待办 / 进行中 / 已完成」三列；
 * 没有归属列的任务落在第一列。</p>
 */
@Service
public class BoardService {

    private static final List<String> DEFAULT_COLUMNS = List.of("待办", "进行中", "已完成");

    private final TaskColumnMapper columnMapper;
    private final TaskMapper taskMapper;
    private final ProjectAccess projectAccess;

    public BoardService(TaskColumnMapper columnMapper, TaskMapper taskMapper, ProjectAccess projectAccess) {
        this.columnMapper = columnMapper;
        this.taskMapper = taskMapper;
        this.projectAccess = projectAccess;
    }

    @Transactional
    public BoardView board(long projectId, long currentUserid) {
        projectAccess.requireAccess(projectId, currentUserid);
        List<TaskColumn> columns = ensureColumns(projectId);

        List<Task> tasks = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getProjectId, projectId)
                .isNull(Task::getCompleteAt)
                .orderByAsc(Task::getSort)
                .orderByDesc(Task::getId));

        long firstColumnId = columns.get(0).getId();
        List<ColumnWithCards> result = new ArrayList<>();
        for (TaskColumn column : columns) {
            List<CardView> cards = new ArrayList<>();
            for (Task task : tasks) {
                long taskColumn = task.getColumnId() == null ? 0 : task.getColumnId();
                boolean belongs = column.getId().equals(taskColumn)
                        || (taskColumn == 0 && column.getId().equals(firstColumnId));
                if (belongs) {
                    cards.add(new CardView(task.getId(), task.getName(), task.getDesc(),
                            task.getOwnerUserid() == null ? 0 : task.getOwnerUserid(),
                            task.getPLevel() == null ? 0 : task.getPLevel(), false));
                }
            }
            result.add(new ColumnWithCards(
                    new ColumnView(column.getId(), column.getName(), column.getSort() == null ? 0 : column.getSort()),
                    cards));
        }
        return new BoardView(projectId, result);
    }

    @Transactional
    public ColumnView createColumn(long projectId, long currentUserid, String name) {
        projectAccess.requireWrite(projectId, currentUserid);
        if (name == null || name.isBlank()) {
            throw new BusinessException("列名不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        TaskColumn column = new TaskColumn();
        column.setProjectId(projectId);
        column.setName(name.trim());
        column.setSort(nextSort(projectId));
        column.setCreatedAt(now);
        column.setUpdatedAt(now);
        columnMapper.insert(column);
        return new ColumnView(column.getId(), column.getName(), column.getSort());
    }

    public ColumnView renameColumn(long projectId, long currentUserid, long columnId, String name) {
        projectAccess.requireWrite(projectId, currentUserid);
        TaskColumn column = requireColumn(projectId, columnId);
        if (name == null || name.isBlank()) {
            throw new BusinessException("列名不能为空");
        }
        column.setName(name.trim());
        column.setUpdatedAt(LocalDateTime.now());
        columnMapper.updateById(column);
        return new ColumnView(column.getId(), column.getName(), column.getSort());
    }

    @Transactional
    public void deleteColumn(long projectId, long currentUserid, long columnId) {
        projectAccess.requireWrite(projectId, currentUserid);
        requireColumn(projectId, columnId);
        long remaining = columnMapper.selectCount(new LambdaQueryWrapper<TaskColumn>()
                .eq(TaskColumn::getProjectId, projectId));
        if (remaining <= 1) {
            throw new BusinessException("至少保留一列");
        }
        // 该列任务回到第一列，避免任务消失
        List<TaskColumn> others = columnMapper.selectList(new LambdaQueryWrapper<TaskColumn>()
                .eq(TaskColumn::getProjectId, projectId)
                .ne(TaskColumn::getId, columnId)
                .orderByAsc(TaskColumn::getSort));
        long fallback = others.get(0).getId();
        taskMapper.update(null, new LambdaUpdateWrapper<Task>()
                .eq(Task::getProjectId, projectId)
                .eq(Task::getColumnId, columnId)
                .set(Task::getColumnId, fallback));
        columnMapper.deleteById(columnId);
    }

    public void moveTask(long currentUserid, long taskId, Long columnId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        long projectId = task.getProjectId() == null ? 0 : task.getProjectId();
        projectAccess.requireWrite(projectId, currentUserid);
        long target = columnId == null ? 0 : columnId;
        if (target != 0) {
            requireColumn(projectId, target);
        }
        taskMapper.update(null, new LambdaUpdateWrapper<Task>()
                .eq(Task::getId, taskId)
                .set(Task::getColumnId, target)
                .set(Task::getUpdatedAt, LocalDateTime.now()));
    }

    private List<TaskColumn> ensureColumns(long projectId) {
        List<TaskColumn> columns = columnMapper.selectList(new LambdaQueryWrapper<TaskColumn>()
                .eq(TaskColumn::getProjectId, projectId)
                .orderByAsc(TaskColumn::getSort)
                .orderByAsc(TaskColumn::getId));
        if (!columns.isEmpty()) {
            return columns;
        }
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < DEFAULT_COLUMNS.size(); i++) {
            TaskColumn column = new TaskColumn();
            column.setProjectId(projectId);
            column.setName(DEFAULT_COLUMNS.get(i));
            column.setSort(i);
            column.setCreatedAt(now);
            column.setUpdatedAt(now);
            columnMapper.insert(column);
        }
        return columnMapper.selectList(new LambdaQueryWrapper<TaskColumn>()
                .eq(TaskColumn::getProjectId, projectId)
                .orderByAsc(TaskColumn::getSort)
                .orderByAsc(TaskColumn::getId));
    }

    private int nextSort(long projectId) {
        return columnMapper.selectList(new LambdaQueryWrapper<TaskColumn>()
                        .eq(TaskColumn::getProjectId, projectId))
                .stream()
                .mapToInt(column -> column.getSort() == null ? 0 : column.getSort())
                .max()
                .orElse(-1) + 1;
    }

    private TaskColumn requireColumn(long projectId, long columnId) {
        TaskColumn column = columnMapper.selectById(columnId);
        if (column == null || !Long.valueOf(projectId).equals(column.getProjectId())) {
            throw new BusinessException("看板列不存在");
        }
        return column;
    }
}
