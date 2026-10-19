<?php

namespace Tests\Feature;

use App\Exceptions\ApiException;
use App\Models\Project;
use App\Models\ProjectTask;
use App\Models\ProjectTaskDependency;
use App\Models\User;
use App\Module\ProjectTaskDependencyService;
use Illuminate\Foundation\Testing\DatabaseTransactions;
use Tests\Feature\Concerns\ProjectPlanningFixture;
use Tests\TestCase;

class ProjectTaskDependencyFeatureTest extends TestCase
{
    use DatabaseTransactions;
    use ProjectPlanningFixture;

    private User $owner;
    private User $member;
    private User $stranger;
    private Project $project;
    private ProjectTask $taskA;
    private ProjectTask $taskB;

    protected function setUp(): void
    {
        parent::setUp();
        $this->bootPlanning();
        $this->owner = $this->planningUser('dependency_owner');
        $this->member = $this->planningUser('dependency_member');
        $this->stranger = $this->planningUser('dependency_stranger');
        $this->planningLogin($this->owner);
        $this->project = $this->planningProject($this->owner, [], [$this->member]);
        $this->taskA = $this->planningTask($this->project, ['name' => 'Task A']);
        $this->taskB = $this->planningTask($this->project, ['name' => 'Task B']);
    }

    protected function tearDown(): void
    {
        $this->shutdownPlanning();
        parent::tearDown();
    }

    private function edgeCount(): int
    {
        return ProjectTaskDependency::query()->where('project_id', $this->project->id)->count();
    }

    public function test_predecessor_direction_creates_finish_to_start_edge(): void
    {
        $dependency = ProjectTaskDependencyService::add($this->taskA->id, $this->taskB->id, 'predecessor');

        $this->assertSame($this->taskB->id, (int)$dependency->predecessor_task_id);
        $this->assertSame($this->taskA->id, (int)$dependency->successor_task_id);
        $this->assertSame($this->project->id, (int)$dependency->project_id);

        $lists = ProjectTaskDependencyService::lists($this->taskA->id);
        $this->assertCount(1, $lists['list']);
        $this->assertSame('predecessor', $lists['list'][0]['direction']);
        $this->assertSame($this->taskB->id, $lists['list'][0]['task']['id']);
    }

    public function test_successor_direction_is_the_inverse_edge(): void
    {
        $dependency = ProjectTaskDependencyService::add($this->taskA->id, $this->taskB->id, 'successor');

        $this->assertSame($this->taskA->id, (int)$dependency->predecessor_task_id);
        $this->assertSame($this->taskB->id, (int)$dependency->successor_task_id);

        $lists = ProjectTaskDependencyService::lists($this->taskA->id);
        $this->assertSame('successor', $lists['list'][0]['direction']);
        $this->assertSame($this->taskB->id, $lists['list'][0]['task']['id']);
    }

    public function test_unknown_direction_is_rejected(): void
    {
        $this->expectException(ApiException::class);
        ProjectTaskDependencyService::add($this->taskA->id, $this->taskB->id, 'blocked');
    }

    public function test_self_dependency_is_rejected(): void
    {
        $this->expectException(ApiException::class);
        ProjectTaskDependencyService::add($this->taskA->id, $this->taskA->id, 'predecessor');
    }

    public function test_cross_project_dependency_is_rejected(): void
    {
        $otherProject = $this->planningProject($this->owner);
        $foreignTask = $this->planningTask($otherProject, ['name' => 'Foreign task']);

        $this->expectException(ApiException::class);
        ProjectTaskDependencyService::add($this->taskA->id, $foreignTask->id, 'predecessor');
    }

    public function test_subtask_cannot_be_part_of_a_dependency(): void
    {
        $subtask = $this->planningTask($this->project, ['name' => 'Subtask', 'parent_id' => $this->taskA->id]);

        $this->expectException(ApiException::class);
        ProjectTaskDependencyService::add($this->taskA->id, $subtask->id, 'predecessor');
    }

    public function test_duplicate_edge_is_idempotent(): void
    {
        $first = ProjectTaskDependencyService::add($this->taskA->id, $this->taskB->id, 'predecessor');
        $second = ProjectTaskDependencyService::add($this->taskA->id, $this->taskB->id, 'predecessor');

        $this->assertSame((int)$first->id, (int)$second->id);
        $this->assertSame(1, $this->edgeCount());
    }

    public function test_dependency_cycle_is_rejected(): void
    {
        $taskC = $this->planningTask($this->project, ['name' => 'Task C']);
        ProjectTaskDependencyService::add($this->taskA->id, $this->taskB->id, 'predecessor');
        ProjectTaskDependencyService::add($this->taskB->id, $taskC->id, 'predecessor');

        try {
            ProjectTaskDependencyService::add($taskC->id, $this->taskA->id, 'predecessor');
            $this->fail('A cycle must not be saved.');
        } catch (ApiException $exception) {
            $this->assertStringContainsString('循环', $exception->getMessage());
        }
        $this->assertSame(2, $this->edgeCount());
    }

    public function test_reverse_edge_of_an_existing_dependency_is_rejected(): void
    {
        ProjectTaskDependencyService::add($this->taskA->id, $this->taskB->id, 'predecessor');

        $this->expectException(ApiException::class);
        ProjectTaskDependencyService::add($this->taskB->id, $this->taskA->id, 'predecessor');
    }

    public function test_disabled_project_returns_empty_lists_and_rejects_writes(): void
    {
        ProjectTaskDependencyService::add($this->taskA->id, $this->taskB->id, 'predecessor');

        $this->project->task_dependency = 'close';
        $this->project->save();

        $lists = ProjectTaskDependencyService::lists($this->taskA->id);
        $this->assertSame([], $lists['list']);

        $this->expectException(ApiException::class);
        ProjectTaskDependencyService::add($this->taskA->id, $this->taskB->id, 'successor');
    }

    public function test_non_member_cannot_add_dependency(): void
    {
        $this->planningLogin($this->stranger);

        $this->expectException(ApiException::class);
        ProjectTaskDependencyService::add($this->taskA->id, $this->taskB->id, 'predecessor');
    }

    public function test_private_related_task_is_not_disclosed_in_lists(): void
    {
        ProjectTaskDependencyService::add($this->taskA->id, $this->taskB->id, 'predecessor');
        $this->taskB->visibility = 0;
        $this->taskB->save();

        $this->planningLogin($this->member);
        $lists = ProjectTaskDependencyService::lists($this->taskA->id);

        $this->assertSame([], $lists['list']);
    }

    public function test_delete_requires_the_edge_to_touch_the_given_task(): void
    {
        $dependency = ProjectTaskDependencyService::add($this->taskA->id, $this->taskB->id, 'predecessor');
        $taskC = $this->planningTask($this->project, ['name' => 'Task C']);

        $this->expectException(ApiException::class);
        ProjectTaskDependencyService::delete($taskC->id, $dependency->id);
    }

    public function test_delete_removes_the_edge_from_both_sides(): void
    {
        $dependency = ProjectTaskDependencyService::add($this->taskA->id, $this->taskB->id, 'predecessor');
        ProjectTaskDependencyService::delete($this->taskB->id, $dependency->id);

        $this->assertSame(0, $this->edgeCount());
        $this->assertSame([], ProjectTaskDependencyService::lists($this->taskA->id)['list']);
    }

    public function test_cross_project_move_drops_edges_that_leave_the_task_tree(): void
    {
        $otherProject = $this->planningProject($this->owner);
        ProjectTaskDependencyService::add($this->taskA->id, $this->taskB->id, 'predecessor');
        $this->assertSame(1, $this->edgeCount());

        // Only one side of the edge moves: the relation must not survive across projects.
        $this->taskA->project_id = $otherProject->id;
        $this->taskA->save();
        ProjectTaskDependencyService::handleProjectMove($this->project->id, $otherProject->id, [$this->taskA->id]);

        $this->assertSame(0, $this->edgeCount());
        $this->assertSame(0, ProjectTaskDependency::query()->where('project_id', $otherProject->id)->count());
    }

    public function test_cross_project_move_keeps_edges_inside_the_moved_tree(): void
    {
        $otherProject = $this->planningProject($this->owner);
        $dependency = ProjectTaskDependencyService::add($this->taskA->id, $this->taskB->id, 'predecessor');

        foreach ([$this->taskA, $this->taskB] as $task) {
            $task->project_id = $otherProject->id;
            $task->save();
        }
        ProjectTaskDependencyService::handleProjectMove(
            $this->project->id,
            $otherProject->id,
            [$this->taskA->id, $this->taskB->id]
        );

        $moved = ProjectTaskDependency::query()->whereKey($dependency->id)->first();
        $this->assertNotNull($moved);
        $this->assertSame($otherProject->id, (int)$moved->project_id);
        $this->assertSame(0, $this->edgeCount());
    }

    public function test_risk_summary_reports_a_schedule_conflict(): void
    {
        $predecessor = $this->planningTask($this->project, [
            'name' => 'Predecessor',
            'start_at' => '2027-03-01 09:00:00',
            'end_at' => '2027-03-10 18:00:00',
        ]);
        $successor = $this->planningTask($this->project, [
            'name' => 'Successor',
            'start_at' => '2027-03-05 09:00:00',
            'end_at' => '2027-03-06 18:00:00',
        ]);
        // add($successor, $predecessor, 'predecessor'): predecessor blocks successor.
        ProjectTaskDependencyService::add($successor->id, $predecessor->id, 'predecessor');

        $risks = ProjectTaskDependencyService::riskSummary($this->project->id);

        $this->assertTrue($risks['enabled']);
        $this->assertSame(1, $risks['schedule_conflicts']);
        $this->assertSame(0, $risks['overdue_blockers']);
        $this->assertSame(1, $risks['total_risk_edges']);
        $this->assertContains('schedule_conflict', $risks['items'][0]['types']);
        $this->assertSame($successor->id, $risks['items'][0]['successor']['id']);
    }

    public function test_risk_summary_reports_an_overdue_blocker(): void
    {
        $predecessor = $this->planningTask($this->project, [
            'name' => 'Late predecessor',
            'start_at' => now()->subDays(5)->toDateTimeString(),
            'end_at' => now()->subDays(2)->toDateTimeString(),
        ]);
        $successor = $this->planningTask($this->project, [
            'name' => 'Waiting successor',
            'start_at' => now()->addDays(2)->toDateTimeString(),
            'end_at' => now()->addDays(4)->toDateTimeString(),
        ]);
        ProjectTaskDependencyService::add($successor->id, $predecessor->id, 'predecessor');

        $risks = ProjectTaskDependencyService::riskSummary($this->project->id);

        $this->assertSame(0, $risks['schedule_conflicts']);
        $this->assertSame(1, $risks['overdue_blockers']);
        $this->assertContains('overdue_blocker', $risks['items'][0]['types']);
    }

    public function test_risk_summary_skips_edges_with_tasks_the_caller_cannot_see(): void
    {
        $predecessor = $this->planningTask($this->project, [
            'name' => 'Visible predecessor',
            'start_at' => now()->subDays(5)->toDateTimeString(),
            'end_at' => now()->subDays(2)->toDateTimeString(),
        ]);
        $hiddenSuccessor = $this->planningPrivateTask($this->project, $this->owner);
        $hiddenSuccessor->start_at = now()->subDays(4)->toDateTimeString();
        $hiddenSuccessor->end_at = now()->addDays(2)->toDateTimeString();
        $hiddenSuccessor->save();
        ProjectTaskDependencyService::add($hiddenSuccessor->id, $predecessor->id, 'predecessor');

        $asOwner = ProjectTaskDependencyService::riskSummary($this->project->id);
        $this->assertSame(1, $asOwner['total_risk_edges']);

        $this->planningLogin($this->member);
        $asMember = ProjectTaskDependencyService::riskSummary($this->project->id);
        $this->assertSame(0, $asMember['total_risk_edges']);
        $this->assertSame([], $asMember['items']);
    }

    public function test_gantt_dependency_query_returns_edges_and_excludes_foreign_tasks(): void
    {
        $dependency = ProjectTaskDependencyService::add($this->taskB->id, $this->taskA->id, 'predecessor');
        $otherProject = $this->planningProject($this->owner);
        $foreignTask = $this->planningTask($otherProject, ['name' => 'Foreign task']);

        $edges = ProjectTaskDependencyService::projectLists(
            $this->project->id,
            [$this->taskA->id, $this->taskB->id, $foreignTask->id]
        );

        $this->assertCount(1, $edges);
        $this->assertSame((int)$dependency->id, $edges[0]['id']);
        $this->assertSame($this->taskA->id, $edges[0]['predecessor']['id']);
        $this->assertSame($this->taskB->id, $edges[0]['successor']['id']);
    }

    public function test_gantt_dependency_query_returns_nothing_when_the_project_disables_dependencies(): void
    {
        ProjectTaskDependencyService::add($this->taskB->id, $this->taskA->id, 'predecessor');
        $this->project->task_dependency = 'close';
        $this->project->save();

        $edges = ProjectTaskDependencyService::projectLists($this->project->id, [$this->taskA->id, $this->taskB->id]);

        $this->assertSame([], $edges);
    }
}
