package com.workbench.app.profile;

import com.workbench.common.api.ApiResponse;
import com.workbench.common.api.BusinessException;
import com.workbench.user.service.SystemAdmin;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Principal;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 状态墙照片：与头像分开存，只有管理端状态墙使用。
 *
 * <p>管理员可以给任意员工设置，员工自己改头像不会影响状态墙。</p>
 */
@RestController
@RequestMapping("/api/v1")
public class WallPhotoController {

    private static final long MAX_BYTES = 5L * 1024 * 1024;

    private final JdbcTemplate jdbc;
    private final SystemAdmin systemAdmin;
    private final Path photoDir;

    public WallPhotoController(JdbcTemplate jdbc,
                               SystemAdmin systemAdmin,
                               @Value("${workbench.storage.root:./data/uploads}") String storageRoot) {
        this.jdbc = jdbc;
        this.systemAdmin = systemAdmin;
        this.photoDir = Paths.get(storageRoot).resolve("wall-photos");
    }

    @PostMapping("/admin/users/{userid}/wall-photo")
    public ApiResponse<Map<String, Object>> upload(Principal principal,
                                                   @PathVariable long userid,
                                                   @RequestParam("file") MultipartFile file) {
        systemAdmin.requireAdmin(currentUserid(principal));
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择图片");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException("图片不能超过 5MB");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType();
        if (!contentType.startsWith("image/")) {
            throw new BusinessException("只能上传图片");
        }
        String extension = switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
        String filename = userid + "-wall-" + System.currentTimeMillis() + extension;
        try {
            Files.createDirectories(photoDir);
            file.transferTo(photoDir.resolve(filename).toAbsolutePath());
            jdbc.update("update users set wall_photo = ?, updated_at = now() where userid = ? and deleted_at is null",
                    filename, userid);
        } catch (IOException exception) {
            throw new BusinessException("照片保存失败");
        }
        return ApiResponse.ok(Map.of("userid", userid, "wallPhoto", filename));
    }

    @DeleteMapping("/admin/users/{userid}/wall-photo")
    public ApiResponse<Void> clear(Principal principal, @PathVariable long userid) {
        systemAdmin.requireAdmin(currentUserid(principal));
        jdbc.update("update users set wall_photo = '' where userid = ?", userid);
        return ApiResponse.ok(null);
    }

    @GetMapping("/wall-photos/{filename}")
    public ResponseEntity<byte[]> file(@PathVariable String filename) {
        if (!filename.matches("[A-Za-z0-9._-]+")) {
            throw new BusinessException("文件名非法");
        }
        try {
            Path target = photoDir.resolve(filename).normalize();
            if (!target.startsWith(photoDir.normalize()) || !Files.exists(target)) {
                throw new BusinessException(404, "照片不存在");
            }
            byte[] body = Files.readAllBytes(target);
            String contentType = filename.endsWith(".png") ? "image/png"
                    : filename.endsWith(".webp") ? "image/webp" : "image/jpeg";
            return ResponseEntity.ok().contentType(MediaType.parseMediaType(contentType)).body(body);
        } catch (IOException exception) {
            throw new BusinessException(404, "照片不存在");
        }
    }

    private long currentUserid(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new BusinessException(401, "未登录");
        }
        return Long.parseLong(principal.getName());
    }
}
