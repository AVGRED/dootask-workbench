<?php

namespace App\Models;

class ProjectTaskView extends AbstractModel
{
    protected $fillable = [
        'project_id', 'created_by', 'name', 'visibility', 'definition',
    ];

    protected $casts = [
        'definition' => 'array',
    ];
}
