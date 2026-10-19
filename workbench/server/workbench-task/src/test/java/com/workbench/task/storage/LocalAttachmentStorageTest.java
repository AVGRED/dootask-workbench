package com.workbench.task.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.workbench.common.api.BusinessException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalAttachmentStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void storesAndReadsBack() throws IOException {
        LocalAttachmentStorage storage = new LocalAttachmentStorage(tempDir.toString());
        String stored = storage.store("说明.txt", new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));

        assertTrue(stored.endsWith(".txt"));
        assertFalse(stored.contains("/"));
        assertFalse(stored.contains("\\"));
        try (InputStream input = storage.open(stored)) {
            assertEquals("hello", new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        assertTrue(storage.exists(stored));
    }

    @Test
    void rejectsUnsupportedExtension() {
        LocalAttachmentStorage storage = new LocalAttachmentStorage(tempDir.toString());
        assertThrows(BusinessException.class, () ->
                storage.store("evil.exe", new ByteArrayInputStream(new byte[]{1})));
    }

    @Test
    void rejectsPathTraversalInStoredName() {
        LocalAttachmentStorage storage = new LocalAttachmentStorage(tempDir.toString());
        assertThrows(BusinessException.class, () -> storage.open("../etc/passwd"));
        assertThrows(BusinessException.class, () -> storage.open("a/b.txt"));
    }

    @Test
    void deleteIsIdempotent() throws IOException {
        LocalAttachmentStorage storage = new LocalAttachmentStorage(tempDir.toString());
        String stored = storage.store("a.md", new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8)));
        assertTrue(Files.exists(tempDir.resolve(stored)));
        storage.delete(stored);
        assertFalse(storage.exists(stored));
        storage.delete(stored);
    }

    @Test
    void extensionParsingHandlesMissingAndUpper() {
        assertEquals("txt", LocalAttachmentStorage.extensionOf("a.TXT"));
        assertEquals("", LocalAttachmentStorage.extensionOf("noext"));
        assertEquals("", LocalAttachmentStorage.extensionOf("trailing."));
        assertEquals("png", LocalAttachmentStorage.extensionOf("dir/子目录/a.png"));
    }
}
