<?php

namespace App\Models;

use Illuminate\Database\Eloquent\SoftDeletes;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class ProjectTaskWorkLog extends AbstractModel
{
    use SoftDeletes;

    protected $fillable = ['project_id', 'task_id', 'userid', 'request_id', 'request_payload_hash', 'minutes', 'work_date', 'note'];

    protected $casts = [
        'minutes' => 'integer',
        'work_date' => 'date:Y-m-d',
    ];

    public function task(): BelongsTo
    {
        return $this->belongsTo(ProjectTask::class, 'task_id');
    }

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class, 'userid', 'userid');
    }
}
