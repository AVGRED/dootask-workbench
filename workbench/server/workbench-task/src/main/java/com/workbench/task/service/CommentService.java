package com.workbench.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workbench.common.api.BusinessException;
import com.workbench.project.service.ProjectAccess;
import com.workbench.task.entity.Notification;
import com.workbench.task.entity.TaskComment;
import com.workbench.task.mapper.NotificationMapper;
import com.workbench.task.mapper.TaskCommentMapper;
import com.workbench.task.web.CommentDtos.CommentView;
import com.workbench.task.web.CommentDtos.CreateCommentRequest;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 任务评论：发表（校验任务、@ 用户存在并生成通知）、列表、删除（作者或项目负责人）。 */
@Service
public class CommentService {

    private final TaskCommentMapper commentMapper;
    private final NotificationMapper notificationMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ProjectAccess projectAccess;

    public CommentService(TaskCommentMapper commentMapper, NotificationMapper notificationMapper,
                          JdbcTemplate jdbcTemplate, ProjectAccess projectAccess) {
        this.commentMapper = commentMapper;
        this.notificationMapper = notificationMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.projectAccess = projectAccess;
    }

    @Transactional
    public CommentView create(long currentUserid, long taskId, CreateCommentRequest request) {
        Long projectId = jdbcTemplate.query(
                "select project_id from project_tasks where id = ? and deleted_at is null",
                resultSet -> resultSet.next() ? resultSet.getLong("project_id") : null,
                taskId);
        if (projectId == null || projectId == 0) {
            throw new BusinessException("任务不存在");
        }
        projectAccess.requireWrite(projectId, currentUserid);

        List<Long> mentions = request.mentionUserids() == null ? List.of() : request.mentionUserids().stream()
                .filter(Objects::nonNull)
                .distinct()
                .filter(id -> id > 0)
                .toList();
        for (Long mention : mentions) {
            Long count = jdbcTemplate.queryForObject(
                    "select count(*) from users where userid = ? and (deleted_at is null)", Long.class, mention);
            if (count == null || count == 0) {
                throw new BusinessException("被 @ 的用户不存在");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        TaskComment comment = new TaskComment();
        comment.setTaskId(taskId);
        comment.setProjectId(projectId);
        comment.setUserid(currentUserid);
        comment.setContent(request.content());
        comment.setMentionedUserids(mentions.stream().map(String::valueOf).collect(Collectors.joining(",")));
        comment.setCreatedAt(now);
        comment.setUpdatedAt(now);
        commentMapper.insert(comment);

        String mentionText = mentions.isEmpty() ? "" : "，提到了 " + mentions.size() + " 人";
        for (Long mention : mentions) {
            Notification notification = new Notification();
            notification.setUserid(mention);
            notification.setType("mention");
            notification.setTitle("任务 #" + taskId + " 有新评论" + mentionText);
            notification.setPayload("{\"taskId\":" + taskId + ",\"commentId\":" + comment.getId() + "}");
            notification.setCreatedAt(now);
            notificationMapper.insert(notification);
        }
        return toView(comment);
    }

    public List<CommentView> list(long taskId, long currentUserid) {
        Long projectId = jdbcTemplate.query(
                "select project_id from project_tasks where id = ? and deleted_at is null",
                rs -> rs.next() ? rs.getLong("project_id") : null,
                taskId);
        if (projectId == null || projectId == 0) {
            throw new BusinessException("任务不存在");
        }
        projectAccess.requireAccess(projectId, currentUserid);
        return commentMapper.selectList(new LambdaQueryWrapper<TaskComment>()
                        .eq(TaskComment::getTaskId, taskId)
                        .orderByAsc(TaskComment::getId))
                .stream()
                .map(CommentService::toView)
                .toList();
    }

    public void delete(long currentUserid, long id) {
        TaskComment comment = commentMapper.selectById(id);
        if (comment == null) {
            throw new BusinessException("评论不存在");
        }
        boolean self = Objects.equals(comment.getUserid(), currentUserid);
        boolean projectOwner = false;
        if (!self) {
            Long owner = jdbcTemplate.query(
                    "select userid from projects where id = ? and deleted_at is null",
                    resultSet -> resultSet.next() ? resultSet.getLong("userid") : null,
                    comment.getProjectId());
            projectOwner = owner != null && owner == currentUserid;
        }
        if (!self && !projectOwner) {
            throw new BusinessException("无权删除该评论");
        }
        commentMapper.deleteById(id);
    }

    static List<Long> parseMentions(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(Long::valueOf)
                .toList();
    }

    private static CommentView toView(TaskComment comment) {
        return new CommentView(
                comment.getId(),
                comment.getTaskId(),
                comment.getUserid(),
                comment.getContent(),
                parseMentions(comment.getMentionedUserids()),
                comment.getCreatedAt());
    }
}
