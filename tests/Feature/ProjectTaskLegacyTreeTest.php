<?php

namespace Tests\Feature;

use App\Exceptions\ApiException;
use App\Models\Project;
use App\Models\ProjectTask;
use App\Models\User;
use Illuminate\Foundation\Testing\DatabaseTransactions;
use Tests\Feature\Concerns\ProjectPlanningFixture;
use Tests\TestCase;

/**
 * Data written before the five-level / fifty-subtask creation rules can be deeper
 * or wider. Traversal, copy and move must keep working for those trees instead of
 * failing on the new limits.
 */
class ProjectTaskLegacyTreeTest extends TestCase
{
    use DatabaseTransactions;
    use ProjectPlanningFixture;

    private User $owner;
    private Project $project;

    protected function setUp(): void
    {
        parent::setUp();
        $this->bootPlanning();
        $this->owner = $this->planningUser('legacy_owner');
        $this->planningLogin($this->owner);
        $this->project = $this->planningProject($this->owner);
    }

    protected function tearDown(): void
    {
        $this->shutdownPlanning();
        parent::tearDown();
    }

    /**
     * Build a chain of $depth tasks below a new root without going through the
     * creation guards (models are written directly, like pre-upgrade rows).
     *
     * @return array{0: ProjectTask, 1: ProjectTask}
     */
    private function seedDeepChain(int $depth): array
    {
        $root = $this->planningTask($this->project, ['name' => 'Legacy root']);
        $parent = $root;
        for ($level = 1; $level <= $depth; $level++) {
            $parent = $this->planningTask($this->project, [
                'name' => 'Legacy level ' . $level,
                'parent_id' => $parent->id,
            ]);
        }
        return [$root, $parent];
    }

    public function test_descendants_of_a_six_level_tree_are_still_traversable(): void
    {
        [$root, $deepest] = $this->seedDeepChain(6);

        $descendants = $root->subTaskDescendants();

        $this->assertCount(6, $descendants);
        $this->assertContains($deepest->id, $descendants->pluck('id')->all());
    }

    public function test_creation_still_rejects_a_seventh_level(): void
    {
        [, $deepest] = $this->seedDeepChain(6);

        $this->expectException(ApiException::class);
        ProjectTask::assertSubtaskCanBeAdded($deepest);
    }

    public function test_copy_keeps_working_for_legacy_trees_with_more_than_fifty_descendants(): void
    {
        $root = $this->planningTask($this->project, ['name' => 'Wide root']);
        for ($i = 1; $i <= 55; $i++) {
            $this->planningTask($this->project, [
                'name' => 'Child ' . $i,
                'parent_id' => $root->id,
            ]);
        }

        $this->assertCount(55, $root->subTaskDescendants());

        $copy = $root->copyTask();

        $this->assertNotNull($copy);
        $this->assertNotSame($root->id, $copy->id);
    }
}
