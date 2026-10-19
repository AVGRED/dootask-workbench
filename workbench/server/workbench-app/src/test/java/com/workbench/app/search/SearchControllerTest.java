package com.workbench.app.search;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.workbench.common.api.GlobalExceptionHandler;
import com.workbench.app.search.SearchResult.Item;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class SearchControllerTest {

    private final SearchService searchService = Mockito.mock(SearchService.class);
    private final Principal principal = () -> "7";
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new SearchController(searchService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void returnsGroupedResults() throws Exception {
        given(searchService.search(eq(7L), eq("工作台"))).willReturn(new SearchResult(
                List.of(new Item(1L, "工作台项目", "项目", "project")),
                List.of(new Item(5L, "工作台任务", "项目 #1", "task")),
                List.of(new Item(7L, "张三", "a@b.com", "user"))));

        mockMvc.perform(get("/api/v1/search").principal(principal).param("keyword", "工作台"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projects[0].title").value("工作台项目"))
                .andExpect(jsonPath("$.data.tasks[0].type").value("task"))
                .andExpect(jsonPath("$.data.users[0].subtitle").value("a@b.com"));
    }

    @Test
    void blankKeywordReturnsEmpty() throws Exception {
        given(searchService.search(eq(7L), eq(""))).willReturn(SearchResult.empty());

        mockMvc.perform(get("/api/v1/search").principal(principal).param("keyword", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projects.length()").value(0));
    }

    @Test
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/search").param("keyword", "x"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }
}
