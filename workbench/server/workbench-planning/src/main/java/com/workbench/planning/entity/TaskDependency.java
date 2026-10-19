package com.workbench.planning.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/** 任务依赖（FS：前置完成后后置才能开始），映射旧库 {@code project_task_dependencies}。 */
@TableName("project_task_dependencies")
public class TaskDependency {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long projectId;
    private Long predecessorTaskId;
    private Long successorTaskId;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

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

    public Long getPredecessorTaskId() {
        return predecessorTaskId;
    }

    public void setPredecessorTaskId(Long predecessorTaskId) {
        this.predecessorTaskId = predecessorTaskId;
    }

    public Long getSuccessorTaskId() {
        return successorTaskId;
    }

    public void setSuccessorTaskId(Long successorTaskId) {
        this.successorTaskId = successorTaskId;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
