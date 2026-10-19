package com.workbench.worklog.web;

import com.workbench.common.api.BusinessException;
import com.workbench.project.service.ProjectAccess;
import com.workbench.worklog.service.WorklogService;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 工时导出（CSV，带 BOM 便于 Excel 直接打开）。 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/worklogs")
public class WorklogExportController {

    private final WorklogService worklogService;
    private final ProjectAccess projectAccess;

    public WorklogExportController(WorklogService worklogService, ProjectAccess projectAccess) {
        this.worklogService = worklogService;
        this.projectAccess = projectAccess;
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(Principal principal, @PathVariable long projectId) {
        long userid = currentUserid(principal);
        projectAccess.requireAccess(projectId, userid);
        String csv = worklogService.exportCsv(projectId, userid);
        byte[] body = ("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("worklogs.csv").build().toString())
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(body);
    }

    private long currentUserid(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new BusinessException(401, "未登录");
        }
        return Long.parseLong(principal.getName());
    }
}
