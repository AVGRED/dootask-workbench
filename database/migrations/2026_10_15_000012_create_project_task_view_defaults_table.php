<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('project_task_view_defaults', function (Blueprint $table) {
            $table->id();
            $table->unsignedBigInteger('project_id')->comment('所属项目ID');
            $table->unsignedBigInteger('userid')->comment('设置默认视图的用户ID');
            $table->unsignedBigInteger('view_id')->comment('默认视图ID');
            $table->timestamps();

            $table->unique(['project_id', 'userid'], 'project_task_view_defaults_user_unique');
            $table->index(['project_id', 'view_id'], 'project_task_view_defaults_view_idx');
            $table->foreign('view_id', 'project_task_view_defaults_view_fk')
                ->references('id')->on('project_task_views')->onDelete('cascade');
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('project_task_view_defaults');
    }
};
