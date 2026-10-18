<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('project_task_dependencies', function (Blueprint $table) {
            $table->id();
            $table->unsignedBigInteger('project_id')->comment('所属项目ID');
            $table->unsignedBigInteger('predecessor_task_id')->comment('前置任务ID');
            $table->unsignedBigInteger('successor_task_id')->comment('后置任务ID');
            $table->unsignedBigInteger('created_by')->nullable()->comment('创建人');
            $table->timestamps();

            $table->unique(['predecessor_task_id', 'successor_task_id'], 'project_task_dependencies_edge_unique');
            $table->index(['project_id', 'predecessor_task_id'], 'project_task_dependencies_project_predecessor_idx');
            $table->index(['project_id', 'successor_task_id'], 'project_task_dependencies_project_successor_idx');
            $table->index('successor_task_id', 'project_task_dependencies_successor_fk_idx');

            // Keep dependency rows from outliving their project or either task.
            // Soft deletes remain restorable; hard deletes clean up these edges.
            $table->foreign('project_id', 'project_task_dependencies_project_fk')
                ->references('id')->on('projects')->onDelete('cascade');
            $table->foreign('predecessor_task_id', 'project_task_dependencies_predecessor_fk')
                ->references('id')->on('project_tasks')->onDelete('cascade');
            $table->foreign('successor_task_id', 'project_task_dependencies_successor_fk')
                ->references('id')->on('project_tasks')->onDelete('cascade');
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('project_task_dependencies');
    }
};
