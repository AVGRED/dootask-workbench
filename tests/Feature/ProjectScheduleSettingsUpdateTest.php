<?php

namespace Tests\Feature;

use App\Http\Controllers\Api\ProjectController;
use App\Models\Project;
use App\Models\User;
use Illuminate\Foundation\Testing\DatabaseTransactions;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Request as RequestFacade;
use Tests\Feature\Concerns\ProjectPlanningFixture;
use Tests\TestCase;

/**
 * api/project/update carries the whole project form, but the scheduling fields are
 * documented as optional. A client may send only the field it changes; the rest must
 * keep their stored values instead of failing validation.
 */
class ProjectScheduleSettingsUpdateTest extends TestCase
{
    use DatabaseTransactions;
    use ProjectPlanningFixture;

    private User $owner;
    private Project $project;

    protected function setUp(): void
    {
        parent::setUp();
        $this->bootPlanning();
        $this->owner = $this->planningUser('schedule_update_owner');
        $this->planningLogin($this->owner);
        $this->project = $this->planningProject($this->owner, [
            'task_schedule_mode' => 'manual',
            'task_schedule_timezone' => 'Asia/Shanghai',
            'task_schedule_workdays' => json_encode([1, 2, 3]),
            'task_schedule_buffer_days' => 2,
            'task_schedule_holidays' => json_encode(['2026-05-01']),
        ]);
    }

    protected function tearDown(): void
    {
        $this->shutdownPlanning();
        parent::tearDown();
    }

    /**
     * @return array result of ProjectController::update()
     */
    private function updateProject(array $payload): array
    {
        $original = request();
        $request = Request::create(
            '/api/project/update',
            'POST',
            [],
            [],
            [],
            ['CONTENT_TYPE' => 'application/json'],
            json_encode(array_merge([
                'project_id' => $this->project->id,
                'name' => 'Schedule update project',
                'desc' => '',
            ], $payload))
        );
        RequestFacade::swap($request);
        // RequestContext is keyed by the current request object, so the login has to
        // be re-applied after swapping in the synthetic request.
        $this->planningLogin($this->owner);
        try {
            return (new ProjectController())->update();
        } finally {
            RequestFacade::swap($original);
            $this->planningLogin($this->owner);
        }
    }

    public function test_updating_only_the_mode_keeps_the_other_schedule_fields(): void
    {
        $result = $this->updateProject(['task_schedule_mode' => 'automatic']);

        $this->assertSame(1, $result['ret']);
        $project = $this->project->fresh();
        $this->assertSame('automatic', $project->task_schedule_mode);
        $this->assertSame('Asia/Shanghai', $project->task_schedule_timezone);
        $this->assertSame([1, 2, 3], json_decode($project->task_schedule_workdays, true));
        $this->assertSame(2, (int)$project->task_schedule_buffer_days);
        $this->assertSame(['2026-05-01'], json_decode($project->task_schedule_holidays, true));
    }

    public function test_updating_only_holidays_keeps_the_mode_and_calendar(): void
    {
        $result = $this->updateProject(['task_schedule_holidays' => ['2026-05-02']]);

        $this->assertSame(1, $result['ret']);
        $project = $this->project->fresh();
        $this->assertSame('manual', $project->task_schedule_mode);
        $this->assertSame([1, 2, 3], json_decode($project->task_schedule_workdays, true));
        $this->assertSame(['2026-05-02'], json_decode($project->task_schedule_holidays, true));
    }

    public function test_partial_payload_still_rejects_an_invalid_value(): void
    {
        $result = $this->updateProject(['task_schedule_mode' => 'sometimes']);

        $this->assertSame(0, $result['ret']);
        $this->assertSame('manual', $this->project->fresh()->task_schedule_mode);
    }

    public function test_invalid_timezone_is_rejected_even_in_a_partial_payload(): void
    {
        $result = $this->updateProject(['task_schedule_timezone' => 'Mars/Olympus']);

        $this->assertSame(0, $result['ret']);
        $this->assertSame('Asia/Shanghai', $this->project->fresh()->task_schedule_timezone);
    }

    public function test_invalid_workdays_are_rejected(): void
    {
        $result = $this->updateProject(['task_schedule_workdays' => [0, 8]]);

        $this->assertSame(0, $result['ret']);
        $this->assertSame([1, 2, 3], json_decode($this->project->fresh()->task_schedule_workdays, true));
    }
}
