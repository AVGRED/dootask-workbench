package com.workbench.task.web;

import com.workbench.common.api.ApiResponse;
import com.workbench.common.api.BusinessException;
import com.workbench.task.entity.Attachment;
import com.workbench.task.service.AttachmentService;
import com.workbench.task.web.AttachmentDtos.AttachmentView;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping("/tasks/{taskId}/attachments")
    public ApiResponse<AttachmentView> upload(
            Principal principal,
            @PathVariable long taskId,
            @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(attachmentService.upload(currentUserid(principal), taskId, file));
    }

    @GetMapping("/tasks/{taskId}/attachments")
    public ApiResponse<List<AttachmentView>> list(Principal principal, @PathVariable long taskId) {
        return ApiResponse.ok(attachmentService.list(taskId, currentUserid(principal)));
    }

    @GetMapping("/attachments/{id}/download")
    public ResponseEntity<InputStreamResource> download(Principal principal, @PathVariable long id)
            throws IOException {
        AttachmentService.Download download = attachmentService.open(id, currentUserid(principal));
        Attachment attachment = download.attachment();
        String encoded = URLEncoder.encode(attachment.getOriginalName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(attachment.getMimeType()))
                .contentLength(attachment.getSizeBytes() == null ? 0 : attachment.getSizeBytes())
                .body(new InputStreamResource(download.content()));
    }

    @DeleteMapping("/attachments/{id}")
    public ApiResponse<Void> delete(Principal principal, @PathVariable long id) {
        attachmentService.delete(currentUserid(principal), id);
        return ApiResponse.ok(null);
    }

    private long currentUserid(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new BusinessException(401, "未登录");
        }
        return Long.parseLong(principal.getName());
    }
}
