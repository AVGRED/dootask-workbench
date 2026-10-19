package com.workbench.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workbench.common.api.BusinessException;
import com.workbench.project.service.ProjectAccess;
import com.workbench.task.entity.Attachment;
import com.workbench.task.mapper.AttachmentMapper;
import com.workbench.task.storage.AttachmentStorage;
import com.workbench.task.web.AttachmentDtos.AttachmentView;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 附件：上传（大小/类型校验 + 落盘 + SHA-256 校验和）、列表、下载、删除（上传者或项目负责人）。
 */
@Service
public class AttachmentService {

    private final AttachmentMapper attachmentMapper;
    private final AttachmentStorage storage;
    private final JdbcTemplate jdbcTemplate;
    private final ProjectAccess projectAccess;

    public AttachmentService(AttachmentMapper attachmentMapper, AttachmentStorage storage,
                             JdbcTemplate jdbcTemplate, ProjectAccess projectAccess) {
        this.attachmentMapper = attachmentMapper;
        this.storage = storage;
        this.jdbcTemplate = jdbcTemplate;
        this.projectAccess = projectAccess;
    }

    private static final java.util.List<String> ALLOWED_PREFIXES = java.util.List.of(
            "image/", "text/plain", "text/csv", "application/pdf", "application/zip",
            "application/json", "application/msword", "application/vnd.ms-excel",
            "application/vnd.ms-powerpoint", "application/vnd.openxmlformats-officedocument.");

    private void checkFileType(String contentType, String filename) {
        String type = contentType == null ? "" : contentType.toLowerCase(java.util.Locale.ROOT);
        String name = filename == null ? "" : filename.toLowerCase(java.util.Locale.ROOT);
        if (name.endsWith(".html") || name.endsWith(".htm") || name.endsWith(".svg")
                || name.endsWith(".js") || name.endsWith(".exe") || name.endsWith(".sh")
                || name.endsWith(".bat") || name.endsWith(".jar")) {
            throw new BusinessException("不支持该文件类型");
        }
        if (ALLOWED_PREFIXES.stream().noneMatch(type::startsWith)) {
            throw new BusinessException("不支持该文件类型：" + type);
        }
    }

    public AttachmentView upload(long currentUserid, long taskId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的文件");
        }
        if (file.getSize() > AttachmentStorage.MAX_SIZE_BYTES) {
            throw new BusinessException("文件不能超过 20MB");
        }
        checkFileType(file.getContentType(), file.getOriginalFilename());
        Long projectId = jdbcTemplate.query(
                "select project_id from project_tasks where id = ? and deleted_at is null",
                resultSet -> resultSet.next() ? resultSet.getLong("project_id") : null,
                taskId);
        if (projectId == null || projectId == 0) {
            throw new BusinessException("任务不存在");
        }
        projectAccess.requireWrite(projectId, currentUserid);

        String originalName = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String storedName;
        String checksum;
        try (InputStream content = file.getInputStream()) {
            byte[] bytes = content.readAllBytes();
            checksum = sha256(bytes);
            try (InputStream again = new java.io.ByteArrayInputStream(bytes)) {
                storedName = storage.store(originalName, again);
            }
        } catch (IOException exception) {
            throw new BusinessException("文件保存失败");
        }

        Attachment attachment = new Attachment();
        attachment.setProjectId(projectId);
        attachment.setTaskId(taskId);
        attachment.setUserid(currentUserid);
        attachment.setOriginalName(originalName);
        attachment.setStoredName(storedName);
        attachment.setMimeType(file.getContentType() == null ? "application/octet-stream" : file.getContentType());
        attachment.setSizeBytes(file.getSize());
        attachment.setChecksum(checksum);
        attachment.setCreatedAt(LocalDateTime.now());
        attachmentMapper.insert(attachment);
        return toView(attachment);
    }

    public List<AttachmentView> list(long taskId, long currentUserid) {
        requireTaskAccess(taskId, currentUserid);
        return attachmentMapper.selectList(new LambdaQueryWrapper<Attachment>()
                        .eq(Attachment::getTaskId, taskId)
                        .orderByDesc(Attachment::getId))
                .stream()
                .map(AttachmentService::toView)
                .toList();
    }

    /** 下载所需：返回元数据 + 内容流，由控制器写响应。 */
    public Download open(long id, long currentUserid) {
        Attachment attachment = attachmentMapper.selectById(id);
        if (attachment == null) {
            throw new BusinessException("附件不存在");
        }
        projectAccess.requireAccess(attachment.getProjectId(), currentUserid);
        try {
            return new Download(attachment, storage.open(attachment.getStoredName()));
        } catch (IOException exception) {
            throw new BusinessException("附件文件已丢失");
        }
    }

    public void delete(long currentUserid, long id) {
        Attachment attachment = attachmentMapper.selectById(id);
        if (attachment == null) {
            throw new BusinessException("附件不存在");
        }
        boolean self = Objects.equals(attachment.getUserid(), currentUserid);
        boolean projectOwner = false;
        if (!self) {
            Long owner = jdbcTemplate.query(
                    "select userid from projects where id = ? and deleted_at is null",
                    resultSet -> resultSet.next() ? resultSet.getLong("userid") : null,
                    attachment.getProjectId());
            projectOwner = owner != null && owner == currentUserid;
        }
        if (!self && !projectOwner) {
            throw new BusinessException("无权删除该附件");
        }
        attachmentMapper.deleteById(id);
        storage.delete(attachment.getStoredName());
    }

    private void requireTaskAccess(long taskId, long currentUserid) {
        Long projectId = jdbcTemplate.query(
                "select project_id from project_tasks where id = ? and deleted_at is null",
                rs -> rs.next() ? rs.getLong("project_id") : null,
                taskId);
        if (projectId == null || projectId == 0) {
            throw new BusinessException("任务不存在");
        }
        projectAccess.requireAccess(projectId, currentUserid);
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            return "";
        }
    }

    private static AttachmentView toView(Attachment attachment) {
        return new AttachmentView(
                attachment.getId(),
                attachment.getTaskId(),
                attachment.getUserid(),
                attachment.getOriginalName(),
                attachment.getMimeType(),
                attachment.getSizeBytes() == null ? 0 : attachment.getSizeBytes(),
                attachment.getCreatedAt());
    }

    /** 下载载荷。 */
    public record Download(Attachment attachment, InputStream content) {
    }
}
