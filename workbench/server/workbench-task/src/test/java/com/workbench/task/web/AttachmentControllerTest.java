package com.workbench.task.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.workbench.common.api.BusinessException;
import com.workbench.common.api.GlobalExceptionHandler;
import com.workbench.task.entity.Attachment;
import com.workbench.task.service.AttachmentService;
import com.workbench.task.web.AttachmentDtos.AttachmentView;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class AttachmentControllerTest {

    private final AttachmentService attachmentService = Mockito.mock(AttachmentService.class);
    private final Principal principal = () -> "7";
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AttachmentController(attachmentService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void uploadReturnsAttachment() throws Exception {
        given(attachmentService.upload(eq(7L), eq(5L), any())).willReturn(new AttachmentView(
                1L, 5L, 7L, "说明.txt", "text/plain", 5, LocalDateTime.now()));

        MockMultipartFile file = new MockMultipartFile("file", "说明.txt", "text/plain",
                "hello".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/tasks/5/attachments").file(file).principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.originalName").value("说明.txt"))
                .andExpect(jsonPath("$.data.sizeBytes").value(5));
    }

    @Test
    void uploadRejectsOversize() throws Exception {
        given(attachmentService.upload(eq(7L), eq(5L), any()))
                .willThrow(new BusinessException("文件不能超过 20MB"));

        MockMultipartFile file = new MockMultipartFile("file", "big.zip", "application/zip", new byte[]{1});

        mockMvc.perform(multipart("/api/v1/tasks/5/attachments").file(file).principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("文件不能超过 20MB"));
    }

    @Test
    void listReturnsAttachments() throws Exception {
        given(attachmentService.list(5L)).willReturn(List.of(
                new AttachmentView(1L, 5L, 7L, "a.png", "image/png", 10, LocalDateTime.now())));

        mockMvc.perform(get("/api/v1/tasks/5/attachments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].originalName").value("a.png"));
    }

    @Test
    void downloadSetsDispositionAndContentType() throws Exception {
        Attachment attachment = new Attachment();
        attachment.setId(1L);
        attachment.setOriginalName("说明.txt");
        attachment.setMimeType("text/plain");
        attachment.setSizeBytes(5L);
        given(attachmentService.open(1L)).willReturn(new AttachmentService.Download(
                attachment, new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8))));

        mockMvc.perform(get("/api/v1/attachments/1/download"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("filename*=UTF-8''")))
                .andExpect(header().string("Content-Type", "text/plain"));
    }

    @Test
    void deleteDelegates() throws Exception {
        mockMvc.perform(delete("/api/v1/attachments/3").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(attachmentService).delete(anyLong(), anyLong());
    }

    @Test
    void deleteRejectsOthers() throws Exception {
        Mockito.doThrow(new BusinessException("无权删除该附件")).when(attachmentService).delete(7L, 3L);

        mockMvc.perform(delete("/api/v1/attachments/3").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }
}
