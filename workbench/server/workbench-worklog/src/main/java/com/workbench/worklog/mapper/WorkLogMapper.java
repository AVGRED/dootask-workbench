package com.workbench.worklog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workbench.worklog.entity.WorkLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkLogMapper extends BaseMapper<WorkLog> {
}
