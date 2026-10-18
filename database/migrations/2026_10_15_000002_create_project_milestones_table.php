<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('project_milestones', function (Blueprint $table) {
            $table->id();
            $table->unsignedBigInteger('project_id')->comment('所属项目ID');
            $table->string('name', 100)->comment('里程碑名称');
            $table->string('description', 500)->nullable()->comment('里程碑说明');
            $table->date('target_at')->nullable()->comment('目标日期');
            $table->string('status', 20)->default('open')->comment('状态：open/completed');
            $table->smallInteger('reminder_days')->default(-1)->comment('提醒提前天数：-1关闭，0当天');
            $table->dateTime('reminded_at')->nullable()->comment('最近一次提醒时间');
            $table->unsignedInteger('sort')->default(0)->comment('排序');
            $table->unsignedBigInteger('created_by')->nullable()->comment('创建人');
            $table->timestamps();

            $table->index(['project_id', 'status', 'sort'], 'project_milestones_project_status_sort_idx');
            $table->index(['status', 'reminded_at', 'target_at'], 'project_milestones_reminder_scan_idx');
            $table->foreign('project_id', 'project_milestones_project_fk')
                ->references('id')->on('projects')->onDelete('cascade');
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('project_milestones');
    }
};
