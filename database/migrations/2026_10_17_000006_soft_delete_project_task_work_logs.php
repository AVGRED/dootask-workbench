<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('project_task_work_logs', function (Blueprint $table) {
            // Preserve request-id uniqueness after a visible worklog is removed. This
            // prevents a delayed idempotent retry from recreating the deleted record.
            $table->softDeletes();
        });
    }

    public function down(): void
    {
        Schema::table('project_task_work_logs', function (Blueprint $table) {
            $table->dropSoftDeletes();
        });
    }
};
