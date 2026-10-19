package com.workbench.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workbench.common.api.BusinessException;
import com.workbench.project.service.ProjectAccess;
import com.workbench.task.entity.Tag;
import com.workbench.task.entity.Task;
import com.workbench.task.entity.TaskTag;
import com.workbench.task.mapper.TagMapper;
import com.workbench.task.mapper.TaskMapper;
import com.workbench.task.mapper.TaskTagMapper;
import com.workbench.task.web.TagDtos.SaveTagRequest;
import com.workbench.task.web.TagDtos.TagView;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

/** 项目标签：建/删、打标签/取消、按标签查任务。 */
@Service
public class TagService {

    private final TagMapper tagMapper;
    private final TaskTagMapper taskTagMapper;
    private final TaskMapper taskMapper;
    private final ProjectAccess projectAccess;

    public TagService(TagMapper tagMapper, TaskTagMapper taskTagMapper, TaskMapper taskMapper,
                      ProjectAccess projectAccess) {
        this.tagMapper = tagMapper;
        this.taskTagMapper = taskTagMapper;
        this.taskMapper = taskMapper;
        this.projectAccess = projectAccess;
    }

    public List<TagView> list(long projectId, long currentUserid) {
        projectAccess.requireAccess(projectId, currentUserid);
        return tagsOf(projectId);
    }

    public TagView create(long projectId, long currentUserid, SaveTagRequest request) {
        projectAccess.requireWrite(projectId, currentUserid);
        Tag existing = tagMapper.selectOne(new LambdaQueryWrapper<Tag>()
                .eq(Tag::getProjectId, projectId)
                .eq(Tag::getName, request.name())
                .last("limit 1"));
        if (existing != null) {
            throw new BusinessException("标签已存在");
        }
        Tag tag = new Tag();
        tag.setProjectId(projectId);
        tag.setName(request.name().trim());
        tag.setColor(request.color() == null ? "" : request.color());
        tag.setCreatedAt(LocalDateTime.now());
        tagMapper.insert(tag);
        return new TagView(tag.getId(), tag.getName(), tag.getColor());
    }

    public void delete(long projectId, long currentUserid, long tagId) {
        projectAccess.requireWrite(projectId, currentUserid);
        Tag tag = tagMapper.selectById(tagId);
        if (tag == null || !Long.valueOf(projectId).equals(tag.getProjectId())) {
            throw new BusinessException("标签不存在");
        }
        taskTagMapper.delete(new LambdaQueryWrapper<TaskTag>().eq(TaskTag::getTagId, tagId));
        tagMapper.deleteById(tagId);
    }

    public void attach(long taskId, long currentUserid, long tagId) {
        Task task = requireTask(taskId);
        long projectId = task.getProjectId() == null ? 0 : task.getProjectId();
        projectAccess.requireWrite(projectId, currentUserid);
        Tag tag = tagMapper.selectById(tagId);
        if (tag == null || !Long.valueOf(projectId).equals(tag.getProjectId())) {
            throw new BusinessException("标签不存在");
        }
        Long exists = taskTagMapper.selectCount(new LambdaQueryWrapper<TaskTag>()
                .eq(TaskTag::getTaskId, taskId)
                .eq(TaskTag::getTagId, tagId));
        if (exists != null && exists > 0) {
            return;
        }
        TaskTag link = new TaskTag();
        link.setTaskId(taskId);
        link.setTagId(tagId);
        taskTagMapper.insert(link);
    }

    public void detach(long taskId, long currentUserid, long tagId) {
        Task task = requireTask(taskId);
        long projectId = task.getProjectId() == null ? 0 : task.getProjectId();
        projectAccess.requireWrite(projectId, currentUserid);
        taskTagMapper.delete(new LambdaQueryWrapper<TaskTag>()
                .eq(TaskTag::getTaskId, taskId)
                .eq(TaskTag::getTagId, tagId));
    }

    public List<TagView> tagsOfTask(long taskId, long currentUserid) {
        Task task = requireTask(taskId);
        long projectId = task.getProjectId() == null ? 0 : task.getProjectId();
        projectAccess.requireAccess(projectId, currentUserid);
        List<Long> tagIds = taskTagMapper.selectList(new LambdaQueryWrapper<TaskTag>()
                        .eq(TaskTag::getTaskId, taskId))
                .stream()
                .map(TaskTag::getTagId)
                .toList();
        if (tagIds.isEmpty()) {
            return List.of();
        }
        return tagMapper.selectBatchIds(tagIds).stream()
                .map(tag -> new TagView(tag.getId(), tag.getName(), tag.getColor()))
                .toList();
    }

    /** 按标签筛选任务 id（给任务列表用）。 */
    public List<Long> taskIdsByTag(long projectId, long tagId) {
        List<Long> taskIds = taskTagMapper.selectList(new LambdaQueryWrapper<TaskTag>()
                        .eq(TaskTag::getTagId, tagId))
                .stream()
                .map(TaskTag::getTaskId)
                .toList();
        if (taskIds.isEmpty()) {
            return List.of();
        }
        return taskMapper.selectList(new LambdaQueryWrapper<Task>()
                        .in(Task::getId, taskIds)
                        .eq(Task::getProjectId, projectId))
                .stream()
                .map(Task::getId)
                .toList();
    }

    private List<TagView> tagsOf(long projectId) {
        return tagMapper.selectList(new LambdaQueryWrapper<Tag>()
                        .eq(Tag::getProjectId, projectId)
                        .orderByAsc(Tag::getId))
                .stream()
                .map(tag -> new TagView(tag.getId(), tag.getName(), tag.getColor()))
                .toList();
    }

    private Task requireTask(long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        return task;
    }
}
