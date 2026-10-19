package com.workbench.planning.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workbench.planning.entity.TaskDependency;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TaskDependencyMapper extends BaseMapper<TaskDependency> {
}
