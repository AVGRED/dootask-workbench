package com.workbench.app.starmap;

import com.workbench.common.api.ApiResponse;
import com.workbench.common.api.BusinessException;
import java.security.Principal;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class StarMapController {

    private final StarMapService starMapService;

    public StarMapController(StarMapService starMapService) {
        this.starMapService = starMapService;
    }

    /** 管理端看全公司；员工端只看自己与合作者。 */
    @GetMapping("/starmap")
    public ApiResponse<Map<String, Object>> map(Principal principal) {
        return ApiResponse.ok(starMapService.map(currentUserid(principal)));
    }

    private long currentUserid(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new BusinessException(401, "未登录");
        }
        return Long.parseLong(principal.getName());
    }
}
