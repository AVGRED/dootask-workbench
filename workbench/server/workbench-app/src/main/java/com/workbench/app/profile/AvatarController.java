package com.workbench.app.profile;

import com.workbench.common.api.ApiResponse;
import com.workbench.common.api.BusinessException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 员工头像：上传静态 3D 风格图片，按用户存取。 */
@RestController
@RequestMapping("/api/v1")
public class AvatarController {

    private static final long MAX_BYTES = 2L * 1024 * 1024;

    private final JdbcTemplate jdbc;
    private final Path avatarDir;

    public AvatarController(JdbcTemplate jdbc,
                            @Value("${workbench.storage.root:./data/uploads}") String storageRoot) {
        this.jdbc = jdbc;
        this.avatarDir = Paths.get(storageRoot).resolve("avatars");
    }

    @PostMapping("/me/avatar")
    public ApiResponse<Map<String, Object>> upload(Principal principal,
                                                   @RequestParam("file") MultipartFile file) {
        long userid = currentUserid(principal);
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择图片");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException("图片不能超过 2MB");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType();
        if (!contentType.startsWith("image/")) {
            throw new BusinessException("只能上传图片");
        }
        String extension = switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> ".jpg";
        };
        String filename = userid + "-" + System.currentTimeMillis() + extension;
        try {
            Files.createDirectories(avatarDir);
            Path target = avatarDir.resolve(filename);
            file.transferTo(target.toAbsolutePath());
            jdbc.update("update users set avatar = ? where userid = ?", filename, userid);
        } catch (IOException exception) {
            throw new BusinessException("头像保存失败");
        }
        return ApiResponse.ok(Map.of("avatar", filename));
    }

    @GetMapping("/me/avatar")
    public ApiResponse<Map<String, Object>> mine(Principal principal) {
        long userid = currentUserid(principal);
        List<String> rows = jdbc.queryForList("select avatar from users where userid = ?", String.class, userid);
        return ApiResponse.ok(Map.of("avatar", rows.isEmpty() ? "" : rows.get(0)));
    }

    @GetMapping("/avatars/{filename}")
    public ResponseEntity<byte[]> file(@PathVariable String filename) {
        if (!filename.matches("[A-Za-z0-9._-]+")) {
            throw new BusinessException("文件名非法");
        }
        try {
            Path target = avatarDir.resolve(filename).normalize();
            if (!target.startsWith(avatarDir.normalize()) || !Files.exists(target)) {
                throw new BusinessException(404, "头像不存在");
            }
            byte[] body = Files.readAllBytes(target);
            String contentType = filename.endsWith(".png") ? "image/png"
                    : filename.endsWith(".webp") ? "image/webp"
                    : filename.endsWith(".gif") ? "image/gif" : "image/jpeg";
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(body);
        } catch (IOException exception) {
            throw new BusinessException(404, "头像不存在");
        }
    }

    private long currentUserid(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new BusinessException(401, "未登录");
        }
        return Long.parseLong(principal.getName());
    }
}
