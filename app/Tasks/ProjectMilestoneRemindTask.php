<?php

namespace App\Tasks;

use App\Models\ProjectMilestone;
use App\Models\ProjectMilestoneReminderAttempt;
use App\Models\User;
use App\Models\WebSocketDialogUser;
use App\Models\WebSocketDialogMsg;
use App\Module\Base;
use Carbon\Carbon;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Log;
use Throwable;

class ProjectMilestoneRemindTask extends AbstractTask
{
    public function start()
    {
        $now = Carbon::now();
        // /crontab invokes this task every minute. A short global lock avoids
        // overlapping scans while leaving room to retry failed deliveries in
        // the same project's local day.
        $lock = Cache::lock('project-milestone-remind-task', 55);
        if (!$lock->get()) {
            return;
        }

        try {
            // Project timezones can differ from the application timezone by
            // more than a day. Reminder windows are at most seven days, so scan
            // a bounded superset and apply the exact window in each timezone.
            $scanFrom = $now->copy()->subDays(2)->toDateString();
            $scanThrough = $now->copy()->addDays(9)->toDateString();
            ProjectMilestone::query()
                ->with('project')
                ->whereHas('project', fn ($query) => $query->whereNull('archived_at'))
                ->where('status', 'open')
                ->where('reminder_days', '>=', 0)
                ->whereNull('reminded_at')
                ->whereBetween('target_at', [$scanFrom, $scanThrough])
                ->chunkById(100, function ($milestones) use ($now) {
                    foreach ($milestones as $candidate) {
                        if (!self::targetDateIfDue($candidate, $now)) continue;
                        $project = null;
                        $milestone = null;
                        $attempting = false;
                        $requestId = null;

                        $milestoneLock = Cache::lock('project-milestone-remind:' . $candidate->id . ':' . Carbon::parse($candidate->target_at)->toDateString(), 120);
                        if (!$milestoneLock->get()) {
                            continue;
                        }

                        try {
                            // The reminder may have been rescheduled or completed
                            // after the scan. Re-read before creating a chat message
                            // so a stale chunk result does not send an old reminder.
                            $milestone = ProjectMilestone::query()
                                ->with('project')
                                ->whereKey($candidate->id)
                                ->where('target_at', $candidate->target_at)
                                ->where('reminder_days', $candidate->reminder_days)
                                ->where('reminder_generation', $candidate->reminder_generation)
                                ->where('status', 'open')
                                ->whereNull('reminded_at')
                                ->first();
                            if (!$milestone) {
                                continue;
                            }
                            $project = $milestone->project;
                            if (!$project || $project->archived_at || $project->deleted_at) {
                                continue;
                            }
                            $targetAt = self::targetDateIfDue($milestone, $now);
                            if (!$targetAt) continue;
                            $attempting = true;
                            $requestId = self::idempotencyKey($milestone, $targetAt);
                            if ((int)$project->dialog_id <= 0) {
                                self::recordAttempt($milestone, 'chat_unavailable', $requestId);
                                continue;
                            }
                            $botUser = User::botGetOrCreate('task-alert');
                            if (!$botUser) {
                                self::recordAttempt($milestone, 'bot_unavailable', $requestId);
                                Log::warning('Project milestone reminder skipped: alert bot unavailable', [
                                    'project_id' => (int)$project->id,
                                    'milestone_id' => (int)$milestone->id,
                                    'reminder_generation' => (int)$milestone->reminder_generation,
                                ]);
                                continue;
                            }
                            $notice = "里程碑提醒：{$project->name} / {$milestone->name}，目标日期：{$targetAt->toDateString()}";
                            $ownerUserid = (int)$milestone->owner_userid;
                            $owner = $ownerUserid > 0
                                ? User::query()->where('userid', $ownerUserid)->whereNull('disable_at')->first()
                                : null;
                            $ownerInProjectChat = $owner && WebSocketDialogUser::query()
                                ->where('dialog_id', $project->dialog_id)
                                ->where('userid', $ownerUserid)
                                ->exists();

                            if ($ownerInProjectChat) {
                                $ownerMention = '<span class="mention user" data-id="' . $ownerUserid . '">@' . e($owner->nickname) . '</span>';
                                $result = WebSocketDialogMsg::sendMsg(
                                    null,
                                    (int)$project->dialog_id,
                                    'text',
                                    ['text' => $ownerMention . ' ' . e($notice)],
                                    $botUser->userid,
                                    false,
                                    false,
                                    true,
                                    null,
                                    $requestId
                                );
                            } else {
                                $result = WebSocketDialogMsg::sendMsg(
                                    null,
                                    (int)$project->dialog_id,
                                    'notice',
                                    ['notice' => $notice],
                                    $botUser->userid,
                                    false,
                                    false,
                                    true,
                                    null,
                                    $requestId
                                );
                            }
                            if (Base::isSuccess($result)) {
                                self::recordAttempt($milestone, 'sent', $requestId, null, true);
                            } else {
                                $resultCode = is_array($result) && isset($result['ret'])
                                    ? substr((string)$result['ret'], 0, 64)
                                    : null;
                                self::recordAttempt($milestone, 'delivery_rejected', $requestId, $resultCode);
                                Log::warning('Project milestone reminder delivery was rejected', [
                                    'project_id' => (int)$project->id,
                                    'milestone_id' => (int)$milestone->id,
                                    'reminder_generation' => (int)$milestone->reminder_generation,
                                    'request_id' => $requestId,
                                    'result_code' => $resultCode,
                                ]);
                            }
                        } catch (Throwable $e) {
                            if ($attempting && $milestone) {
                                try {
                                    self::recordAttempt($milestone, 'delivery_exception', $requestId);
                                } catch (Throwable $recordError) {
                                    report($recordError);
                                }
                            }
                            Log::error('Project milestone reminder delivery failed', [
                                'project_id' => isset($project) ? (int)$project->id : null,
                                'milestone_id' => (int)$candidate->id,
                                'reminder_generation' => (int)$candidate->reminder_generation,
                                'target_at' => Carbon::parse($candidate->target_at)->toDateString(),
                                'exception' => $e,
                            ]);
                            report($e);
                        } finally {
                            $milestoneLock->release();
                        }
                    }
                });
        } finally {
            $lock->release();
        }
    }

    private static function recordAttempt(
        ProjectMilestone $milestone,
        string $status,
        ?string $requestId,
        ?string $resultCode = null,
        bool $delivered = false
    ): void
    {
        $now = Carbon::now();
        DB::transaction(function () use ($milestone, $status, $requestId, $resultCode, $delivered, $now) {
            ProjectMilestone::query()
                ->whereKey($milestone->id)
                ->where('target_at', Carbon::parse($milestone->target_at)->toDateString())
                ->where('reminder_days', (int)$milestone->reminder_days)
                ->where('reminder_generation', (int)$milestone->reminder_generation)
                ->where('status', 'open')
                ->whereNull('reminded_at')
                ->update([
                    'reminder_attempt_count' => DB::raw('reminder_attempt_count + 1'),
                    'reminder_last_attempt_at' => $now,
                    'reminder_last_error_code' => $delivered ? null : $status,
                    'reminded_at' => $delivered ? $now : null,
                ]);

            ProjectMilestoneReminderAttempt::createInstance([
                'project_id' => (int)$milestone->project_id,
                'milestone_id' => (int)$milestone->id,
                'reminder_generation' => (int)$milestone->reminder_generation,
                'target_at' => Carbon::parse($milestone->target_at)->toDateString(),
                'request_id' => $requestId,
                'status' => $status,
                'result_code' => $resultCode ?: null,
                'attempted_at' => $now,
            ])->save();

            $keepIds = ProjectMilestoneReminderAttempt::query()
                ->where('milestone_id', $milestone->id)
                ->orderByDesc('id')
                ->limit(100)
                ->pluck('id');
            ProjectMilestoneReminderAttempt::query()
                ->where('milestone_id', $milestone->id)
                ->whereNotIn('id', $keepIds)
                ->delete();
        });
    }

    public function end()
    {
    }

    private static function idempotencyKey(ProjectMilestone $milestone, Carbon $targetAt): string
    {
        return hash('sha256', implode('|', [
            'project-milestone-reminder',
            (int)$milestone->id,
            $targetAt->toDateString(),
            (int)$milestone->reminder_days,
            (int)$milestone->reminder_generation,
        ]));
    }

    private static function targetDateIfDue(ProjectMilestone $milestone, Carbon $now): ?Carbon
    {
        $project = $milestone->project;
        if (!$project || $project->archived_at || $project->deleted_at || !$milestone->target_at) return null;
        $timezone = $project->task_schedule_timezone ?: config('app.timezone', 'UTC');
        if (!in_array($timezone, timezone_identifiers_list(), true)) $timezone = config('app.timezone', 'UTC');
        $projectNow = $now->copy()->setTimezone($timezone);
        if ($projectNow->hour < 9) return null;

        $today = $projectNow->copy()->startOfDay();
        $targetDate = Carbon::parse($milestone->target_at)->toDateString();
        $targetAt = Carbon::createFromFormat('!Y-m-d', $targetDate, $timezone);
        if (!$targetAt) return null;
        if ($targetAt->lessThan($today) || $targetAt->greaterThan($today->copy()->addDays(7))) return null;
        if ($today->lessThan($targetAt->copy()->subDays((int)$milestone->reminder_days))) return null;
        return $targetAt;
    }
}
