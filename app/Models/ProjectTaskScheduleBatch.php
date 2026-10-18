<?php

namespace App\Models;

class ProjectTaskScheduleBatch extends AbstractModel
{
    protected $fillable = [
        'project_id', 'created_by', 'request_id', 'preview_hash', 'request_payload_hash', 'timezone', 'status', 'changes', 'undone_at',
    ];

    protected $casts = [
        'changes' => 'array',
        'undone_at' => 'datetime',
    ];
}
