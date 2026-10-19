package com.workbench.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workbench.project.entity.ActivityLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ActivityLogMapper extends BaseMapper<ActivityLog> {
}
