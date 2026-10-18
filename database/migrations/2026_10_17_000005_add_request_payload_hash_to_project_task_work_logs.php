<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('project_task_work_logs', function (Blueprint $table) {
            $table->string('request_payload_hash', 64)->nullable()->after('request_id');
        });
    }

    public function down(): void
    {
        Schema::table('project_task_work_logs', function (Blueprint $table) {
            $table->dropColumn('request_payload_hash');
        });
    }
};
