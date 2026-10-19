package com.workbench.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workbench.common.api.BusinessException;
import com.workbench.user.entity.Department;
import com.workbench.user.mapper.DepartmentMapper;
import com.workbench.user.web.DepartmentDtos.DepartmentView;
import com.workbench.user.web.DepartmentDtos.SaveDepartmentRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** 部门管理：列表（平铺 + 子部门数量）、创建、修改、删除。 */
@Service
public class DepartmentService {

    private final DepartmentMapper departmentMapper;

    public DepartmentService(DepartmentMapper departmentMapper) {
        this.departmentMapper = departmentMapper;
    }

    public List<DepartmentView> list() {
        List<Department> all = departmentMapper.selectList(
                new LambdaQueryWrapper<Department>().orderByAsc(Department::getParentId).orderByAsc(Department::getId));
        Map<Long, Long> children = all.stream()
                .collect(Collectors.groupingBy(Department::getParentId, Collectors.counting()));
        return all.stream()
                .map(item -> new DepartmentView(
                        item.getId(),
                        item.getName(),
                        item.getParentId() == null ? 0L : item.getParentId(),
                        item.getOwnerUserid() == null ? 0L : item.getOwnerUserid(),
                        children.getOrDefault(item.getId(), 0L)))
                .toList();
    }

    public DepartmentView create(SaveDepartmentRequest request) {
        Department department = new Department();
        department.setName(request.name());
        department.setParentId(request.parentId() == null ? 0L : request.parentId());
        department.setOwnerUserid(request.ownerUserid() == null ? 0L : request.ownerUserid());
        department.setDialogId(0L);
        department.setCreatedAt(LocalDateTime.now());
        department.setUpdatedAt(LocalDateTime.now());
        departmentMapper.insert(department);
        return new DepartmentView(department.getId(), department.getName(), department.getParentId(),
                department.getOwnerUserid(), 0L);
    }

    public DepartmentView update(long id, SaveDepartmentRequest request) {
        Department department = require(id);
        department.setName(request.name());
        if (request.parentId() != null) department.setParentId(request.parentId());
        if (request.ownerUserid() != null) department.setOwnerUserid(request.ownerUserid());
        department.setUpdatedAt(LocalDateTime.now());
        departmentMapper.updateById(department);
        return new DepartmentView(department.getId(), department.getName(), department.getParentId(),
                department.getOwnerUserid(), 0L);
    }

    public void delete(long id) {
        require(id);
        Long children = departmentMapper.selectCount(
                new LambdaQueryWrapper<Department>().eq(Department::getParentId, id));
        if (children != null && children > 0) {
            throw new BusinessException("请先删除子部门");
        }
        departmentMapper.deleteById(id);
    }

    private Department require(long id) {
        Department department = departmentMapper.selectById(id);
        if (department == null) {
            throw new BusinessException("部门不存在");
        }
        return department;
    }
}
