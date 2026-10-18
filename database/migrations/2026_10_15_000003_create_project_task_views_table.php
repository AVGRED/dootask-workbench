<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('project_task_views', function (Blueprint $table) {
            $table->id();
            $table->unsignedBigInteger('project_id')->comment('所属项目ID');
            $table->unsignedBigInteger('created_by')->comment('创建人');
            $table->string('name', 100)->comment('视图名称');
            $table->string('visibility', 20)->default('personal')->comment('personal/project');
            $table->json('definition')->comment('筛选和展示定义');
            $table->timestamps();

            $table->index(['project_id', 'visibility', 'created_by'], 'project_task_views_access_idx');
            $table->foreign('project_id', 'project_task_views_project_fk')
                ->references('id')->on('projects')->onDelete('cascade');
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('project_task_views');
    }
};
