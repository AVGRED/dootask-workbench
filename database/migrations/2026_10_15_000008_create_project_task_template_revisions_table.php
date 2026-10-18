<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('project_task_template_revisions', function (Blueprint $table) {
            $table->bigIncrements('id');
            $table->unsignedBigInteger('template_id');
            $table->unsignedBigInteger('project_id');
            $table->unsignedInteger('version');
            $table->string('name', 100);
            $table->string('title')->nullable();
            $table->text('content')->nullable();
            $table->json('subtasks')->nullable();
            $table->unsignedBigInteger('changed_by')->nullable();
            $table->timestamps();
            $table->unique(['template_id', 'version'], 'uq_template_revision_version');
            $table->index(['project_id', 'template_id'], 'idx_template_revision_project');
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('project_task_template_revisions');
    }
};
