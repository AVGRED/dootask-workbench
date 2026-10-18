<?php

namespace Tests\Feature;

use App\Exceptions\ApiException;
use App\Models\Project;
use App\Models\ProjectTask;
use App\Models\ProjectTaskWorkLog;
use App\Models\User;
use App\Module\ProjectTaskWorkService;
use Illuminate\Foundation\Testing\DatabaseTransactions;
use Tests\Feature\Concerns\ProjectPlanningFixture;
use Tests\TestCase;

class ProjectTaskWorkFeatureTest extends TestCase
{
    use DatabaseTransactions;
    use ProjectPlanningFixture;

    private User $owner;
    private User $member;
    private User $stranger;
    private Project $project;
    private ProjectTask $task;

    protected function setUp(): void
    {
        parent::setUp();
        $this->bootPlanning();
        $this->owner = $this->planningUser('work_owner');
        $this->member = $this->planningUser('work_member');
        $this->stranger = $this->planningUser('work_stranger');
        $this->planningLogin($this->owner);
        $this->project = $this->planningProject($this->owner, [
            'task_schedule_timezone' => 'UTC',
        ], [$this->member]);
        $this->task = $this->planningTask($this->project, ['name' => 'Work task'], $this->owner);
    }

    protected function tearDown(): void
    {
        $this->shutdownPlanning();
        parent::tearDown();
    }

    private function addLog(string $requestId, int $minutes = 60, ?string $date = '2026-03-02', string $note = ''): array
    {
        return ProjectTaskWorkService::addLog($this->project->id, $this->task->id, [
            'request_id' => $requestId,
            'minutes' => $minutes,
            'work_date' => $date,
            'note' => $note,
        ]);
    }

    public function test_estimate_and_remaining_are_computed_from_logs(): void
    {
        ProjectTaskWorkService::setEstimate($this->project->id, $this->task->id, 120);
        $this->addLog('req-estimate-0001', 60);

        $summary = ProjectTaskWorkService::taskSummary($this->project->id, $this->task->id);

        $this->assertSame(120, $summary['estimated_minutes']);
        $this->assertSame(60, $summary['logged_minutes']);
        $this->assertSame(60, $summary['remaining_minutes']);
        $this->assertSame(0, $summary['overrun_minutes']);
    }

    public function test_overrun_is_reported_instead_of_a_negative_remainder(): void
    {
        ProjectTaskWorkService::setEstimate($this->project->id, $this->task->id, 30);
        $this->addLog('req-overrun-0001', 60);

        $summary = ProjectTaskWorkService::taskSummary($this->project->id, $this->task->id);

        $this->assertSame(0, $summary['remaining_minutes']);
        $this->assertSame(30, $summary['overrun_minutes']);
    }

    public function test_estimate_range_is_validated(): void
    {
        $this->expectException(ApiException::class);
        ProjectTaskWorkService::setEstimate($this->project->id, $this->task->id, 1000001);
    }

    public function test_zero_estimate_is_stored_as_empty(): void
    {
        ProjectTaskWorkService::setEstimate($this->project->id, $this->task->id, 90);
        ProjectTaskWorkService::setEstimate($this->project->id, $this->task->id, 0);

        $this->task->refresh();
        $this->assertNull($this->task->estimated_minutes);
    }

    public function test_worklog_minutes_are_validated(): void
    {
        $this->expectException(ApiException::class);
        $this->addLog('req-invalid-0001', 1441);
    }

    public function test_worklog_date_must_be_a_real_calendar_date(): void
    {
        $this->expectException(ApiException::class);
        $this->addLog('req-invalid-0002', 60, '2026-02-30');
    }

    public function test_worklog_note_is_limited_to_500_characters(): void
    {
        $this->expectException(ApiException::class);
        $this->addLog('req-invalid-0003', 60, '2026-03-02', str_repeat('注', 501));
    }

    public function test_request_id_must_be_stable_and_well_formed(): void
    {
        $this->expectException(ApiException::class);
        $this->addLog('short');
    }

    public function test_repeated_request_id_returns_the_original_record(): void
    {
        $first = $this->addLog('req-replay-0001', 45);
        $second = $this->addLog('req-replay-0001', 45);

        $this->assertSame((int)$first['log']['id'], (int)$second['log']['id']);
        $this->assertSame(1, ProjectTaskWorkLog::query()->where('task_id', $this->task->id)->count());
        $this->assertSame(45, $second['summary']['logged_minutes']);
    }

    public function test_reused_request_id_with_different_payload_is_rejected(): void
    {
        $this->addLog('req-conflict-0001', 45);

        $this->expectException(ApiException::class);
        $this->addLog('req-conflict-0001', 90);
    }

    public function test_deleted_log_is_not_recreated_by_a_late_retry(): void
    {
        $created = $this->addLog('req-tombstone-0001', 30);
        ProjectTaskWorkService::deleteLog($this->project->id, (int)$created['log']['id']);

        $this->assertSame(0, ProjectTaskWorkLog::query()->where('task_id', $this->task->id)->count());

        $this->expectException(ApiException::class);
        $this->addLog('req-tombstone-0001', 30);
    }

    public function test_member_cannot_delete_someone_elses_worklog(): void
    {
        // Task lead rights are enough to write work, but still not enough to delete
        // another member's record: only its author or the project owner may delete it.
        $this->planningTaskOwner($this->task, $this->member);
        $created = $this->addLog('req-delete-0001', 30);
        $this->planningLogin($this->member);

        try {
            ProjectTaskWorkService::deleteLog($this->project->id, (int)$created['log']['id']);
            $this->fail('A member must not delete another member\'s worklog.');
        } catch (ApiException $exception) {
            $this->assertStringContainsString('只能删除自己的工时记录', $exception->getMessage());
        }
        $this->assertSame(1, ProjectTaskWorkLog::query()->where('task_id', $this->task->id)->count());
    }

    public function test_project_owner_can_delete_a_member_worklog(): void
    {
        $this->planningTaskOwner($this->task, $this->member);
        $this->planningLogin($this->member);
        $created = $this->addLog('req-delete-0002', 30);
        $this->planningLogin($this->owner);

        ProjectTaskWorkService::deleteLog($this->project->id, (int)$created['log']['id']);

        $this->assertSame(0, ProjectTaskWorkLog::query()->where('task_id', $this->task->id)->count());
    }

    public function test_non_member_cannot_record_work(): void
    {
        $this->planningLogin($this->stranger);

        $this->expectException(ApiException::class);
        $this->addLog('req-stranger-0001', 30);
    }

    public function test_capacity_rejects_ranges_longer_than_93_days(): void
    {
        $this->expectException(ApiException::class);
        ProjectTaskWorkService::capacity($this->project->id, '2026-01-01', '2026-04-10');
    }

    public function test_capacity_uses_working_days_from_the_project_calendar(): void
    {
        $this->task->estimated_minutes = 480;
        $this->task->start_at = '2026-03-02 09:00:00';
        $this->task->end_at = '2026-03-02 18:00:00';
        $this->task->save();

        $capacity = ProjectTaskWorkService::capacity($this->project->id, '2026-03-02', '2026-03-06');

        // Five working days at eight hours each, for the owner and the member.
        $this->assertSame(4800, $capacity['totals']['available_minutes']);
        $this->assertSame(480, $capacity['totals']['estimated_minutes']);
        $this->assertCount(2, $capacity['members']);
        $this->assertSame([1, 2, 3, 4, 5], $capacity['capacity_baseline']['workdays']);
    }

    public function test_holiday_exceptions_reduce_the_available_capacity(): void
    {
        $this->project->task_schedule_holidays = ['2026-03-03'];
        $this->project->save();

        $capacity = ProjectTaskWorkService::capacity($this->project->id, '2026-03-02', '2026-03-06');

        $this->assertSame(3840, $capacity['totals']['available_minutes']);
    }

    public function test_left_members_do_not_receive_capacity_but_keep_logged_time(): void
    {
        $this->addLog('req-left-0001', 120);
        $this->planningLogin($this->member);
        \App\Models\ProjectUser::where('project_id', $this->project->id)
            ->where('userid', $this->owner->userid)->delete();

        $capacity = ProjectTaskWorkService::capacity($this->project->id, '2026-03-02', '2026-03-06');
        $row = collect($capacity['members'])->firstWhere('userid', $this->owner->userid);

        $this->assertNotNull($row);
        $this->assertFalse($row['capacity_member']);
        $this->assertSame(0, $row['available_minutes']);
        $this->assertSame(120, $row['logged_minutes']);
    }
}
