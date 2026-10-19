package com.workbench.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workbench.task.entity.TaskComment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TaskCommentMapper extends BaseMapper<TaskComment> {
}
