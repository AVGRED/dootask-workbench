package com.workbench.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workbench.user.entity.Department;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DepartmentMapper extends BaseMapper<Department> {
}
