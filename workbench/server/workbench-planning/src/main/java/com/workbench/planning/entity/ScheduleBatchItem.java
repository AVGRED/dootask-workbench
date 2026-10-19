package com.workbench.planning.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/** 批次明细：一个任务的排期前后值，撤销时按此还原。 */
@TableName("schedule_batch_items")
public class ScheduleBatchItem {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long batchId;
    private Long taskId;
    private LocalDateTime oldStart;
    private LocalDateTime oldEnd;
    private LocalDateTime newStart;
    private LocalDateTime newEnd;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getBatchId() {
        return batchId;
    }

    public void setBatchId(Long batchId) {
        this.batchId = batchId;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public LocalDateTime getOldStart() {
        return oldStart;
    }

    public void setOldStart(LocalDateTime oldStart) {
        this.oldStart = oldStart;
    }

    public LocalDateTime getOldEnd() {
        return oldEnd;
    }

    public void setOldEnd(LocalDateTime oldEnd) {
        this.oldEnd = oldEnd;
    }

    public LocalDateTime getNewStart() {
        return newStart;
    }

    public void setNewStart(LocalDateTime newStart) {
        this.newStart = newStart;
    }

    public LocalDateTime getNewEnd() {
        return newEnd;
    }

    public void setNewEnd(LocalDateTime newEnd) {
        this.newEnd = newEnd;
    }
}
