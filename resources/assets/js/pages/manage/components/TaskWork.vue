<template>
    <FormItem>
        <div class="item-label" slot="label"><i class="taskfont">&#xe6e8;</i>{{$L('工时')}}</div>
        <div class="item-content task-work">
            <div v-if="workLogsLoadFailed" class="task-work-load-error" role="alert">
                <span><Icon type="md-warning"/> {{$L('数据加载失败')}}</span>
                <Button size="small" type="text" :disabled="workLogsLoading" @click="loadWorkLogs(false)">{{$L('重试')}}</Button>
            </div>
            <div v-if="!workLogsLoaded && workLogsLoading" class="task-work-load-state">{{$L('加载中')}}</div>
            <template v-if="workLogsLoaded">
                <div class="task-work-summary">
                    <span>{{$L('预估')}} <InputNumber v-model="workEstimateInput" :min="0" :max="1000000" :step="30" :disabled="readonly || workSaving || !workCanEdit" size="small"/></span>
                    <Button size="small" type="text" :disabled="readonly || workSaving || !workCanEdit" :loading="workSaving" @click="saveWorkEstimate">{{$L('保存预估')}}</Button>
                    <span>{{$L('已登记')}} {{workSummary.logged_minutes || 0}} {{$L('分钟')}}</span>
                    <span>{{$L('剩余')}} {{workSummary.remaining_minutes || 0}} {{$L('分钟')}}</span>
                </div>
                <div v-if="!readonly && workCanEdit" class="task-work-add">
                    <InputNumber v-model="workMinutesInput" :min="1" :max="1440" :step="15" size="small" :placeholder="$L('分钟')" :disabled="workSaving || !!workLogRequestId"/>
                    <DatePicker v-model="workDateInput" type="date" format="yyyy-MM-dd" size="small" :disabled="workSaving || !!workLogRequestId"/>
                    <Input v-model="workNoteInput" :maxlength="500" size="small" :placeholder="$L('工时备注')" :disabled="workSaving || !!workLogRequestId"/>
                    <Button size="small" type="primary" :loading="workSaving" @click="addWorkLog">{{$L('登记工时')}}</Button>
                </div>
                <div v-for="log in workLogs" :key="log.id" class="task-work-log">
                    <span>{{log.work_date}} · {{log.user && log.user.name}} · {{log.minutes}} {{$L('分钟')}}</span>
                    <span v-if="log.note">{{log.note}}</span>
                    <Button v-if="!readonly && workCanEdit && (Number(log.userid) === Number(userId) || projectOwner)" size="small" type="text" :loading="deletingWorkLogIds.includes(log.id)" @click="deleteWorkLog(log.id)">{{$L('删除')}}</Button>
                </div>
                <Button v-if="workLogs.length < workLogsTotal" size="small" type="text" :loading="workLogsLoadingMore" :disabled="workLogsLoading" @click="loadMoreWorkLogs">{{$L('更多')}}</Button>
            </template>
        </div>
    </FormItem>
</template>

<script>
import emitter from "../../../store/events";

export default {
    name: "TaskWork",

    props: {
        taskId: {type: [Number, String], default: 0},
        projectId: {type: [Number, String], default: 0},
        userId: {type: [Number, String], default: 0},
        projectOwner: {type: Boolean, default: false},
        readonly: {type: Boolean, default: false},
        estimatedMinutes: {type: [Number, String], default: 0},
    },

    data() {
        return {
            workLogs: [],
            workLogsTotal: 0,
            workLogsPage: 0,
            workLogsLoading: false,
            workLogsLoaded: false,
            workLogsLoadFailed: false,
            workLogsLoadingMore: false,
            workSummary: {estimated_minutes: 0, logged_minutes: 0, remaining_minutes: 0, overrun_minutes: 0},
            workCanEdit: false,
            workMinutesInput: null,
            workDateInput: $A.dayjs().format('YYYY-MM-DD'),
            workNoteInput: '',
            workSaving: false,
            workEstimateInput: 0,
            workRequestKey: 0,
            workMutationKey: 0,
            workLogRequestId: '',
            deletingWorkLogIds: [],
        };
    },

    watch: {
        taskId: {
            handler() {
                this.reset();
                if (Number(this.taskId) > 0) {
                    this.loadWorkLogs();
                }
            },
            immediate: true,
        },
    },

    created() {
        emitter.on('taskWorkUpdate', this.onTaskWorkUpdate);
    },

    beforeDestroy() {
        emitter.off('taskWorkUpdate', this.onTaskWorkUpdate);
    },

    methods: {
        reset() {
            this.workRequestKey++;
            this.workMutationKey++;
            this.workSaving = false;
            this.workLogRequestId = '';
            this.deletingWorkLogIds = [];
            this.workMinutesInput = null;
            this.workNoteInput = '';
            this.workDateInput = $A.dayjs().format('YYYY-MM-DD');
            this.workLogs = [];
            this.workLogsTotal = 0;
            this.workLogsPage = 0;
            this.workLogsLoading = false;
            this.workLogsLoaded = false;
            this.workLogsLoadFailed = false;
            this.workCanEdit = false;
            this.workLogsLoadingMore = false;
            this.workSummary = {estimated_minutes: 0, logged_minutes: 0, remaining_minutes: 0, overrun_minutes: 0};
            this.workEstimateInput = Number(this.estimatedMinutes) || 0;
        },

        onTaskWorkUpdate(taskId) {
            if (Number(taskId) === Number(this.taskId) && this.workLogsLoaded) {
                this.loadWorkLogs();
            }
        },

        async loadWorkLogs(append = false) {
            const key = ++this.workRequestKey;
            const projectId = Number(this.projectId);
            const taskId = Number(this.taskId);
            if (!projectId || !taskId) {
                this.workLogsLoading = false;
                return;
            }
            const page = append ? this.workLogsPage + 1 : 1;
            this.workLogsLoading = true;
            this.workLogsLoadFailed = false;
            if (append) this.workLogsLoadingMore = true;
            else this.workLogsLoadingMore = false;
            try {
                const {data} = await this.$store.dispatch('call', {url: 'projecttaskwork/task', data: {project_id: projectId, task_id: taskId, page, per_page: 50}});
                if (key !== this.workRequestKey || taskId !== Number(this.taskId)) return;
                this.workLogs = append
                    ? [...new Map([...this.workLogs, ...(data.logs || [])].map(log => [log.id, log])).values()]
                    : (data.logs || []);
                this.workLogsTotal = Number(data.logs_total) || 0;
                this.workLogsPage = Number(data.logs_page) || page;
                this.workSummary = data.summary || this.workSummary;
                this.workCanEdit = !!data.can_edit;
                this.workEstimateInput = Number(this.workSummary.estimated_minutes) || 0;
                if (data.work_date_today) this.workDateInput = $A.dayjs(data.work_date_today).format('YYYY-MM-DD');
                this.workLogsLoaded = true;
            } catch (e) {
                if (key === this.workRequestKey && taskId === Number(this.taskId)) this.workLogsLoadFailed = true;
            } finally {
                if (key === this.workRequestKey) {
                    this.workLogsLoading = false;
                    this.workLogsLoadingMore = false;
                }
            }
        },

        loadMoreWorkLogs() {
            if (this.workLogsLoading || this.workLogsLoadingMore || this.workLogs.length >= this.workLogsTotal) return;
            return this.loadWorkLogs(true);
        },

        async saveWorkEstimate() {
            if (this.workSaving) return;
            const mutationKey = ++this.workMutationKey;
            const projectId = Number(this.projectId);
            const taskId = Number(this.taskId);
            this.workSaving = true;
            try {
                const {data, msg} = await this.$store.dispatch('call', {url: 'projecttaskwork/estimate', method: 'post', data: {
                    project_id: projectId, task_id: taskId, estimated_minutes: this.workEstimateInput || 0,
                }});
                if (mutationKey !== this.workMutationKey || taskId !== Number(this.taskId)) return;
                this.workSummary = data;
                this.$emit('estimate-updated', Number(data.estimated_minutes) || 0);
                $A.messageSuccess(msg);
            } catch (e) {
                if (mutationKey === this.workMutationKey && taskId === Number(this.taskId)) $A.modalError(e);
            } finally {
                if (mutationKey === this.workMutationKey) this.workSaving = false;
            }
        },

        async addWorkLog() {
            if (this.workSaving || !this.workMinutesInput) return;
            if (!this.workLogRequestId) this.workLogRequestId = `work-${Date.now()}-${Math.random().toString(36).slice(2, 12)}`;
            const mutationKey = ++this.workMutationKey;
            const projectId = Number(this.projectId);
            const taskId = Number(this.taskId);
            const requestId = this.workLogRequestId;
            this.workSaving = true;
            try {
                await this.$store.dispatch('call', {url: 'projecttaskwork/add', method: 'post', data: {
                    project_id: projectId, task_id: taskId,
                    request_id: requestId,
                    minutes: this.workMinutesInput, work_date: $A.dayjs(this.workDateInput).format('YYYY-MM-DD'), note: this.workNoteInput,
                }});
                if (mutationKey !== this.workMutationKey || taskId !== Number(this.taskId)) return;
                this.workLogRequestId = '';
                this.workMinutesInput = null;
                this.workNoteInput = '';
                await this.loadWorkLogs();
                if (mutationKey !== this.workMutationKey || taskId !== Number(this.taskId)) return;
                $A.messageSuccess(this.$L('工时已登记'));
            } catch (e) {
                if (mutationKey !== this.workMutationKey || taskId !== Number(this.taskId)) return;
                // A network failure may happen after the server committed. Keep the
                // same request ID and inputs so the next attempt safely replays it.
                if (Number(e?.ret) === 0) this.workLogRequestId = '';
                $A.modalError(e);
            } finally {
                if (mutationKey === this.workMutationKey) this.workSaving = false;
            }
        },

        async deleteWorkLog(logId) {
            if (this.deletingWorkLogIds.includes(logId)) return;
            const projectId = Number(this.projectId);
            const taskId = Number(this.taskId);
            this.deletingWorkLogIds.push(logId);
            try {
                await this.$store.dispatch('call', {url: 'projecttaskwork/delete', method: 'post', data: {project_id: projectId, log_id: logId}});
                if (projectId !== Number(this.projectId) || taskId !== Number(this.taskId)) return;
                await this.loadWorkLogs();
            } catch (e) {
                if (projectId === Number(this.projectId) && taskId === Number(this.taskId)) $A.modalError(e);
            } finally {
                if (projectId === Number(this.projectId) && taskId === Number(this.taskId)) {
                    this.deletingWorkLogIds = this.deletingWorkLogIds.filter(id => id !== logId);
                }
            }
        },
    },
};
</script>
