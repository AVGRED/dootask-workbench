<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('project_milestones', function (Blueprint $table) {
            $table->unsignedInteger('reminder_generation')
                ->default(0)
                ->after('reminder_days')
                ->comment('提醒配置周期号，用于提醒消息幂等');
        });
    }

    public function down(): void
    {
        Schema::table('project_milestones', function (Blueprint $table) {
            $table->dropColumn('reminder_generation');
        });
    }
};
