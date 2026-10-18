<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Relations\BelongsTo;

/**
 * Directed scheduling dependency between two tasks in one project.
 * The predecessor must finish before the successor can start.
 */
class ProjectTaskDependency extends AbstractModel
{
    protected $fillable = [
        'project_id',
        'predecessor_task_id',
        'successor_task_id',
        'created_by',
    ];

    public function predecessor(): BelongsTo
    {
        return $this->belongsTo(ProjectTask::class, 'predecessor_task_id');
    }

    public function successor(): BelongsTo
    {
        return $this->belongsTo(ProjectTask::class, 'successor_task_id');
    }
}
