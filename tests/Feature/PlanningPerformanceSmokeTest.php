<?php

namespace Tests\Feature;

use App\Models\Project;
use App\Models\ProjectTask;
use App\Models\ProjectTaskDependency;
use App\Models\User;
use App\Module\ProjectTaskDependencyService;
use App\Module\ProjectTaskScheduleService;
use App\Module\ProjectTaskWorkService;
use Illuminate\Foundation\Testing\DatabaseTransactions;
use Illuminate\Support\Facades\DB;
use Tests\Feature\Concerns\ProjectPlanningFixture;
use Tests\TestCase;

/**
 * Opt-in performance smoke test (DOOTASK_PERF_SMOKE=1). It seeds a project with a
 * long dependency chain and reports timings + query counts for the planning queries
 * that previously had no baseline. Bounds are deliberately loose: the goal is to
 * catch algorithmic regressions (N+1, accidental full scans), not to grade hardware.
 */
class PlanningPerformanceSmokeTest extends TestCase
{
    use DatabaseTransactions;
    use ProjectPlanningFixture;

    private const TASK_COUNT = 200;

    private User $owner;
    private Project $project;

    protected function setUp(): void
    {
        parent::setUp();
        if (getenv('DOOTASK_PERF_SMOKE') !== '1') {
            $this->markTestSkipped('设置 DOOTASK_PERF_SMOKE=1 才运行性能冒烟');
        }
        $this->bootPlanning();
        $this->owner = $this->planningUser('perf_owner');
        $this->planningLogin($this->owner);
        $this->project = $this->planningProject($this->owner, [
            'task_schedule_mode' => 'automatic',
            'task_schedule_timezone' => 'PRC',
            'task_schedule_workdays' => json_encode([1, 2, 3, 4, 5]),
        ]);
    }

    protected function tearDown(): void
    {
        $this->shutdownPlanning();
        parent::tearDown();
    }

    /**
     * @return array{tasks: ProjectTask[], edges: int}
     */
    private function seedChain(): array
    {
        $tasks = [];
        $start = \Carbon\Carbon::parse('2027-01-04 09:00:00');
        for ($i = 0; $i < self::TASK_COUNT; $i++) {
            $day = $start->copy()->addDays($i);
            $task = $this->planningTask($this->project, [
                'name' => 'Perf task ' . $i,
                'start_at' => $day->toDateTimeString(),
                'end_at' => $day->copy()->setTime(18, 0)->toDateTimeString(),
            ], $this->owner);
            $tasks[] = $task;
            if ($i > 0) {
                // Insert edges directly: the service path adds audit logs and is slow to seed.
                ProjectTaskDependency::createInstance([
                    'project_id' => $this->project->id,
                    'predecessor_task_id' => $tasks[$i - 1]->id,
                    'successor_task_id' => $task->id,
                    'created_by' => $this->owner->userid,
                ])->save();
            }
        }
        return ['tasks' => $tasks, 'edges' => count($tasks) - 1];
    }

    /**
     * @return array{seconds: float, queries: int, result: mixed}
     */
    private function measure(callable $callback): array
    {
        $queries = 0;
        $active = true;
        DB::listen(function () use (&$queries, &$active) {
            if ($active) {
                $queries++;
            }
        });
        $start = microtime(true);
        try {
            $result = $callback();
        } finally {
            $elapsed = microtime(true) - $start;
            $active = false;
        }
        return ['seconds' => $elapsed, 'queries' => $queries, 'result' => $result];
    }

    public function test_planning_queries_have_a_baseline(): void
    {
        $seedStart = microtime(true);
        $seed = $this->seedChain();
        $seedSeconds = microtime(true) - $seedStart;
        $anchor = $seed['tasks'][0];
        $now = \Carbon\Carbon::now();

        $preview = $this->measure(fn () => ProjectTaskScheduleService::preview(
            $this->project->id,
            $anchor->id,
            '2027-01-05 09:00:00',
            '2027-01-05 18:00:00'
        ));
        $capacity = $this->measure(fn () => ProjectTaskWorkService::capacity(
            $this->project->id,
            $now->copy()->subDays(1)->toDateString(),
            $now->copy()->addDays(28)->toDateString()
        ));
        $risks = $this->measure(fn () => ProjectTaskDependencyService::riskSummary($this->project->id));
        $taskIds = array_map(fn (ProjectTask $task) => $task->id, $seed['tasks']);
        $edges = $this->measure(fn () => ProjectTaskDependencyService::projectLists($this->project->id, $taskIds));

        $report = [
            'tasks' => self::TASK_COUNT,
            'edges' => $seed['edges'],
            'seed_seconds' => round($seedSeconds, 2),
            'preview' => round($preview['seconds'], 3),
            'preview_queries' => $preview['queries'],
            'capacity' => round($capacity['seconds'], 3),
            'capacity_queries' => $capacity['queries'],
            'risk_summary' => round($risks['seconds'], 3),
            'risk_summary_queries' => $risks['queries'],
            'gantt_lists' => round($edges['seconds'], 3),
            'gantt_lists_queries' => $edges['queries'],
            'preview_changes' => count($preview['result']['changes'] ?? []),
            'gantt_edges' => count($edges['result']),
        ];
        fwrite(STDERR, "\nPERF_SMOKE=" . json_encode($report, JSON_UNESCAPED_UNICODE) . "\n");

        // Loose guards: a chain of 200 must not behave like O(n^2) or query per row.
        $this->assertLessThan(10.0, $preview['seconds'], 'preview');
        $this->assertLessThan(2000, $preview['queries'], 'preview queries');
        $this->assertLessThan(5.0, $capacity['seconds'], 'capacity');
        $this->assertLessThan(500, $capacity['queries'], 'capacity queries');
        $this->assertLessThan(5.0, $risks['seconds'], 'risk summary');
        $this->assertLessThan(500, $risks['queries'], 'risk summary queries');
        $this->assertLessThan(5.0, $edges['seconds'], 'gantt lists');
        $this->assertLessThan(200, $edges['queries'], 'gantt lists queries');
    }
}
