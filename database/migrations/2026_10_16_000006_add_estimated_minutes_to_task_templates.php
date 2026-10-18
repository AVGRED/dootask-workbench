<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('project_task_templates', function (Blueprint $table) {
            $table->unsignedInteger('estimated_minutes')->nullable()->after('milestone_name')->comment('模板预估工时，分钟');
        });
        Schema::table('project_task_template_revisions', function (Blueprint $table) {
            $table->unsignedInteger('estimated_minutes')->nullable()->after('milestone_name')->comment('版本预估工时，分钟');
        });
    }

    public function down(): void
    {
        Schema::table('project_task_template_revisions', function (Blueprint $table) {
            $table->dropColumn('estimated_minutes');
        });
        Schema::table('project_task_templates', function (Blueprint $table) {
            $table->dropColumn('estimated_minutes');
        });
    }
};
