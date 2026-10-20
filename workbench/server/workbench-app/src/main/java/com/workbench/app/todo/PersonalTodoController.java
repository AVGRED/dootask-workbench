package com.workbench.app.todo;

import com.workbench.common.api.ApiResponse;
import com.workbench.common.api.BusinessException;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/todos")
public class PersonalTodoController {

    private final PersonalTodoService service;

    public PersonalTodoController(PersonalTodoService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(Principal principal) {
        return ApiResponse.ok(service.list(currentUserid(principal)));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(Principal principal, @RequestBody SaveTodoRequest request) {
        return ApiResponse.ok(service.create(currentUserid(principal), request.title(), request.todoDate()));
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(Principal principal,
                                                   @PathVariable long id,
                                                   @RequestBody SaveTodoRequest request) {
        return ApiResponse.ok(service.update(currentUserid(principal), id, request.title(), request.done()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(Principal principal, @PathVariable long id) {
        service.delete(currentUserid(principal), id);
        return ApiResponse.ok(null);
    }

    public record SaveTodoRequest(String title, Boolean done, LocalDate todoDate) {
    }

    private long currentUserid(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new BusinessException(401, "未登录");
        }
        return Long.parseLong(principal.getName());
    }
}
