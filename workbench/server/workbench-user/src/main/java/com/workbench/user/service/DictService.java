package com.workbench.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workbench.common.api.BusinessException;
import com.workbench.user.entity.DictItem;
import com.workbench.user.mapper.DictItemMapper;
import com.workbench.user.web.SystemDtos.DictItemView;
import com.workbench.user.web.SystemDtos.SaveDictRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** 数据字典：按类型读启用项，管理员增删改。 */
@Service
public class DictService {

    private final DictItemMapper dictItemMapper;
    private final SystemAdmin systemAdmin;

    public DictService(DictItemMapper dictItemMapper, SystemAdmin systemAdmin) {
        this.dictItemMapper = dictItemMapper;
        this.systemAdmin = systemAdmin;
    }

    public List<DictItemView> enabled(String type) {
        return dictItemMapper.selectList(new LambdaQueryWrapper<DictItem>()
                        .eq(DictItem::getDictType, type)
                        .eq(DictItem::getEnabled, 1)
                        .orderByAsc(DictItem::getSort)
                        .orderByAsc(DictItem::getId))
                .stream()
                .map(DictService::toView)
                .toList();
    }

    public List<DictItemView> all(long currentUserid, String type) {
        systemAdmin.requireAdmin(currentUserid);
        LambdaQueryWrapper<DictItem> wrapper = new LambdaQueryWrapper<DictItem>()
                .orderByAsc(DictItem::getDictType)
                .orderByAsc(DictItem::getSort)
                .orderByAsc(DictItem::getId);
        if (type != null && !type.isBlank()) {
            wrapper.eq(DictItem::getDictType, type);
        }
        return dictItemMapper.selectList(wrapper).stream().map(DictService::toView).toList();
    }

    public DictItemView create(long currentUserid, SaveDictRequest request) {
        systemAdmin.requireAdmin(currentUserid);
        DictItem existing = dictItemMapper.selectOne(new LambdaQueryWrapper<DictItem>()
                .eq(DictItem::getDictType, request.type())
                .eq(DictItem::getCode, request.code())
                .last("limit 1"));
        if (existing != null) {
            throw new BusinessException("该类型下编码已存在");
        }
        LocalDateTime now = LocalDateTime.now();
        DictItem item = new DictItem();
        item.setDictType(request.type());
        item.setCode(request.code());
        item.setLabel(request.label());
        item.setSort(request.sort() == null ? 0 : request.sort());
        item.setEnabled(request.enabled() == null || request.enabled() ? 1 : 0);
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        dictItemMapper.insert(item);
        return toView(item);
    }

    public DictItemView update(long currentUserid, long id, SaveDictRequest request) {
        systemAdmin.requireAdmin(currentUserid);
        DictItem item = dictItemMapper.selectById(id);
        if (item == null) {
            throw new BusinessException("字典项不存在");
        }
        if (!Objects.equals(item.getDictType(), request.type()) || !Objects.equals(item.getCode(), request.code())) {
            Long duplicated = dictItemMapper.selectCount(new LambdaQueryWrapper<DictItem>()
                    .eq(DictItem::getDictType, request.type())
                    .eq(DictItem::getCode, request.code()));
            if (duplicated != null && duplicated > 0) {
                throw new BusinessException("该类型下编码已存在");
            }
        }
        item.setDictType(request.type());
        item.setCode(request.code());
        item.setLabel(request.label());
        if (request.sort() != null) item.setSort(request.sort());
        if (request.enabled() != null) item.setEnabled(request.enabled() ? 1 : 0);
        item.setUpdatedAt(LocalDateTime.now());
        dictItemMapper.updateById(item);
        return toView(item);
    }

    public void delete(long currentUserid, long id) {
        systemAdmin.requireAdmin(currentUserid);
        if (dictItemMapper.selectById(id) == null) {
            throw new BusinessException("字典项不存在");
        }
        dictItemMapper.deleteById(id);
    }

    private static DictItemView toView(DictItem item) {
        return new DictItemView(item.getId(), item.getDictType(), item.getCode(), item.getLabel(),
                item.getSort() == null ? 0 : item.getSort(),
                item.getEnabled() == null || item.getEnabled() == 1);
    }
}
