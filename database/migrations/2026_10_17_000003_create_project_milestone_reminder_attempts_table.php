<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('project_milestone_reminder_attempts', function (Blueprint $table) {
            $table->id();
            $table->unsignedBigInteger('project_id');
            $table->unsignedBigInteger('milestone_id');
            $table->unsignedInteger('reminder_generation');
            $table->date('target_at');
            $table->string('request_id', 64)->nullable();
            $table->string('status', 32);
            $table->string('result_code', 64)->nullable();
            $table->dateTime('attempted_at');
            $table->timestamps();

            $table->index(['project_id', 'milestone_id', 'id'], 'milestone_reminder_attempts_history_idx');
            $table->index(['milestone_id', 'id'], 'milestone_reminder_attempts_retention_idx');
            $table->foreign('project_id', 'milestone_reminder_attempts_project_fk')
                ->references('id')->on('projects')->onDelete('cascade');
            $table->foreign('milestone_id', 'milestone_reminder_attempts_milestone_fk')
                ->references('id')->on('project_milestones')->onDelete('cascade');
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('project_milestone_reminder_attempts');
    }
};
