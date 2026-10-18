<?php

namespace Tests\Feature;

use App\Exceptions\ApiException;
use App\Models\Project;
use App\Models\ProjectMilestone;
use App\Models\ProjectTask;
use App\Models\User;
use App\Module\ProjectMilestoneService;
use Illuminate\Foundation\Testing\DatabaseTransactions;
use Tests\Feature\Concerns\ProjectPlanningFixture;
use Tests\TestCase;

class ProjectMilestoneFeatureTest extends TestCase
{
    use DatabaseTransactions;
    use ProjectPlanningFixture;

    private User $owner;
    private User $member;
    private User $stranger;
    private Project $project;

    protected function setUp(): void
    {
        parent::setUp();
        $this->bootPlanning();
        $this->owner = $this->planningUser('milestone_owner');
        $this->member = $this->planningUser('milestone_member');
        $this->stranger = $this->planningUser('milestone_stranger');
        $this->planningLogin($this->owner);
        $this->project = $this->planningProject($this->owner, [], [$this->member]);
    }

    protected function tearDown(): void
    {
        $this->shutdownPlanning();
        parent::tearDown();
    }

    private function saveMilestone(array $overrides = []): ProjectMilestone
    {
        $data = array_merge([
            'milestoneId' => 0,
            'name' => 'Release 1.0',
            'description' => '',
            'targetAt' => null,
            'status' => 'open',
            'reminderDays' => -1,
            'ownerUserid' => null,
        ], $overrides);

        return ProjectMilestoneService::save(
            $this->project->id,
            $data['milestoneId'],
            $data['name'],
            $data['description'],
            $data['targetAt'],
            $data['status'],
            $data['reminderDays'],
            $data['ownerUserid']
        );
    }

    public function test_creates_milestone_with_project_owner_permissions(): void
    {
        $milestone = $this->saveMilestone(['targetAt' => '2026-06-30']);

        $this->assertSame('Release 1.0', $milestone->name);
        $this->assertSame('open', $milestone->status);
        $this->assertSame('2026-06-30', $milestone->target_at);
        $this->assertSame($this->project->id, (int)$milestone->project_id);

        $lists = ProjectMilestoneService::lists($this->project->id);
        $this->assertTrue($lists['can_manage']);
        $this->assertCount(1, $lists['milestones']);
        $this->assertSame(0, $lists['milestones'][0]['task_count']);
    }

    public function test_name_length_is_bounded(): void
    {
        $this->expectException(ApiException::class);
        $this->saveMilestone(['name' => str_repeat('名', 101)]);
    }

    public function test_description_length_is_bounded(): void
    {
        $this->expectException(ApiException::class);
        $this->saveMilestone(['description' => str_repeat('说', 501)]);
    }

    public function test_status_enum_is_validated(): void
    {
        $this->expectException(ApiException::class);
        $this->saveMilestone(['status' => 'done']);
    }

    public function test_reminder_requires_a_target_date(): void
    {
        $this->expectException(ApiException::class);
        $this->saveMilestone(['reminderDays' => 1]);
    }

    public function test_reminder_offsets_are_restricted(): void
    {
        $this->expectException(ApiException::class);
        $this->saveMilestone(['targetAt' => '2026-06-30', 'reminderDays' => 2]);
    }

    public function test_target_date_must_be_a_real_calendar_date(): void
    {
        $this->expectException(ApiException::class);
        $this->saveMilestone(['targetAt' => '2026-02-30']);
    }

    public function test_owner_must_be_a_project_member(): void
    {
        $this->expectException(ApiException::class);
        $this->saveMilestone(['ownerUserid' => $this->stranger->userid]);
    }

    public function test_member_may_be_assigned_as_owner(): void
    {
        $milestone = $this->saveMilestone(['ownerUserid' => $this->member->userid]);

        $this->assertSame($this->member->userid, (int)$milestone->owner_userid);
    }

    public function test_repeated_identical_save_does_not_restart_the_reminder_cycle(): void
    {
        $milestone = $this->saveMilestone(['targetAt' => '2026-06-30', 'reminderDays' => 3]);
        $generation = (int)$milestone->fresh()->reminder_generation;

        $this->saveMilestone([
            'milestoneId' => (int)$milestone->id,
            'targetAt' => '2026-06-30',
            'reminderDays' => 3,
        ]);
        $this->assertSame($generation, (int)$milestone->fresh()->reminder_generation);

        $this->saveMilestone([
            'milestoneId' => (int)$milestone->id,
            'targetAt' => '2026-07-31',
            'reminderDays' => 3,
        ]);
        $this->assertSame($generation + 1, (int)$milestone->fresh()->reminder_generation);
    }

    public function test_sort_requires_the_complete_milestone_set(): void
    {
        $first = $this->saveMilestone(['name' => 'First']);
        $second = $this->saveMilestone(['name' => 'Second']);

        $this->expectException(ApiException::class);
        ProjectMilestoneService::sort($this->project->id, [(int)$first->id]);
    }

    public function test_sort_reorders_milestones(): void
    {
        $first = $this->saveMilestone(['name' => 'First']);
        $second = $this->saveMilestone(['name' => 'Second']);

        ProjectMilestoneService::sort($this->project->id, [(int)$second->id, (int)$first->id]);

        $names = array_column(ProjectMilestoneService::lists($this->project->id)['milestones'], 'name');
        $this->assertSame(['Second', 'First'], $names);
    }

    public function test_assignment_must_use_a_milestone_from_the_same_project(): void
    {
        $task = $this->planningTask($this->project, ['name' => 'Assigned task']);
        $otherProject = $this->planningProject($this->owner);
        $foreign = $this->planningMilestone($otherProject, ['name' => 'Foreign']);

        $this->expectException(ApiException::class);
        ProjectMilestoneService::assign($task->id, (int)$foreign->id);
    }

    public function test_assign_and_clear_a_task_milestone(): void
    {
        $task = $this->planningTask($this->project, ['name' => 'Assigned task']);
        $milestone = $this->saveMilestone();

        ProjectMilestoneService::assign($task->id, (int)$milestone->id);
        $this->assertSame((int)$milestone->id, (int)$task->fresh()->milestone_id);

        ProjectMilestoneService::assign($task->id, 0);
        $this->assertNull($task->fresh()->milestone_id);
    }

    public function test_deleting_a_milestone_unlinks_its_tasks(): void
    {
        $task = $this->planningTask($this->project, ['name' => 'Assigned task']);
        $milestone = $this->saveMilestone();
        ProjectMilestoneService::assign($task->id, (int)$milestone->id);

        ProjectMilestoneService::delete($this->project->id, (int)$milestone->id);

        $this->assertSame(0, ProjectMilestone::query()->where('project_id', $this->project->id)->count());
        $this->assertNull($task->fresh()->milestone_id);
    }

    public function test_member_cannot_create_or_delete_milestones(): void
    {
        $milestone = $this->saveMilestone();
        $this->planningLogin($this->member);

        $lists = ProjectMilestoneService::lists($this->project->id);
        $this->assertFalse($lists['can_manage']);

        try {
            $this->saveMilestone(['name' => 'Member attempt']);
            $this->fail('A plain member must not create milestones.');
        } catch (ApiException $exception) {
            $this->assertStringContainsString('仅项目负责人和管理员', $exception->getMessage());
        }

        $this->expectException(ApiException::class);
        ProjectMilestoneService::delete($this->project->id, (int)$milestone->id);
    }

    public function test_statistics_ignore_archived_tasks_and_count_completion(): void
    {
        $milestone = $this->saveMilestone();
        $open = $this->planningTask($this->project, ['name' => 'Open task']);
        $done = $this->planningTask($this->project, ['name' => 'Done task', 'complete_at' => now()]);
        $archived = $this->planningTask($this->project, ['name' => 'Archived task']);
        foreach ([$open, $done, $archived] as $task) {
            ProjectMilestoneService::assign($task->id, (int)$milestone->id);
        }
        // Archiving is only allowed after the task is linked; a linked task that is
        // archived afterwards must disappear from the milestone statistics.
        $archived->archived_at = now();
        $archived->save();

        $row = ProjectMilestoneService::lists($this->project->id)['milestones'][0];

        $this->assertSame(2, $row['task_count']);
        $this->assertSame(1, $row['completed_count']);
        $this->assertSame(50, $row['progress_percent']);
    }

    public function test_overdue_tasks_are_counted(): void
    {
        $milestone = $this->saveMilestone();
        $overdue = $this->planningTask($this->project, [
            'name' => 'Overdue task',
            'start_at' => '2026-01-01 09:00:00',
            'end_at' => '2026-01-02 18:00:00',
        ]);
        ProjectMilestoneService::assign($overdue->id, (int)$milestone->id);

        $row = ProjectMilestoneService::lists($this->project->id)['milestones'][0];

        $this->assertSame(1, $row['overdue_count']);
    }

    public function test_private_tasks_are_not_counted_for_other_members(): void
    {
        $milestone = $this->saveMilestone();
        $private = $this->planningPrivateTask($this->project, $this->owner);
        ProjectMilestoneService::assign($private->id, (int)$milestone->id);

        $this->planningLogin($this->member);
        $row = ProjectMilestoneService::lists($this->project->id)['milestones'][0];

        $this->assertSame(0, $row['task_count']);
    }

    public function test_reminder_attempt_log_is_owner_only(): void
    {
        $milestone = $this->saveMilestone();

        $page = ProjectMilestoneService::reminderAttempts($this->project->id, (int)$milestone->id);
        $this->assertSame(0, $page['total']);

        $this->planningLogin($this->member);
        $this->expectException(ApiException::class);
        ProjectMilestoneService::reminderAttempts($this->project->id, (int)$milestone->id);
    }
}
