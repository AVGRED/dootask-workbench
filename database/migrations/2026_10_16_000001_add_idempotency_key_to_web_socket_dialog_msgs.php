<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('web_socket_dialog_msgs', function (Blueprint $table) {
            $table->char('idempotency_key', 64)->nullable()->after('dialog_id')->comment('幂等消息键');
            $table->unique('idempotency_key', 'web_socket_dialog_msgs_idempotency_unique');
        });
    }

    public function down(): void
    {
        Schema::table('web_socket_dialog_msgs', function (Blueprint $table) {
            $table->dropUnique('web_socket_dialog_msgs_idempotency_unique');
            $table->dropColumn('idempotency_key');
        });
    }
};
