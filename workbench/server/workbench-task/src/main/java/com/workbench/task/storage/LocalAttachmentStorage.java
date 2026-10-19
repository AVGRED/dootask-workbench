package com.workbench.task.storage;

import com.workbench.common.api.BusinessException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 本地磁盘存储：根目录可配置，文件名使用 UUID + 原扩展名。 */
@Component
public class LocalAttachmentStorage implements AttachmentStorage {

    private final Path root;

    public LocalAttachmentStorage(@Value("${workbench.storage.root:./data/uploads}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    public Path root() {
        return root;
    }

    @Override
    public String store(String originalName, InputStream content) throws IOException {
        String extension = extensionOf(originalName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException("不支持的文件类型：" + extension);
        }
        Files.createDirectories(root);
        String storedName = UUID.randomUUID() + (extension.isEmpty() ? "" : "." + extension);
        Path target = resolveSafe(storedName);
        Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        return storedName;
    }

    @Override
    public InputStream open(String storedName) throws IOException {
        return Files.newInputStream(resolveSafe(storedName));
    }

    @Override
    public void delete(String storedName) {
        try {
            Files.deleteIfExists(resolveSafe(storedName));
        } catch (IOException ignored) {
            // 文件已不存在时忽略，数据库记录仍会删除。
        }
    }

    @Override
    public boolean exists(String storedName) {
        return Files.exists(resolveSafe(storedName));
    }

    /** 解析存储名并确保结果仍在根目录内（防路径穿越）。 */
    private Path resolveSafe(String storedName) {
        if (storedName == null || storedName.isBlank()
                || storedName.contains("/") || storedName.contains("\\") || storedName.contains("..")) {
            throw new BusinessException("非法的存储文件名");
        }
        Path resolved = root.resolve(storedName).normalize();
        if (!resolved.startsWith(root)) {
            throw new BusinessException("非法的存储文件名");
        }
        return resolved;
    }

    /** 取小写扩展名；无扩展名返回空串。 */
    static String extensionOf(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "";
        }
        String name = Path.of(originalName).getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
