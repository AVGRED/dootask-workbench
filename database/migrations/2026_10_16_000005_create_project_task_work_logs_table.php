<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('project_task_work_logs', function (Blueprint $table) {
            $table->id();
            $table->unsignedBigInteger('project_id');
            $table->unsignedBigInteger('task_id');
            $table->unsignedBigInteger('userid');
            $table->string('request_id', 64);
            $table->unsignedSmallInteger('minutes');
            $table->date('work_date');
            $table->string('note', 500)->nullable();
            $table->timestamps();
            $table->index(['project_id', 'work_date', 'userid'], 'task_work_logs_capacity_idx');
            $table->index(['task_id', 'project_id', 'work_date', 'id'], 'task_work_logs_task_history_idx');
            $table->unique(['project_id', 'userid', 'request_id'], 'task_work_logs_request_unique');
            $table->foreign('project_id', 'task_work_logs_project_fk')->references('id')->on('projects')->onDelete('cascade');
            // Soft delete/archive keeps the audit rows. Permanent task deletion purges them.
            $table->foreign('task_id', 'task_work_logs_task_fk')->references('id')->on('project_tasks')->onDelete('cascade');
            $table->foreign('userid', 'task_work_logs_user_fk')->references('userid')->on('users')->onDelete('cascade');
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('project_task_work_logs');
    }
};
