package com.workbench.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workbench.common.api.BusinessException;
import com.workbench.user.entity.SystemSetting;
import com.workbench.user.mapper.SystemSettingMapper;
import com.workbench.user.web.SystemDtos.PublicSettingsView;
import com.workbench.user.web.SystemDtos.SettingView;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/** 系统设置：管理员读写，公开白名单可匿名读。 */
@Service
public class SettingsService {

    /** 允许匿名读取的键（前端启动时要用的站点信息）。 */
    public static final Set<String> PUBLIC_KEYS = Set.of("app.name", "app.announcement", "ui.theme", "task.workdays");

    private final SystemSettingMapper settingMapper;
    private final SystemAdmin systemAdmin;

    public SettingsService(SystemSettingMapper settingMapper, SystemAdmin systemAdmin) {
        this.settingMapper = settingMapper;
        this.systemAdmin = systemAdmin;
    }

    public List<SettingView> list(long currentUserid) {
        systemAdmin.requireAdmin(currentUserid);
        return settingMapper.selectList(new LambdaQueryWrapper<SystemSetting>().orderByAsc(SystemSetting::getSettingKey))
                .stream()
                .map(SettingsService::toView)
                .toList();
    }

    public SettingView upsert(long currentUserid, String key, String value, String description) {
        systemAdmin.requireAdmin(currentUserid);
        if (key == null || key.isBlank()) {
            throw new BusinessException("设置键不能为空");
        }
        SystemSetting setting = settingMapper.selectById(key);
        LocalDateTime now = LocalDateTime.now();
        if (setting == null) {
            setting = new SystemSetting();
            setting.setSettingKey(key);
            setting.setSettingValue(value);
            setting.setDescription(description == null ? "" : description);
            setting.setUpdatedBy(currentUserid);
            setting.setUpdatedAt(now);
            settingMapper.insert(setting);
        } else {
            setting.setSettingValue(value);
            if (description != null) setting.setDescription(description);
            setting.setUpdatedBy(currentUserid);
            setting.setUpdatedAt(now);
            settingMapper.updateById(setting);
        }
        return toView(setting);
    }

    /** 匿名可读：只返回白名单键。 */
    public PublicSettingsView publicSettings() {
        List<SettingView> items = settingMapper.selectList(new LambdaQueryWrapper<SystemSetting>()
                        .in(SystemSetting::getSettingKey, PUBLIC_KEYS)
                        .orderByAsc(SystemSetting::getSettingKey))
                .stream()
                .map(SettingsService::toView)
                .toList();
        return new PublicSettingsView(items);
    }

    private static SettingView toView(SystemSetting setting) {
        return new SettingView(setting.getSettingKey(), setting.getSettingValue(),
                setting.getDescription(), setting.getUpdatedAt());
    }
}
