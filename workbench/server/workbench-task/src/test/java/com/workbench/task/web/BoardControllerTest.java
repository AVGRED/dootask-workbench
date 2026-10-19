package com.workbench.task.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.workbench.common.api.BusinessException;
import com.workbench.common.api.GlobalExceptionHandler;
import com.workbench.task.service.BoardService;
import com.workbench.task.web.BoardDtos.BoardView;
import com.workbench.task.web.BoardDtos.CardView;
import com.workbench.task.web.BoardDtos.ColumnView;
import com.workbench.task.web.BoardDtos.ColumnWithCards;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class BoardControllerTest {

    private final BoardService boardService = Mockito.mock(BoardService.class);
    private final Principal principal = () -> "7";
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new BoardController(boardService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void boardReturnsColumnsWithCards() throws Exception {
        given(boardService.board(3L, 7L)).willReturn(new BoardView(3L, List.of(
                new ColumnWithCards(new ColumnView(1L, "待办", 0),
                        List.of(new CardView(5L, "写文档", "", 7L, 1, false))))));

        mockMvc.perform(get("/api/v1/projects/3/board").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.columns[0].column.name").value("待办"))
                .andExpect(jsonPath("$.data.columns[0].cards[0].name").value("写文档"));
    }

    @Test
    void createColumnReturnsColumn() throws Exception {
        given(boardService.createColumn(eq(3L), eq(7L), any())).willReturn(new ColumnView(2L, "验收", 1));

        mockMvc.perform(post("/api/v1/projects/3/columns").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"验收\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("验收"));
    }

    @Test
    void moveTaskDelegates() throws Exception {
        mockMvc.perform(put("/api/v1/tasks/5/move").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"columnId\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(boardService).moveTask(7L, 5L, 2L);
    }

    @Test
    void moveRejectedForViewer() throws Exception {
        Mockito.doThrow(new BusinessException(403, "只读成员不能修改该项目的任务"))
                .when(boardService).moveTask(7L, 5L, 2L);

        mockMvc.perform(put("/api/v1/tasks/5/move").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"columnId\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void deleteColumnDelegates() throws Exception {
        mockMvc.perform(delete("/api/v1/projects/3/columns/2").principal(principal))
                .andExpect(status().isOk());

        verify(boardService).deleteColumn(3L, 7L, 2L);
    }

    @Test
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/projects/3/board"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }
}
