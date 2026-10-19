package com.workbench.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workbench.task.entity.Notification;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {
}
