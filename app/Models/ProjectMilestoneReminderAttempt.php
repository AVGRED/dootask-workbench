<?php

namespace App\Models;

class ProjectMilestoneReminderAttempt extends AbstractModel
{
    protected $fillable = [
        'project_id', 'milestone_id', 'reminder_generation', 'target_at', 'request_id',
        'status', 'result_code', 'attempted_at',
    ];

    protected $casts = [
        'reminder_generation' => 'integer',
        'target_at' => 'date:Y-m-d',
        'attempted_at' => 'datetime',
    ];
}
