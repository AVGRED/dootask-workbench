<?php

namespace Tests\Feature;

use App\Exceptions\ApiException;
use App\Models\Project;
use App\Models\User;
use App\Module\ProjectTaskViewService;
use Illuminate\Foundation\Testing\DatabaseTransactions;
use Tests\Feature\Concerns\ProjectPlanningFixture;
use Tests\TestCase;

class ProjectTaskViewFeatureTest extends TestCase
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
        $this->owner = $this->planningUser('view_owner');
        $this->member = $this->planningUser('view_member');
        $this->planningLogin($this->owner);
        $this->project = $this->planningProject($this->owner, [], [$this->member]);
    }

    protected function tearDown(): void
    {
        $this->shutdownPlanning();
        parent::tearDown();
    }

    private function saveView(string $name, string $visibility = 'personal', array $definition = [], int $viewId = 0): array
    {
        return ProjectTaskViewService::save($this->project->id, $viewId, $name, $visibility, $definition);
    }

    public function test_personal_view_is_visible_only_to_its_creator(): void
    {
        $this->saveView('My filter');

        $this->assertCount(1, ProjectTaskViewService::lists($this->project->id));

        $this->planningLogin($this->member);
        $this->assertCount(0, ProjectTaskViewService::lists($this->project->id));
    }

    public function test_project_owner_can_share_a_view_and_members_can_read_it(): void
    {
        $this->saveView('Team filter', 'project');

        $this->planningLogin($this->member);
        $lists = ProjectTaskViewService::lists($this->project->id);

        $this->assertCount(1, $lists);
        $this->assertSame('project', $lists[0]['visibility']);
        $this->assertFalse($lists[0]['can_edit']);
    }

    public function test_member_cannot_create_a_shared_view(): void
    {
        $this->planningLogin($this->member);

        $this->expectException(ApiException::class);
        $this->saveView('Member shared', 'project');
    }

    public function test_member_cannot_modify_a_shared_view(): void
    {
        $shared = $this->saveView('Team filter', 'project');
        $this->planningLogin($this->member);

        $this->expectException(ApiException::class);
        $this->saveView('Hijacked', 'project', [], (int)$shared['id']);
    }

    public function test_member_cannot_delete_a_shared_view(): void
    {
        $shared = $this->saveView('Team filter', 'project');
        $this->planningLogin($this->member);

        $this->expectException(ApiException::class);
        ProjectTaskViewService::delete($this->project->id, (int)$shared['id']);
    }

    public function test_other_members_cannot_read_or_delete_a_personal_view(): void
    {
        $personal = $this->saveView('Private filter');
        $this->planningLogin($this->member);

        $this->expectException(ApiException::class);
        ProjectTaskViewService::delete($this->project->id, (int)$personal['id']);
    }

    public function test_view_name_is_required_and_bounded(): void
    {
        $this->expectException(ApiException::class);
        $this->saveView('   ');
    }

    public function test_visibility_enum_is_validated(): void
    {
        $this->expectException(ApiException::class);
        $this->saveView('Bad scope', 'global');
    }

    public function test_definition_is_normalized_before_storage(): void
    {
        $saved = $this->saveView('Normalized', 'personal', [
            'tab' => 'weird',
            'sort' => ['field' => 'nope', 'type' => 'sideways'],
            'table_groups' => ['my', 'bogus', 'my'],
            'table_fields' => ['owner', 'bogus', 'due'],
            'date_range' => ['from' => '2026-02-30', 'to' => '2026-03-01'],
            'keyword' => str_repeat('k', 150),
        ]);

        $definition = $saved['definition'];
        $this->assertSame('column', $definition['tab']);
        $this->assertSame(['field' => 'end_at', 'type' => 'desc'], $definition['sort']);
        $this->assertSame(['my'], $definition['table_groups']);
        $this->assertSame(['owner', 'due'], $definition['table_fields']);
        $this->assertSame([], $definition['date_range']);
        $this->assertSame(100, mb_strlen($definition['keyword']));
    }

    public function test_invalid_sort_direction_falls_back_to_desc(): void
    {
        $saved = $this->saveView('Sort fallback', 'personal', [
            'sort' => ['field' => 'level', 'type' => 'sideways'],
        ]);

        $this->assertSame(['field' => 'level', 'type' => 'desc'], $saved['definition']['sort']);
    }

    public function test_valid_date_range_survives_and_reversed_ranges_are_dropped(): void
    {
        $valid = $this->saveView('Valid range', 'personal', [
            'date_range' => ['from' => '2026-03-01', 'to' => '2026-03-31'],
        ]);
        $this->assertSame(['from' => '2026-03-01', 'to' => '2026-03-31'], $valid['definition']['date_range']);

        $reversed = $this->saveView('Reversed range', 'personal', [
            'date_range' => ['from' => '2026-03-31', 'to' => '2026-03-01'],
        ]);
        $this->assertSame([], $reversed['definition']['date_range']);
    }

    public function test_milestone_filter_must_belong_to_the_project(): void
    {
        $otherProject = $this->planningProject($this->owner);
        $foreign = $this->planningMilestone($otherProject, ['name' => 'Foreign']);

        $this->expectException(ApiException::class);
        $this->saveView('Foreign milestone', 'personal', ['milestone_id' => (int)$foreign->id]);
    }

    public function test_own_milestone_filter_is_accepted(): void
    {
        $milestone = $this->planningMilestone($this->project, ['name' => 'Local']);

        $saved = $this->saveView('Local milestone', 'personal', ['milestone_id' => (int)$milestone->id]);

        $this->assertSame((int)$milestone->id, $saved['definition']['milestone_id']);
    }

    public function test_default_view_is_per_user_and_requires_visibility(): void
    {
        $personal = $this->saveView('Private filter');
        ProjectTaskViewService::setDefault($this->project->id, (int)$personal['id']);

        $lists = ProjectTaskViewService::lists($this->project->id);
        $this->assertTrue($lists[0]['is_default']);

        $this->planningLogin($this->member);
        $this->assertCount(0, ProjectTaskViewService::lists($this->project->id));

        $this->expectException(ApiException::class);
        ProjectTaskViewService::setDefault($this->project->id, (int)$personal['id']);
    }

    public function test_updating_an_own_view_replaces_its_definition(): void
    {
        $created = $this->saveView('First name', 'personal', ['keyword' => 'alpha']);
        $updated = $this->saveView('Second name', 'personal', ['keyword' => 'beta'], (int)$created['id']);

        $this->assertSame((int)$created['id'], (int)$updated['id']);
        $this->assertSame('Second name', $updated['name']);
        $this->assertSame('beta', $updated['definition']['keyword']);
    }

    public function test_non_member_cannot_read_views(): void
    {
        $stranger = $this->planningUser('view_stranger');
        $this->planningLogin($stranger);

        $this->expectException(ApiException::class);
        ProjectTaskViewService::lists($this->project->id);
    }
}
