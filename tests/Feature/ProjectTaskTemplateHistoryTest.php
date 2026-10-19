<?php

namespace Tests\Feature;

use App\Http\Controllers\Api\ProjectTaskTemplateController;
use App\Models\Project;
use App\Models\ProjectTaskTemplate;
use App\Models\ProjectTaskTemplateRevision;
use App\Models\User;
use Illuminate\Foundation\Testing\DatabaseTransactions;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Request as RequestFacade;
use Tests\Feature\Concerns\ProjectPlanningFixture;
use Tests\TestCase;

/**
 * Template history is written by the template update flow, but reads and restores
 * go through their own controller. These cases cover the restore invariants:
 * snapshots are immutable, restoring bumps the version, and a snapshot that points
 * at a missing or ambiguous milestone is refused instead of silently dropping it.
 */
class ProjectTaskTemplateHistoryTest extends TestCase
{
    use DatabaseTransactions;
    use ProjectPlanningFixture;

    private User $owner;
    private Project $project;

    protected function setUp(): void
    {
        parent::setUp();
        $this->bootPlanning();
        $this->owner = $this->planningUser('template_history_owner');
        $this->planningLogin($this->owner);
        $this->project = $this->planningProject($this->owner);
    }

    protected function tearDown(): void
    {
        $this->shutdownPlanning();
        parent::tearDown();
    }

    private function template(int $version, string $content, ?string $milestoneName = null): ProjectTaskTemplate
    {
        $template = ProjectTaskTemplate::createInstance([
            'project_id' => $this->project->id,
            'name' => 'Template ' . uniqid(),
            'title' => 'Title',
            'content' => $content,
            'milestone_name' => $milestoneName,
            'sort' => 0,
            'is_default' => 0,
            'userid' => $this->owner->userid,
            'version' => $version,
        ]);
        $template->save();
        return $template;
    }

    private function revision(ProjectTaskTemplate $template, int $version, string $content, ?string $milestoneName = null): ProjectTaskTemplateRevision
    {
        $revision = ProjectTaskTemplateRevision::createInstance([
            'template_id' => $template->id,
            'project_id' => $this->project->id,
            'version' => $version,
            'name' => $template->name,
            'title' => 'Title',
            'content' => $content,
            'milestone_name' => $milestoneName,
            'subtasks' => [],
            'changed_by' => $this->owner->userid,
        ]);
        $revision->save();
        return $revision;
    }

    /**
     * @return array{result?: array, exception?: \Throwable}
     */
    private function callController(string $method, array $payload): array
    {
        $original = request();
        $request = Request::create(
            '/api/projecttasktemplate/' . $method,
            $method === 'revisions' ? 'GET' : 'POST',
            [],
            [],
            [],
            ['CONTENT_TYPE' => 'application/json'],
            json_encode(array_merge(['project_id' => $this->project->id], $payload))
        );
        RequestFacade::swap($request);
        $this->planningLogin($this->owner);
        try {
            $controller = new ProjectTaskTemplateController();
            return ['result' => $controller->{$method}()];
        } catch (\Throwable $e) {
            return ['exception' => $e];
        } finally {
            RequestFacade::swap($original);
            $this->planningLogin($this->owner);
        }
    }

    public function test_revisions_returns_the_current_template_and_history_newest_first(): void
    {
        $template = $this->template(3, 'current content');
        $this->revision($template, 1, 'first content');
        $this->revision($template, 2, 'second content');

        $outcome = $this->callController('revisions', ['template_id' => $template->id]);

        $this->assertArrayHasKey('result', $outcome);
        $data = $outcome['result']['data'];
        $this->assertSame(3, (int)$data['current']['version']);
        $this->assertCount(2, $data['revisions']);
        $this->assertSame(2, (int)$data['revisions']->first()->version);
        $this->assertSame(2, (int)$data['total']);
    }

    public function test_restore_snapshots_the_current_content_and_bumps_the_version(): void
    {
        $template = $this->template(2, 'current content');
        $this->revision($template, 1, 'old content');

        $outcome = $this->callController('restore', ['template_id' => $template->id, 'version' => 1]);

        $fresh = $template->fresh();
        $this->assertSame('old content', $fresh->content);
        $this->assertSame(3, (int)$fresh->version);
        $this->assertSame('current content', (string)ProjectTaskTemplateRevision::query()
            ->where('template_id', $template->id)
            ->where('version', 2)
            ->value('content'));
        if (isset($outcome['result'])) {
            $this->assertSame(1, $outcome['result']['ret']);
        }
    }

    public function test_restore_rejects_an_unknown_version(): void
    {
        $template = $this->template(2, 'current content');

        $outcome = $this->callController('restore', ['template_id' => $template->id, 'version' => 99]);

        $this->assertArrayHasKey('result', $outcome);
        $this->assertSame(0, $outcome['result']['ret']);
        $this->assertStringContainsString('历史版本不存在', $outcome['result']['msg']);
        $this->assertSame(2, (int)$template->fresh()->version);
    }

    public function test_restore_rejects_a_snapshot_whose_milestone_disappeared(): void
    {
        $template = $this->template(2, 'current content');
        $this->revision($template, 1, 'old content', 'Ghost milestone');

        $outcome = $this->callController('restore', ['template_id' => $template->id, 'version' => 1]);

        $this->assertArrayHasKey('result', $outcome);
        $this->assertSame(0, $outcome['result']['ret']);
        $this->assertStringContainsString('里程碑', $outcome['result']['msg']);
        $this->assertSame('current content', $template->fresh()->content);
    }

    public function test_restore_of_identical_content_does_not_create_a_new_version(): void
    {
        $template = $this->template(2, 'same content');
        $this->revision($template, 1, 'same content');

        $outcome = $this->callController('restore', ['template_id' => $template->id, 'version' => 1]);

        $this->assertArrayHasKey('result', $outcome);
        $this->assertSame(1, $outcome['result']['ret']);
        $this->assertSame(2, (int)$template->fresh()->version);
    }
}
