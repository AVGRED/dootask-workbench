<?php

namespace App\Models;

/**
 * Immutable content snapshot for a project task template version.
 */
class ProjectTaskTemplateRevision extends AbstractModel
{
    protected $fillable = [
        'template_id',
        'project_id',
        'version',
        'name',
        'title',
        'content',
        'milestone_name',
        'estimated_minutes',
        'subtasks',
        'changed_by',
    ];

    protected $casts = [
        'version' => 'integer',
        'subtasks' => 'array',
    ];
}
