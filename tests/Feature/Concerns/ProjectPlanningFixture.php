<?php

namespace Tests\Feature\Concerns;

use App\Models\Project;
use App\Models\ProjectMilestone;
use App\Models\ProjectTask;
use App\Models\ProjectTaskUser;
use App\Models\ProjectUser;
use App\Models\User;
use App\Module\Interface\DooSo;
use App\Services\RequestContext;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Events\Dispatcher;
use Mockery;

/**
 * Shared fixture for project planning feature tests.
 *
 * Planning services resolve the caller through RequestContext (DooTask runs on
 * LaravelS, so there is no framework auth guard in CLI). These helpers build the
 * minimum rows the permission checks need and mock the Doo interface exactly like
 * ProjectTaskHandoffFeatureTest does.
 */
trait ProjectPlanningFixture
{
    private $planningOriginalDispatcher = null;

    protected function bootPlanning(): void
    {
        config(['cache.default' => 'array']);
        if ($this->planningOriginalDispatcher === null) {
            $this->planningOriginalDispatcher = Model::getEventDispatcher();
        }
        Model::setEventDispatcher(new Dispatcher(app()));
        RequestContext::clean();
    }

    protected function shutdownPlanning(): void
    {
        RequestContext::clean();
        if ($this->planningOriginalDispatcher !== null) {
            Model::setEventDispatcher($this->planningOriginalDispatcher);
            $this->planningOriginalDispatcher = null;
        }
    }

    protected function planningLogin(User $user): void
    {
        RequestContext::clean();
        $doo = Mockery::mock(DooSo::class);
        $doo->shouldReceive('userId')->andReturn($user->userid);
        $doo->shouldReceive('translate')->andReturnUsing(fn ($value) => $value);
        RequestContext::save('doo_instance', $doo);
        RequestContext::save('auth', $user);
    }

    protected function planningUser(string $name, array $attributes = []): User
    {
        $user = User::createInstance(array_merge([
            'email' => uniqid('planning_' . $name . '_') . '@test.local',
            'nickname' => $name,
            'identity' => ['normal'],
            'bot' => 0,
        ], $attributes));
        $user->save();
        return $user;
    }

    /**
     * @param User[] $members
     */
    protected function planningProject(User $owner, array $attributes = [], array $members = []): Project
    {
        $project = Project::createInstance(array_merge([
            'name' => 'Planning ' . uniqid(),
            'department_owner_view' => 'open',
        ], $attributes));
        $project->save();
        $this->planningJoin($project, $owner, 1);
        foreach ($members as $member) {
            $this->planningJoin($project, $member);
        }
        return $project;
    }

    protected function planningJoin(Project $project, User $user, int $owner = 0): void
    {
        ProjectUser::createInstance([
            'project_id' => $project->id,
            'userid' => $user->userid,
            'owner' => $owner,
        ])->save();
    }

    protected function planningTask(Project $project, array $attributes = [], ?User $owner = null): ProjectTask
    {
        $task = ProjectTask::createInstance(array_merge([
            'project_id' => $project->id,
            'parent_id' => 0,
            'name' => 'Task ' . uniqid(),
            'userid' => $owner ? $owner->userid : User::userid(),
            'visibility' => 1,
            'dialog_id' => 0,
        ], $attributes));
        $task->save();
        if ($owner) {
            $this->planningTaskOwner($task, $owner);
        }
        return $task;
    }

    protected function planningTaskOwner(ProjectTask $task, User $user): void
    {
        ProjectTaskUser::createInstance([
            'project_id' => $task->project_id,
            'task_id' => $task->id,
            'task_pid' => $task->id,
            'userid' => $user->userid,
            'owner' => 1,
        ])->save();
    }

    protected function planningMilestone(Project $project, array $attributes = []): ProjectMilestone
    {
        $milestone = ProjectMilestone::createInstance(array_merge([
            'project_id' => $project->id,
            'name' => 'Milestone ' . uniqid(),
            'status' => 'open',
            'reminder_days' => -1,
            'sort' => 0,
        ], $attributes));
        $milestone->save();
        return $milestone;
    }

    protected function planningVisibilityUser(ProjectTask $task, User $user): void
    {
        \App\Models\ProjectTaskVisibilityUser::createInstance([
            'project_id' => $task->project_id,
            'task_id' => $task->id,
            'userid' => $user->userid,
        ])->save();
    }

    /**
     * A task that only the given users may see (visibility=0).
     */
    protected function planningPrivateTask(Project $project, User $owner, array $visibleUsers = []): ProjectTask
    {
        $task = $this->planningTask($project, ['visibility' => 0], $owner);
        foreach ($visibleUsers as $user) {
            $this->planningVisibilityUser($task, $user);
        }
        return $task;
    }
}
