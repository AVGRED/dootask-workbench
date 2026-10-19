package com.workbench.user.web;

import com.workbench.common.api.ApiResponse;
import com.workbench.common.api.BusinessException;
import com.workbench.user.service.DictService;
import com.workbench.user.service.SettingsService;
import com.workbench.user.web.SystemDtos.DictItemView;
import com.workbench.user.web.SystemDtos.PublicSettingsView;
import com.workbench.user.web.SystemDtos.SaveDictRequest;
import com.workbench.user.web.SystemDtos.SaveSettingRequest;
import com.workbench.user.web.SystemDtos.SettingView;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class SystemController {

    private final SettingsService settingsService;
    private final DictService dictService;

    public SystemController(SettingsService settingsService, DictService dictService) {
        this.settingsService = settingsService;
        this.dictService = dictService;
    }

    @GetMapping("/admin/settings")
    public ApiResponse<List<SettingView>> settings(Principal principal) {
        return ApiResponse.ok(settingsService.list(currentUserid(principal)));
    }

    @PutMapping("/admin/settings/{key}")
    public ApiResponse<SettingView> saveSetting(
            Principal principal,
            @PathVariable String key,
            @Valid @RequestBody SaveSettingRequest request) {
        return ApiResponse.ok(settingsService.upsert(currentUserid(principal), key, request.value(),
                request.description()));
    }

    /** 匿名可读：站点名称、公告、主题、工作日。 */
    @GetMapping("/system/public/settings")
    public ApiResponse<PublicSettingsView> publicSettings() {
        return ApiResponse.ok(settingsService.publicSettings());
    }

    @GetMapping("/dicts/{type}")
    public ApiResponse<List<DictItemView>> dict(@PathVariable String type) {
        return ApiResponse.ok(dictService.enabled(type));
    }

    @GetMapping("/admin/dicts")
    public ApiResponse<List<DictItemView>> allDicts(
            Principal principal,
            @RequestParam(required = false) String type) {
        return ApiResponse.ok(dictService.all(currentUserid(principal), type));
    }

    @PostMapping("/admin/dicts")
    public ApiResponse<DictItemView> createDict(
            Principal principal,
            @Valid @RequestBody SaveDictRequest request) {
        return ApiResponse.ok(dictService.create(currentUserid(principal), request));
    }

    @PutMapping("/admin/dicts/{id}")
    public ApiResponse<DictItemView> updateDict(
            Principal principal,
            @PathVariable long id,
            @Valid @RequestBody SaveDictRequest request) {
        return ApiResponse.ok(dictService.update(currentUserid(principal), id, request));
    }

    @DeleteMapping("/admin/dicts/{id}")
    public ApiResponse<Void> deleteDict(Principal principal, @PathVariable long id) {
        dictService.delete(currentUserid(principal), id);
        return ApiResponse.ok(null);
    }

    private long currentUserid(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new BusinessException(401, "未登录");
        }
        return Long.parseLong(principal.getName());
    }
}
