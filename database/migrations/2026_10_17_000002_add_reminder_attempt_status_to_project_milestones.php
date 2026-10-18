<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('project_milestones', function (Blueprint $table) {
            $table->unsignedInteger('reminder_attempt_count')->default(0)->after('reminder_generation');
            $table->dateTime('reminder_last_attempt_at')->nullable()->after('reminder_attempt_count');
            $table->string('reminder_last_error_code', 40)->nullable()->after('reminder_last_attempt_at');
        });
    }

    public function down(): void
    {
        Schema::table('project_milestones', function (Blueprint $table) {
            $table->dropColumn(['reminder_attempt_count', 'reminder_last_attempt_at', 'reminder_last_error_code']);
        });
    }
};
