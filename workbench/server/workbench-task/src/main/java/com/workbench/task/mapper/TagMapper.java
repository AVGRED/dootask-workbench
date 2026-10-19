package com.workbench.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workbench.task.entity.Tag;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TagMapper extends BaseMapper<Tag> {
}
