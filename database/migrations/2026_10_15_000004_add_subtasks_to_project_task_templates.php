<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('project_task_templates', function (Blueprint $table) {
            $table->json('subtasks')->nullable()->after('content')->comment('模板子任务配置');
        });
    }

    public function down(): void
    {
        Schema::table('project_task_templates', function (Blueprint $table) {
            $table->dropColumn('subtasks');
        });
    }
};
