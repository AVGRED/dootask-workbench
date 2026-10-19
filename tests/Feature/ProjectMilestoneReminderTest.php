<?php

namespace Tests\Feature;

use App\Models\Project;
use App\Models\ProjectMilestone;
use App\Models\User;
use App\Tasks\ProjectMilestoneRemindTask;
use Carbon\Carbon;
use Illuminate\Foundation\Testing\DatabaseTransactions;
use ReflectionMethod;
use Tests\Feature\Concerns\ProjectPlanningFixture;
use Tests\TestCase;

/**
 * The reminder task runs from the crontab entry every minute. Its scheduling rules
 * (local 09:00 gate, seven-day horizon, per-milestone window) and its idempotency key
 * are pure functions; they are exercised here without delivering real messages.
 */
class ProjectMilestoneReminderTest extends TestCase
{
    use DatabaseTransactions;
    use ProjectPlanningFixture;

    private User $owner;
    private Project $project;

    protected function setUp(): void
    {
        parent::setUp();
        $this->bootPlanning();
        $this->owner = $this->planningUser('reminder_owner');
        $this->planningLogin($this->owner);
        $this->project = $this->planningProject($this->owner, [
            'task_schedule_timezone' => 'Asia/Shanghai',
        ]);
    }

    protected function tearDown(): void
    {
        $this->shutdownPlanning();
        parent::tearDown();
    }

    private function milestone(array $attributes = [], ?Project $project = null): ProjectMilestone
    {
        $milestone = $this->planningMilestone($project ?: $this->project, array_merge([
            'name' => 'Release',
            'target_at' => '2026-10-25',
            'reminder_days' => 1,
            'reminder_generation' => 1,
        ], $attributes));
        $milestone->setRelation('project', $project ?: $this->project);
        return $milestone;
    }

    private function dueDate(ProjectMilestone $milestone, Carbon $now): ?Carbon
    {
        $method = new ReflectionMethod(ProjectMilestoneRemindTask::class, 'targetDateIfDue');
        $method->setAccessible(true);
        return $method->invoke(null, $milestone, $now);
    }

    private function idempotencyKey(ProjectMilestone $milestone, Carbon $targetAt): string
    {
        $method = new ReflectionMethod(ProjectMilestoneRemindTask::class, 'idempotencyKey');
        $method->setAccessible(true);
        return $method->invoke(null, $milestone, $targetAt);
    }

    public function test_reminders_are_not_sent_before_local_nine(): void
    {
        $milestone = $this->milestone();
        // 00:30 UTC is 08:30 in Asia/Shanghai.
        $now = Carbon::parse('2026-10-24 00:30:00', 'UTC');

        $this->assertNull($this->dueDate($milestone, $now));
    }

    public function test_reminder_is_due_inside_its_window(): void
    {
        $milestone = $this->milestone(['target_at' => '2026-10-25', 'reminder_days' => 1]);
        // 02:00 UTC is 10:00 in Asia/Shanghai; one day before the target date.
        $now = Carbon::parse('2026-10-24 02:00:00', 'UTC');

        $due = $this->dueDate($milestone, $now);

        $this->assertNotNull($due);
        $this->assertSame('2026-10-25', $due->toDateString());
    }

    public function test_reminder_is_not_due_before_its_window_opens(): void
    {
        $milestone = $this->milestone(['target_at' => '2026-10-25', 'reminder_days' => 1]);
        $now = Carbon::parse('2026-10-22 02:00:00', 'UTC');

        $this->assertNull($this->dueDate($milestone, $now));
    }

    public function test_reminder_is_not_due_beyond_the_seven_day_horizon(): void
    {
        $milestone = $this->milestone(['target_at' => '2026-11-10', 'reminder_days' => 7]);
        $now = Carbon::parse('2026-10-25 02:00:00', 'UTC');

        $this->assertNull($this->dueDate($milestone, $now));
    }

    public function test_archived_projects_never_remind(): void
    {
        $archived = $this->planningProject($this->owner, ['archived_at' => now()]);
        $milestone = $this->milestone(['target_at' => '2026-10-25'], $archived);
        $now = Carbon::parse('2026-10-24 02:00:00', 'UTC');

        $this->assertNull($this->dueDate($milestone, $now));
    }

    public function test_idempotency_key_is_stable_per_generation_and_changes_after_rearm(): void
    {
        $milestone = $this->milestone(['target_at' => '2026-10-25', 'reminder_generation' => 3]);
        $targetAt = Carbon::parse('2026-10-25 00:00:00');

        $key = $this->idempotencyKey($milestone, $targetAt);
        $this->assertMatchesRegularExpression('/^[a-f0-9]{64}$/', $key);
        $this->assertSame($key, $this->idempotencyKey($milestone, $targetAt));

        $milestone->reminder_generation = 4;
        $this->assertNotSame($key, $this->idempotencyKey($milestone, $targetAt));
    }
}
