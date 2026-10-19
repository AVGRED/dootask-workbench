package com.workbench.task.storage;

import java.io.IOException;
import java.io.InputStream;

/**
 * 附件存储抽象：写入返回可下载的存储名，读取返回字节流。
 *
 * <p>实现必须保证存储名不含路径分隔符，避免路径穿越。</p>
 */
public interface AttachmentStorage {

    /** 单文件上限（字节）。 */
    long MAX_SIZE_BYTES = 20L * 1024 * 1024;

    /** 允许上传的扩展名（小写，不含点）。 */
    java.util.Set<String> ALLOWED_EXTENSIONS = java.util.Set.of(
            "png", "jpg", "jpeg", "gif", "webp", "pdf", "txt", "md", "csv", "xlsx", "docx", "zip");

    String store(String originalName, InputStream content) throws IOException;

    InputStream open(String storedName) throws IOException;

    void delete(String storedName);

    boolean exists(String storedName);
}
