package com.workbench.app.dashboard;

import com.workbench.app.dashboard.DashboardDtos.DashboardView;
import com.workbench.app.dashboard.DashboardDtos.LayoutView;
import com.workbench.app.dashboard.DashboardDtos.SaveLayoutRequest;
import com.workbench.app.dashboard.DashboardDtos.WidgetDef;
import com.workbench.common.api.ApiResponse;
import com.workbench.common.api.BusinessException;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/catalog")
    public ApiResponse<List<WidgetDef>> catalog() {
        return ApiResponse.ok(dashboardService.catalog());
    }

    @GetMapping("/me")
    public ApiResponse<DashboardView> me(Principal principal) {
        return ApiResponse.ok(dashboardService.me(currentUserid(principal)));
    }

    @PutMapping("/me/layout")
    public ApiResponse<LayoutView> saveLayout(Principal principal,
                                              @Valid @RequestBody SaveLayoutRequest request) {
        return ApiResponse.ok(dashboardService.saveLayout(currentUserid(principal), request.items()));
    }

    @PostMapping("/me/layout/reset")
    public ApiResponse<LayoutView> reset(Principal principal) {
        return ApiResponse.ok(dashboardService.resetLayout(currentUserid(principal)));
    }

    private long currentUserid(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new BusinessException(401, "未登录");
        }
        return Long.parseLong(principal.getName());
    }
}
