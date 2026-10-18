<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('project_task_template_revisions', function (Blueprint $table) {
            $table->string('milestone_name', 100)->nullable()->after('content')->comment('模板主任务关联里程碑名称');
        });
    }

    public function down(): void
    {
        Schema::table('project_task_template_revisions', function (Blueprint $table) {
            $table->dropColumn('milestone_name');
        });
    }
};
