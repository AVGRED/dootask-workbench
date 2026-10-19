package com.workbench.planning.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/** 排期应用批次：一次 apply 的变更集合，可整体撤销。 */
@TableName("schedule_batches")
public class ScheduleBatch {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long projectId;
    private Long userid;
    private String requestId;
    private Integer changeCount;
    private LocalDateTime createdAt;
    private LocalDateTime undoneAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getUserid() {
        return userid;
    }

    public void setUserid(Long userid) {
        this.userid = userid;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Integer getChangeCount() {
        return changeCount;
    }

    public void setChangeCount(Integer changeCount) {
        this.changeCount = changeCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUndoneAt() {
        return undoneAt;
    }

    public void setUndoneAt(LocalDateTime undoneAt) {
        this.undoneAt = undoneAt;
    }
}
