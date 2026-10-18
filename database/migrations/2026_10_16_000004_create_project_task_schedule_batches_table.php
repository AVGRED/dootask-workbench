<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('project_task_schedule_batches', function (Blueprint $table) {
            $table->id();
            $table->unsignedBigInteger('project_id');
            $table->unsignedBigInteger('created_by');
            $table->string('request_id', 64);
            $table->string('preview_hash', 64);
            $table->string('timezone', 64)->default('UTC');
            $table->string('status', 20)->default('applied')->comment('applied/undone');
            $table->json('changes')->comment('批次任务日期前后快照');
            $table->dateTime('undone_at')->nullable();
            $table->timestamps();

            $table->unique(['project_id', 'request_id'], 'project_task_schedule_batches_request_unique');
            $table->index(['project_id', 'created_by', 'created_at'], 'project_task_schedule_batches_audit_idx');
            $table->foreign('project_id', 'project_task_schedule_batches_project_fk')
                ->references('id')->on('projects')->onDelete('cascade');
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('project_task_schedule_batches');
    }
};
