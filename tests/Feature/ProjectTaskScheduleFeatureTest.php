<?php

namespace Tests\Feature;

use App\Exceptions\ApiException;
use App\Models\Project;
use App\Models\ProjectTask;
use App\Models\User;
use App\Module\ProjectTaskDependencyService;
use App\Module\ProjectTaskScheduleService;
use Illuminate\Foundation\Testing\DatabaseTransactions;
use Tests\Feature\Concerns\ProjectPlanningFixture;
use Tests\TestCase;

class ProjectTaskScheduleFeatureTest extends TestCase
{
    use DatabaseTransactions;
    use ProjectPlanningFixture;

    private User $owner;
    private User $member;
    private Project $project;

    protected function setUp(): void
    {
        parent::setUp();
        $this->bootPlanning();
        $this->owner = $this->planningUser('schedule_owner');
        $this->member = $this->planningUser('schedule_member');
        $this->planningLogin($this->owner);
        $this->project = $this->planningProject($this->owner, [], [$this->member]);
    }

    protected function tearDown(): void
    {
        $this->shutdownPlanning();
        parent::tearDown();
    }

    private function prepareAutomaticScheduling(array $attributes = []): void
    {
        $this->project->task_schedule_mode = 'automatic';
        // Keep storage and project timezone identical so wall-clock assertions stay readable.
        $this->project->task_schedule_timezone = 'PRC';
        $this->project->task_schedule_workdays = json_encode([1, 2, 3, 4, 5]);
        foreach ($attributes as $key => $value) {
            $this->project->{$key} = $value;
        }
        $this->project->save();
    }

    private function scheduleTask(string $name, string $start, string $end, array $attributes = []): ProjectTask
    {
        return $this->planningTask($this->project, array_merge([
            'name' => $name,
            'start_at' => $start,
            'end_at' => $end,
        ], $attributes), $this->owner);
    }

    /**
     * Anchor A (Monday 09:00-18:00) plus successor B, then move A to Wednesday.
     *
     * @return array{0: ProjectTask, 1: ProjectTask}
     */
    private function seedChain(array $projectAttributes = []): array
    {
        $this->prepareAutomaticScheduling($projectAttributes);
        $anchor = $this->scheduleTask('Anchor', '2026-03-02 09:00:00', '2026-03-02 18:00:00');
        $successor = $this->scheduleTask('Successor', '2026-03-02 09:00:00', '2026-03-02 18:00:00');
        // add($successor, $anchor, 'predecessor'): the anchor blocks the successor.
        ProjectTaskDependencyService::add($successor->id, $anchor->id, 'predecessor');

        return [$anchor, $successor];
    }

    private function changeFor(array $preview, int $taskId): ?array
    {
        foreach ($preview['changes'] as $change) {
            if ((int)$change['id'] === $taskId) {
                return $change;
            }
        }
        return null;
    }

    public function test_settings_default_to_manual_with_weekdays(): void
    {
        $settings = ProjectTaskScheduleService::settings($this->project->id);

        $this->assertSame('manual', $settings['mode']);
        $this->assertSame([1, 2, 3, 4, 5], $settings['workdays']);
        $this->assertSame(0, $settings['buffer_days']);
        $this->assertSame([], $settings['holidays']);
    }

    public function test_update_settings_validates_mode_workdays_buffer_and_timezone(): void
    {
        $invalidPayloads = [
            ['mode' => 'sometimes'],
            ['workdays' => []],
            ['workdays' => [0, 8]],
            ['buffer_days' => 31],
            ['timezone' => 'Mars/Olympus'],
        ];

        foreach ($invalidPayloads as $payload) {
            try {
                ProjectTaskScheduleService::updateSettings($this->project->id, array_merge([
                    'mode' => 'automatic',
                    'workdays' => [1, 2, 3, 4, 5],
                    'timezone' => 'Asia/Shanghai',
                    'buffer_days' => 0,
                ], $payload));
                $this->fail('Invalid scheduling settings must be rejected: ' . json_encode($payload));
            } catch (ApiException $exception) {
                $this->assertNotSame('', $exception->getMessage());
            }
        }
    }

    public function test_update_settings_persists_normalized_values(): void
    {
        $settings = ProjectTaskScheduleService::updateSettings($this->project->id, [
            'mode' => 'automatic',
            'workdays' => [5, 3, 3, 1],
            'timezone' => 'Asia/Shanghai',
            'buffer_days' => 2,
            'holidays' => ['2026-03-05'],
        ]);

        $this->assertSame('automatic', $settings['mode']);
        $this->assertSame([1, 3, 5], $settings['workdays']);
        $this->assertSame(2, $settings['buffer_days']);
        $this->assertSame(['2026-03-05'], $settings['holidays']);
    }

    public function test_member_cannot_update_scheduling_settings(): void
    {
        $this->planningLogin($this->member);

        $this->expectException(ApiException::class);
        ProjectTaskScheduleService::updateSettings($this->project->id, [
            'mode' => 'automatic',
            'workdays' => [1, 2, 3, 4, 5],
            'timezone' => 'Asia/Shanghai',
            'buffer_days' => 0,
        ]);
    }

    public function test_preview_requires_the_dependency_feature(): void
    {
        $this->prepareAutomaticScheduling(['task_dependency' => 'close']);
        $anchor = $this->scheduleTask('Anchor', '2026-03-02 09:00:00', '2026-03-02 18:00:00');

        $this->expectException(ApiException::class);
        ProjectTaskScheduleService::preview($this->project->id, $anchor->id, '2026-03-04 09:00:00', '2026-03-04 18:00:00');
    }

    public function test_preview_requires_automatic_mode(): void
    {
        $anchor = $this->scheduleTask('Anchor', '2026-03-02 09:00:00', '2026-03-02 18:00:00');

        $this->expectException(ApiException::class);
        ProjectTaskScheduleService::preview($this->project->id, $anchor->id, '2026-03-04 09:00:00', '2026-03-04 18:00:00');
    }

    public function test_preview_rejects_completed_anchor_tasks(): void
    {
        $this->prepareAutomaticScheduling();
        $anchor = $this->scheduleTask('Anchor', '2026-03-02 09:00:00', '2026-03-02 18:00:00', ['complete_at' => now()]);

        $this->expectException(ApiException::class);
        ProjectTaskScheduleService::preview($this->project->id, $anchor->id, '2026-03-04 09:00:00', '2026-03-04 18:00:00');
    }

    public function test_preview_rejects_invalid_datetime_format(): void
    {
        $this->prepareAutomaticScheduling();
        $anchor = $this->scheduleTask('Anchor', '2026-03-02 09:00:00', '2026-03-02 18:00:00');

        $this->expectException(ApiException::class);
        ProjectTaskScheduleService::preview($this->project->id, $anchor->id, '2026-03-04', '2026-03-04');
    }

    public function test_preview_cascades_to_finish_to_start_successors(): void
    {
        [$anchor, $successor] = $this->seedChain();

        $preview = ProjectTaskScheduleService::preview(
            $this->project->id,
            $anchor->id,
            '2026-03-04 09:00:00',
            '2026-03-04 18:00:00'
        );

        $this->assertMatchesRegularExpression('/^[a-f0-9]{64}$/', $preview['preview_hash']);
        $this->assertSame(0, $preview['conflicts']);

        $anchorChange = $this->changeFor($preview, $anchor->id);
        $this->assertNotNull($anchorChange);
        $this->assertSame('2026-03-02 09:00:00', $anchorChange['before']['start_at']);
        $this->assertSame('2026-03-04 09:00:00', $anchorChange['after']['start_at']);

        $successorChange = $this->changeFor($preview, $successor->id);
        $this->assertNotNull($successorChange);
        $this->assertSame('调整排期起点', $anchorChange['reason']);
        $this->assertSame('2026-03-04 18:00:00', $successorChange['after']['start_at']);
        $this->assertSame('2026-03-05 03:00:00', $successorChange['after']['end_at']);
    }

    public function test_buffer_workdays_push_successors_later(): void
    {
        [$anchor, $successor] = $this->seedChain(['task_schedule_buffer_days' => 1]);

        $preview = ProjectTaskScheduleService::preview(
            $this->project->id,
            $anchor->id,
            '2026-03-04 09:00:00',
            '2026-03-04 18:00:00'
        );

        $successorChange = $this->changeFor($preview, $successor->id);
        $this->assertNotNull($successorChange);
        $this->assertSame('2026-03-05 18:00:00', $successorChange['after']['start_at']);
    }

    public function test_completed_successors_are_skipped_instead_of_moved(): void
    {
        [$anchor, $successor] = $this->seedChain();
        $successor->complete_at = now();
        $successor->save();

        $preview = ProjectTaskScheduleService::preview(
            $this->project->id,
            $anchor->id,
            '2026-03-04 09:00:00',
            '2026-03-04 18:00:00'
        );

        $this->assertNull($this->changeFor($preview, $successor->id));
        $reasons = array_column($preview['skipped'], 'reason', 'id');
        $this->assertSame('已完成任务保持原计划', $reasons[$successor->id]);
        $this->assertSame(1, $preview['conflicts']);
    }

    public function test_apply_rejects_a_stale_preview_hash(): void
    {
        [$anchor] = $this->seedChain();

        $this->expectException(ApiException::class);
        ProjectTaskScheduleService::apply(
            $this->project->id,
            $anchor->id,
            '2026-03-04 09:00:00',
            '2026-03-04 18:00:00',
            str_repeat('a', 64),
            'schedule-stale-0001'
        );
    }

    public function test_apply_moves_tasks_and_undo_restores_them(): void
    {
        [$anchor, $successor] = $this->seedChain();
        $preview = ProjectTaskScheduleService::preview(
            $this->project->id,
            $anchor->id,
            '2026-03-04 09:00:00',
            '2026-03-04 18:00:00'
        );

        $applied = ProjectTaskScheduleService::apply(
            $this->project->id,
            $anchor->id,
            '2026-03-04 09:00:00',
            '2026-03-04 18:00:00',
            $preview['preview_hash'],
            'schedule-apply-0001'
        );

        $this->assertSame('applied', $applied['status']);
        $this->assertSame(2, $applied['applied_count']);
        $this->assertSame('2026-03-04 18:00:00', $successor->fresh()->start_at->toDateTimeString());
        $this->assertSame('2026-03-04 09:00:00', $anchor->fresh()->start_at->toDateTimeString());

        $undone = ProjectTaskScheduleService::undo($this->project->id, $applied['batch_id']);

        $this->assertSame('undone', $undone['status']);
        $this->assertSame('2026-03-02 09:00:00', $successor->fresh()->start_at->toDateTimeString());
        $this->assertSame('2026-03-02 09:00:00', $anchor->fresh()->start_at->toDateTimeString());
    }

    public function test_apply_is_idempotent_for_the_same_request_id(): void
    {
        [$anchor] = $this->seedChain();
        $preview = ProjectTaskScheduleService::preview(
            $this->project->id,
            $anchor->id,
            '2026-03-04 09:00:00',
            '2026-03-04 18:00:00'
        );
        $payload = [
            $this->project->id,
            $anchor->id,
            '2026-03-04 09:00:00',
            '2026-03-04 18:00:00',
            $preview['preview_hash'],
            'schedule-replay-0001',
        ];

        $first = ProjectTaskScheduleService::apply(...$payload);
        $second = ProjectTaskScheduleService::apply(...$payload);

        $this->assertSame($first['batch_id'], $second['batch_id']);
        $this->assertSame('applied', $second['status']);
    }

    public function test_reusing_a_request_id_with_another_preview_is_rejected(): void
    {
        [$anchor] = $this->seedChain();
        $preview = ProjectTaskScheduleService::preview(
            $this->project->id,
            $anchor->id,
            '2026-03-04 09:00:00',
            '2026-03-04 18:00:00'
        );
        ProjectTaskScheduleService::apply(
            $this->project->id,
            $anchor->id,
            '2026-03-04 09:00:00',
            '2026-03-04 18:00:00',
            $preview['preview_hash'],
            'schedule-conflict-0001'
        );

        $otherPreview = ProjectTaskScheduleService::preview(
            $this->project->id,
            $anchor->id,
            '2026-03-05 09:00:00',
            '2026-03-05 18:00:00'
        );

        $this->expectException(ApiException::class);
        ProjectTaskScheduleService::apply(
            $this->project->id,
            $anchor->id,
            '2026-03-05 09:00:00',
            '2026-03-05 18:00:00',
            $otherPreview['preview_hash'],
            'schedule-conflict-0001'
        );
    }

    public function test_undo_is_blocked_after_a_manual_edit(): void
    {
        [$anchor, $successor] = $this->seedChain();
        $preview = ProjectTaskScheduleService::preview(
            $this->project->id,
            $anchor->id,
            '2026-03-04 09:00:00',
            '2026-03-04 18:00:00'
        );
        $applied = ProjectTaskScheduleService::apply(
            $this->project->id,
            $anchor->id,
            '2026-03-04 09:00:00',
            '2026-03-04 18:00:00',
            $preview['preview_hash'],
            'schedule-undo-0001'
        );

        $successor->refresh();
        $successor->end_at = '2026-03-05 12:00:00';
        $successor->save();

        try {
            ProjectTaskScheduleService::undo($this->project->id, $applied['batch_id']);
            $this->fail('Undo must not overwrite a manual edit made after the batch.');
        } catch (ApiException $exception) {
            $this->assertStringContainsString('已被修改', $exception->getMessage());
        }
    }

    public function test_batches_are_listed_for_the_project(): void
    {
        [$anchor] = $this->seedChain();
        $preview = ProjectTaskScheduleService::preview(
            $this->project->id,
            $anchor->id,
            '2026-03-04 09:00:00',
            '2026-03-04 18:00:00'
        );
        ProjectTaskScheduleService::apply(
            $this->project->id,
            $anchor->id,
            '2026-03-04 09:00:00',
            '2026-03-04 18:00:00',
            $preview['preview_hash'],
            'schedule-list-0001'
        );

        $batches = ProjectTaskScheduleService::batches($this->project->id);

        $this->assertCount(1, $batches);
        $this->assertSame('applied', $batches[0]['status']);
        $this->assertSame(2, $batches[0]['changed_count']);
    }
}
