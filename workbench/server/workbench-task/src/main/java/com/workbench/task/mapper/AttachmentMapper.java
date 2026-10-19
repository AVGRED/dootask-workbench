package com.workbench.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workbench.task.entity.Attachment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AttachmentMapper extends BaseMapper<Attachment> {
}
