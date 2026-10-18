<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Relations\BelongsTo;

/**
 * A delivery milestone belonging to a project.
 * @property int $reminder_days -1=disabled, otherwise days before target date
 * @property int $reminder_generation Increments whenever reminder settings are re-armed
 * @property \Illuminate\Support\Carbon|null $reminded_at
 */
class ProjectMilestone extends AbstractModel
{
    protected $fillable = [
        'project_id',
        'name',
        'description',
        'owner_userid',
        'target_at',
        'status',
        'reminder_days',
        'reminder_generation',
        'reminder_attempt_count',
        'reminder_last_attempt_at',
        'reminder_last_error_code',
        'reminded_at',
        'sort',
        'created_by',
    ];

    public function project(): BelongsTo
    {
        return $this->belongsTo(Project::class, 'project_id');
    }

    protected $casts = [
        'reminder_generation' => 'integer',
        'reminder_attempt_count' => 'integer',
        'reminder_last_attempt_at' => 'datetime',
        'reminded_at' => 'datetime',
    ];
}
