-- V21: composite indexes for dashboard / status wall / report queries.
-- V4 already indexes project_id / owner_userid / complete_at separately,
-- V7 indexes (project_id, work_date, userid); these cover the remaining shapes.

ALTER TABLE `project_tasks`
    ADD KEY `project_tasks_project_complete_idx` (`project_id`, `complete_at`),
    ADD KEY `project_tasks_owner_complete_idx` (`owner_userid`, `complete_at`);

ALTER TABLE `project_task_work_logs`
    ADD KEY `task_work_logs_user_date_idx` (`userid`, `work_date`);
