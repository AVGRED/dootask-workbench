<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('projects', function (Blueprint $table) {
            $table->string('task_schedule_mode', 20)->default('manual')->after('task_dependency')->comment('任务排期模式');
            $table->json('task_schedule_workdays')->nullable()->after('task_schedule_mode')->comment('工作日，ISO weekday 1-7');
            $table->string('task_schedule_timezone', 64)->nullable()->after('task_schedule_workdays')->comment('排期时区');
            $table->unsignedTinyInteger('task_schedule_buffer_days')->default(0)->after('task_schedule_timezone')->comment('依赖缓冲工作日');
            $table->unsignedInteger('task_schedule_revision')->default(1)->after('task_schedule_buffer_days')->comment('排期规则版本');
        });

        Schema::table('project_tasks', function (Blueprint $table) {
            $table->unsignedInteger('estimated_minutes')->nullable()->after('milestone_id')->comment('预估工时，分钟');
        });
    }

    public function down(): void
    {
        Schema::table('project_tasks', function (Blueprint $table) {
            $table->dropColumn('estimated_minutes');
        });
        Schema::table('projects', function (Blueprint $table) {
            $table->dropColumn([
                'task_schedule_mode',
                'task_schedule_workdays',
                'task_schedule_timezone',
                'task_schedule_buffer_days',
                'task_schedule_revision',
            ]);
        });
    }
};
