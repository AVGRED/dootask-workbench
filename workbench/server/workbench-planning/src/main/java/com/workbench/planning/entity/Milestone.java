package com.workbench.planning.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 里程碑，映射旧库 {@code project_milestones} 表。 */
@TableName("project_milestones")
public class Milestone {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long projectId;
    private String name;
    private Long ownerUserid;
    private String description;
    private LocalDate targetAt;
    private String status;
    private Integer reminderDays;
    private LocalDateTime remindedAt;
    private Integer sort;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getOwnerUserid() {
        return ownerUserid;
    }

    public void setOwnerUserid(Long ownerUserid) {
        this.ownerUserid = ownerUserid;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDate getTargetAt() {
        return targetAt;
    }

    public void setTargetAt(LocalDate targetAt) {
        this.targetAt = targetAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getReminderDays() {
        return reminderDays;
    }

    public void setReminderDays(Integer reminderDays) {
        this.reminderDays = reminderDays;
    }

    public LocalDateTime getRemindedAt() {
        return remindedAt;
    }

    public void setRemindedAt(LocalDateTime remindedAt) {
        this.remindedAt = remindedAt;
    }

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
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
