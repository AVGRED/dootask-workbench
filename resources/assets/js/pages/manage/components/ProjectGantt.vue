<template>
    <div class="project-gstc-gantt">
        <GanttView
            :lists="lists"
            :dependencies="dependencies"
            :milestones="milestones"
            :project-timezone="projectTimezone"
            :menuWidth="menuWidth"
            :itemWidth="80"
            @on-change="onChange"
            @on-click="onClick"
            :readonly="readonly || editLoad > 0">
            <template #titleTool>
                <div class="project-gstc-title-tools">
                    <Dropdown class="project-gstc-dropdown-filtr" trigger="click" @on-click="onSwitchColumn" transfer>
                        <Icon class="project-gstc-dropdown-icon" :class="{filtr:filtrProjectId > 0}" type="md-funnel" />
                        <DropdownMenu slot="list">
                            <DropdownItem :name="0" :class="{'dropdown-active':filtrProjectId == 0}">{{ $L('全部') }}</DropdownItem>
                            <DropdownItem
                                v-for="(item, index) in projectColumn"
                                :key="index"
                                :name="item.id"
                                :class="{'dropdown-active':filtrProjectId == item.id}">
                                {{ item.name }}
                                <span v-if="item.tasks">({{ filtrLength(item.tasks) }})</span>
                            </DropdownItem>
                        </DropdownMenu>
                    </Dropdown>
                    <Button v-if="dependencyEnabled" size="small" icon="md-alert" @click.stop="openDependencyRisks">{{$L('依赖风险')}}</Button>
                </div>
            </template>
        </GanttView>
        <Modal v-model="riskSummaryVisible" :title="$L('依赖风险')" :width="riskSummaryWidth" :mask-closable="false">
            <div class="project-gstc-risk-summary">
                <div v-if="riskSummaryLoading && riskSummary.items.length === 0" class="form-tip">{{$L('加载中...')}}</div>
                <div v-else-if="riskSummaryFailed" class="project-gstc-edit-warning">
                    <span><Icon type="md-warning"/> {{$L('数据加载失败')}}</span>
                    <Button size="small" type="text" :disabled="riskSummaryLoading" @click="loadDependencyRisks">{{$L('重试')}}</Button>
                </div>
                <template v-else>
                    <div class="project-gstc-risk-counts">
                        <Tag color="error">{{$L('计划冲突')}} {{riskSummary.schedule_conflicts}}</Tag>
                        <Tag color="warning">{{$L('逾期阻塞')}} {{riskSummary.overdue_blockers}}</Tag>
                    </div>
                    <div v-if="riskSummary.items.length === 0" class="form-tip">{{$L('当前没有可见的依赖风险')}}</div>
                    <ul v-else class="project-gstc-risk-list">
                        <li v-for="item in riskSummary.items" :key="item.id">
                            <div class="project-gstc-risk-types">
                                <Tag v-for="type in item.types" :key="type" :color="type === 'overdue_blocker' ? 'warning' : 'error'">{{riskTypeLabel(type)}}</Tag>
                            </div>
                            <div class="project-gstc-risk-tasks">
                                <Button type="text" @click="openRiskTask(item.predecessor)">#{{item.predecessor.id}} {{item.predecessor.name}}</Button>
                                <Icon type="md-arrow-forward"/>
                                <Button type="text" @click="openRiskTask(item.successor)">#{{item.successor.id}} {{item.successor.name}}</Button>
                            </div>
                            <div class="project-gstc-risk-dates">
                                <span>{{item.predecessor.end_at ? formatRiskTime(item.predecessor.end_at) : '-'}}</span>
                                <Icon type="md-arrow-forward"/>
                                <span>{{item.successor.start_at ? formatRiskTime(item.successor.start_at) : '-'}}</span>
                            </div>
                        </li>
                    </ul>
                    <p v-if="riskSummary.truncated" class="form-tip">{{$L('仅显示前50条风险')}}</p>
                </template>
            </div>
            <div slot="footer">
                <Button @click="riskSummaryVisible = false">{{$L('关闭')}}</Button>
            </div>
        </Modal>
        <div class="project-gstc-edit" :class="{info:editShowInfo, visible:editData && editData.length > 0}">
            <div v-if="dependencyLoadFailed" class="project-gstc-edit-warning">
                <span><Icon type="md-warning"/>{{$L('数据加载失败')}}</span>
                <Button size="small" type="text" @click="loadDependencies">{{$L('重试')}}</Button>
            </div>
            <div class="project-gstc-edit-info">
                <Table max-height="600" :columns="editColumns" :data="editData"></Table>
                <div class="project-gstc-edit-btns">
                    <Button :loading="editLoad > 0" type="text" @click="editSubmit(false)">{{$L('取消')}}</Button>
                    <Button v-if="canManageSchedule && scheduleMode === 'automatic' && editData.length === 1" :loading="schedulePreviewLoading" :disabled="dependencyLoading || dependencyLoadFailed" type="warning" @click="previewCascadeSchedule">{{$L('预演后续排期')}}</Button>
                    <Button :loading="editLoad > 0" type="primary" @click="editSubmit(true)">{{$L('保存')}}</Button>
                    <Icon type="md-arrow-dropright" class="zoom" @click="editShowInfo=false"/>
                </div>
            </div>
            <div class="project-gstc-edit-small">
                <div class="project-gstc-edit-text" @click="editShowInfo=true">{{$L('未保存计划时间')}}: <span v-if="editData">{{editData.length}}</span></div>
                <Button :loading="editLoad > 0" size="small" type="text" @click="editSubmit(false)">{{$L('取消')}}</Button>
                <Button v-if="canManageSchedule && scheduleMode === 'automatic' && editData.length === 1" :loading="schedulePreviewLoading" :disabled="dependencyLoading || dependencyLoadFailed" size="small" type="warning" @click="previewCascadeSchedule">{{$L('预演后续排期')}}</Button>
                <Button :loading="editLoad > 0" size="small" type="primary" @click="editSubmit(true)">{{$L('保存')}}</Button>
            </div>
        </div>
        <div v-if="canManageSchedule && !readonly && (lastScheduleBatchId || scheduleBatchesLoading || scheduleBatchesLoadFailed)" class="project-gstc-schedule-undo">
            <span v-if="scheduleBatchesLoadFailed"><Icon type="md-warning"/> {{$L('数据加载失败')}}</span>
            <span v-else-if="scheduleBatchesLoading">{{$L('加载中...')}}</span>
            <span v-else>{{$L('最近一次自动排期已应用')}}</span>
            <Button v-if="lastScheduleBatchId" size="small" type="text" :loading="scheduleUndoLoading" @click="undoLastScheduleBatch">{{$L('撤销排期')}}</Button>
            <Button v-if="scheduleBatchesLoadFailed" size="small" type="text" :loading="scheduleBatchesLoading" @click="loadScheduleBatches">{{$L('重试')}}</Button>
        </div>
        <Modal v-model="schedulePreviewVisible" :title="$L('自动排期预演')" :width="schedulePreviewWidth" :mask-closable="false">
            <p v-if="schedulePreview.preview_hash" class="project-gstc-schedule-summary">{{$L('本次将调整(*)个任务；已完成、缺少计划或无权修改的任务会列在跳过项。', schedulePreview.changes.length)}}</p>
            <div v-if="schedulePreviewLoading" class="project-gstc-schedule-loading" role="status">{{$L('加载中...')}}</div>
            <Table v-else :columns="schedulePreviewColumns" :data="schedulePreview.changes" max-height="360"/>
            <div v-if="!schedulePreviewLoading && schedulePreview.skipped.length" class="project-gstc-schedule-skipped">
                <div class="title">{{$L('跳过的任务')}}</div>
                <div v-for="item in schedulePreview.skipped" :key="item.id">#{{item.id}} {{item.name}}：{{item.reason}}</div>
            </div>
            <div slot="footer">
                <Button :disabled="scheduleApplyLoading" @click="clearSchedulePreview">{{$L('取消')}}</Button>
                <Button type="warning" :loading="schedulePreviewLoading" :disabled="scheduleApplyLoading || dependencyLoading || dependencyLoadFailed" @click="previewCascadeSchedule">{{$L('预演后续排期')}}</Button>
                <Button type="primary" :disabled="schedulePreviewLoading || !schedulePreview.preview_hash || schedulePreview.changes.length === 0" :loading="scheduleApplyLoading" @click="applyCascadeSchedule">{{$L('确认应用排期')}}</Button>
            </div>
        </Modal>
    </div>
</template>

<script>
import {mapGetters, mapState} from "vuex";
import GanttView from "../../../components/GanttView";
import {taskMatchesProjectFlowFilter} from "../../../utils/projectFlowFilter";
import emitter from "../../../store/events";

export default {
    name: 'ProjectGantt',
    components: {GanttView},
    props: {
        projectId: {
            type: Number,
            default: 0
        },
        projectColumn: {
            default: []
        },
        flowFilter: {
            type: Object,
            default: () => ({})
        },
        searchText: {
            type: String,
            default: ''
        },
        readonly: {
            type: Boolean,
            default: false
        },
        canManageSchedule: {type: Boolean, default: false},
        dependencyEnabled: {type: Boolean, default: true},
        scheduleMode: {type: String, default: 'manual'},
        projectTimezone: {type: String, default: ''},
    },

    data() {
        return {
            lists: [],
            dependencies: [],
            dependencyLoading: false,
            dependencyLoadFailed: false,
            dependencyLoadTimer: null,
            milestones: [],
            dependencyRequestKey: 0,
            milestoneRequestKey: 0,

            filtrProjectId: 0,

            editColumns: [
                {
                    title: this.$L('任务名称'),
                    key: 'label',
                    minWidth: 100,
                    ellipsis: true,
                }, {
                    title: this.$L('原计划时间'),
                    width: 140,
                    align: 'center',
                    render: (h, {row}) => {
                        if (row.notime === true) {
                            return h('span', '-');
                        }
                        return h('div', {
                            style: {},
                        }, [
                            h('div', this.formatGanttTime(row.baktime.start, "YYYY-MM-DD HH:mm")),
                            h('div', this.formatGanttTime(row.baktime.end, "YYYY-MM-DD HH:mm"))
                        ]);
                    }
                }, {
                    title: this.$L('新计划时间'),
                    width: 140,
                    align: 'center',
                    render: (h, {row}) => {
                        return h('div', {
                            style: {},
                        }, [
                            h('div', this.formatGanttTime(row.newTime.start, "YYYY-MM-DD HH:mm")),
                            h('div', this.formatGanttTime(row.newTime.end, "YYYY-MM-DD HH:mm"))
                        ]);
                    }
                }, {
                    title: this.$L('依赖冲突'),
                    minWidth: 180,
                    render: (h, {row}) => {
                        const conflicts = this.scheduleConflictNames(row.id);
                        if (!conflicts.length) return h('span', '-');
                        return h('div', {class: 'project-gantt-schedule-conflict'}, [
                            h('Icon', {props: {type: 'md-warning'}}),
                            h('span', this.$L('计划时间冲突提示')),
                            ...conflicts.map((name, index) => h('div', {key: index}, name)),
                        ]);
                    }
                }
            ],
            editData: [],
            editLoad: 0,
            editShowInfo: false,
            schedulePreviewVisible: false,
            schedulePreviewLoading: false,
            scheduleApplyLoading: false,
            scheduleUndoLoading: false,
            scheduleBatchesLoading: false,
            scheduleBatchesLoadFailed: false,
            schedulePreviewRequestKey: 0,
            scheduleApplyRequestKey: 0,
            scheduleUndoRequestKey: 0,
            schedulePreview: {preview_hash: '', changes: [], skipped: []},
            schedulePreviewAnchor: null,
            scheduleApplyRequestId: '',
            riskSummaryVisible: false,
            riskSummaryLoading: false,
            riskSummaryFailed: false,
            riskSummaryRequestKey: 0,
            riskSummary: {schedule_conflicts: 0, overdue_blockers: 0, total_risk_edges: 0, truncated: false, items: []},
            lastScheduleBatchId: 0,
            schedulePreviewColumns: [
                {title: this.$L('任务名称'), key: 'name', minWidth: 140},
                {title: this.$L('调整原因'), key: 'reason', minWidth: 140},
                {title: this.$L('新开始时间'), minWidth: 150, render: (h, {row}) => h('span', row.after?.start_at || '-')},
                {title: this.$L('新结束时间'), minWidth: 150, render: (h, {row}) => h('span', row.after?.end_at || '-')},
            ],
        }
    },

    mounted() {
        this.initData();
        this.loadMilestones();
        this.loadScheduleBatches();
        emitter.on('projectMilestonesUpdate', this.onProjectMilestonesUpdate);
    },

    destroyed() {
        emitter.off('projectMilestonesUpdate', this.onProjectMilestonesUpdate);
        clearTimeout(this.dependencyLoadTimer);
        this.dependencyRequestKey++;
        this.riskSummaryRequestKey++;
        this.schedulePreviewRequestKey++;
        this.scheduleApplyRequestKey++;
        this.scheduleUndoRequestKey++;
    },

    computed: {
        ...mapState(['taskPriority']),

        ...mapGetters(['projectData']),

        menuWidth() {
            return this.windowWidth < 1440 ? 180 : 260;
        },

        schedulePreviewWidth() {
            return this.windowWidth < 800 ? '96%' : 760;
        },

        riskSummaryWidth() {
            return this.windowWidth < 800 ? '96%' : 680;
        },

        completedTask() {
            return this.projectData.cacheParameter.completedTask;
        }
    },

    watch: {
        projectTimezone(timezone, previousTimezone) {
            if (!previousTimezone || !timezone || timezone === previousTimezone) return;
            const convertWallTime = value => {
                const oldWallTime = this.formatGanttTime(value);
                const newProjectTime = $A.dayjsTz(oldWallTime, previousTimezone).tz(timezone);
                return Date.UTC(newProjectTime.year(), newProjectTime.month(), newProjectTime.date(),
                    newProjectTime.hour(), newProjectTime.minute(), newProjectTime.second(), newProjectTime.millisecond());
            };
            this.clearSchedulePreview();
            this.editData = this.editData.map(item => ({
                ...item,
                baktime: {start: convertWallTime(item.baktime.start), end: convertWallTime(item.baktime.end)},
                newTime: {start: convertWallTime(item.newTime.start), end: convertWallTime(item.newTime.end)},
            }));
            this.initData();
        },
        projectId() {
            this.riskSummaryRequestKey++;
            this.riskSummaryVisible = false;
            this.riskSummaryLoading = false;
            this.riskSummaryFailed = false;
            this.riskSummary = {schedule_conflicts: 0, overdue_blockers: 0, total_risk_edges: 0, truncated: false, items: []};
            this.clearSchedulePreview();
            this.scheduleApplyRequestKey++;
            this.scheduleUndoRequestKey++;
            this.scheduleApplyLoading = false;
            this.scheduleUndoLoading = false;
            this.scheduleBatchesLoading = false;
            this.scheduleBatchesLoadFailed = false;
            this.lastScheduleBatchId = 0;
            this.loadScheduleBatches();
            if (this.editLoad === 0 && this.editData.length > 0) {
                this.editSubmit(false);
            }
            this.filtrProjectId = 0;
            this.initData();
            this.loadMilestones();
        },
        dependencyEnabled(enabled) {
            if (enabled) return;
            this.riskSummaryRequestKey++;
            this.riskSummaryVisible = false;
            this.riskSummaryLoading = false;
            this.riskSummaryFailed = false;
            this.riskSummary = {schedule_conflicts: 0, overdue_blockers: 0, total_risk_edges: 0, truncated: false, items: []};
            this.clearSchedulePreview();
        },
        scheduleMode(mode) {
            if (mode !== 'automatic') this.clearSchedulePreview();
        },
        canManageSchedule(canManage) {
            if (!canManage || this.readonly) {
                this._scheduleBatchesRequestKey = (this._scheduleBatchesRequestKey || 0) + 1;
                this.scheduleBatchesLoading = false;
                this.scheduleBatchesLoadFailed = false;
                this.lastScheduleBatchId = 0;
                this.clearSchedulePreview();
            } else {
                this.loadScheduleBatches();
            }
        },
        readonly(value) {
            if (value) {
                this._scheduleBatchesRequestKey = (this._scheduleBatchesRequestKey || 0) + 1;
                this.scheduleBatchesLoading = false;
                this.scheduleBatchesLoadFailed = false;
                this.lastScheduleBatchId = 0;
                this.clearSchedulePreview();
            } else if (this.canManageSchedule) {
                this.loadScheduleBatches();
            }
        },
        projectColumn: {
            handler() {
                this.initData();
            },
            deep: true,
        },
        flowFilter: {
            handler() {
                this.initData();
            },
            deep: true,
        },
        searchText() {
            this.initData();
        },
        completedTask() {
            this.initData();
        }
    },

    methods: {
        async openDependencyRisks() {
            this.riskSummaryVisible = true;
            this.riskSummary = {schedule_conflicts: 0, overdue_blockers: 0, total_risk_edges: 0, truncated: false, items: []};
            await this.loadDependencyRisks();
        },

        async loadDependencyRisks() {
            const projectId = Number(this.projectId);
            if (!projectId) return;
            const requestKey = ++this.riskSummaryRequestKey;
            this.riskSummaryLoading = true;
            this.riskSummaryFailed = false;
            try {
                const {data} = await this.$store.dispatch('call', {
                    url: 'projecttaskdependency/risks',
                    data: {project_id: projectId},
                });
                if (requestKey !== this.riskSummaryRequestKey || projectId !== Number(this.projectId)) return;
                this.riskSummary = {
                    schedule_conflicts: Number(data.schedule_conflicts) || 0,
                    overdue_blockers: Number(data.overdue_blockers) || 0,
                    total_risk_edges: Number(data.total_risk_edges) || 0,
                    truncated: !!data.truncated,
                    items: data.items || [],
                };
            } catch (e) {
                if (requestKey === this.riskSummaryRequestKey && projectId === Number(this.projectId)) {
                    this.riskSummaryFailed = true;
                }
            } finally {
                if (requestKey === this.riskSummaryRequestKey) this.riskSummaryLoading = false;
            }
        },

        riskTypeLabel(type) {
            return type === 'overdue_blocker' ? this.$L('逾期阻塞') : this.$L('计划冲突');
        },

        formatRiskTime(value) {
            return $A.dayjsTz(value, this.serverTimezone())
                .tz(this.projectTimezone || this.serverTimezone())
                .format('YYYY-MM-DD HH:mm');
        },

        openRiskTask(task) {
            if (!task || !task.id) return;
            this.$store.dispatch('openTask', {id: Number(task.id), project_id: Number(this.projectId)});
        },

        serverTimezone() {
            return $A.timezoneName || Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
        },

        ganttTimestamp(value) {
            if (!value) return 0;
            const serverTime = $A.dayjsTz(value, this.serverTimezone());
            const projectTime = serverTime.tz(this.projectTimezone || this.serverTimezone());
            return Date.UTC(projectTime.year(), projectTime.month(), projectTime.date(),
                projectTime.hour(), projectTime.minute(), projectTime.second(), projectTime.millisecond());
        },

        formatGanttTime(value, format = 'YYYY-MM-DD HH:mm:ss') {
            return $A.dayjsUtc(Number(value)).format(format);
        },

        storageTimeFromGantt(value) {
            const projectWallTime = this.formatGanttTime(value);
            return $A.dayjsTz(projectWallTime, this.projectTimezone || this.serverTimezone())
                .tz(this.serverTimezone()).format('YYYY-MM-DD HH:mm');
        },

        clearSchedulePreview() {
            this.schedulePreviewRequestKey++;
            this.schedulePreviewLoading = false;
            this.schedulePreviewVisible = false;
            this.schedulePreview = {preview_hash: '', changes: [], skipped: []};
            this.schedulePreviewAnchor = null;
            this.scheduleApplyRequestId = '';
        },

        async previewCascadeSchedule() {
            if (!this.canManageSchedule || this.readonly || !this.dependencyEnabled
                || this.scheduleMode !== 'automatic' || this.editData.length !== 1 || this.schedulePreviewLoading || this.scheduleApplyLoading) return;
            const projectId = Number(this.projectId);
            const item = this.editData[0];
            const requestKey = ++this.schedulePreviewRequestKey;
            const anchor = {
                task_id: Number(item.id),
                start_at: this.formatGanttTime(item.newTime.start),
                end_at: this.formatGanttTime(item.newTime.end),
            };
            this.schedulePreviewLoading = true;
            this.schedulePreview = {preview_hash: '', changes: [], skipped: []};
            this.schedulePreviewAnchor = null;
            this.scheduleApplyRequestId = '';
            try {
                const {data} = await this.$store.dispatch('call', {
                    url: 'projecttaskschedule/preview',
                    method: 'post',
                    data: {project_id: projectId, ...anchor},
                });
                if (requestKey !== this.schedulePreviewRequestKey || projectId !== Number(this.projectId)) return;
                this.schedulePreview = {
                    preview_hash: data.preview_hash,
                    changes: data.changes || [],
                    skipped: data.skipped || [],
                };
                this.schedulePreviewAnchor = {project_id: projectId, ...anchor};
                this.scheduleApplyRequestId = `schedule-${Date.now()}-${Math.random().toString(36).slice(2, 12)}`;
                this.schedulePreviewVisible = true;
            } catch (error) {
                if (requestKey === this.schedulePreviewRequestKey) $A.modalError(error);
            } finally {
                if (requestKey === this.schedulePreviewRequestKey) this.schedulePreviewLoading = false;
            }
        },

        async applyCascadeSchedule() {
            if (!this.canManageSchedule || this.readonly || !this.dependencyEnabled || this.scheduleMode !== 'automatic'
                || !this.schedulePreviewAnchor || !this.schedulePreview.preview_hash || this.scheduleApplyLoading) return;
            const {project_id, ...anchor} = this.schedulePreviewAnchor;
            if (!this.scheduleApplyRequestId) {
                this.scheduleApplyRequestId = `schedule-${Date.now()}-${Math.random().toString(36).slice(2, 12)}`;
            }
            const requestKey = ++this.scheduleApplyRequestKey;
            this.scheduleApplyLoading = true;
            try {
                const {data, msg} = await this.$store.dispatch('call', {
                    url: 'projecttaskschedule/apply',
                    method: 'post',
                    data: {
                        project_id,
                        ...anchor,
                        preview_hash: this.schedulePreview.preview_hash,
                        request_id: this.scheduleApplyRequestId,
                    },
                });
                if (requestKey !== this.scheduleApplyRequestKey || Number(project_id) !== Number(this.projectId)) return;
                this.lastScheduleBatchId = Number(data.batch_id) || 0;
                this.schedulePreviewVisible = false;
                this.scheduleApplyRequestId = '';
                this.editData = [];
                await this.$store.dispatch('getTaskForProject', project_id);
                if (requestKey !== this.scheduleApplyRequestKey || Number(project_id) !== Number(this.projectId)) return;
                this.initData();
                this.loadScheduleBatches();
                $A.messageSuccess(msg);
            } catch (error) {
                if (requestKey === this.scheduleApplyRequestKey && Number(project_id) === Number(this.projectId)) $A.modalError(error);
            } finally {
                if (requestKey === this.scheduleApplyRequestKey) this.scheduleApplyLoading = false;
            }
        },

        async undoLastScheduleBatch() {
            const batchId = this.lastScheduleBatchId;
            const projectId = Number(this.projectId);
            if (!this.canManageSchedule || this.readonly || !batchId || this.scheduleUndoLoading) return;
            const requestKey = ++this.scheduleUndoRequestKey;
            this.scheduleUndoLoading = true;
            try {
                const {msg} = await this.$store.dispatch('call', {
                    url: 'projecttaskschedule/undo',
                    method: 'post',
                    data: {project_id: projectId, batch_id: batchId},
                });
                if (requestKey !== this.scheduleUndoRequestKey || projectId !== Number(this.projectId)) return;
                this.lastScheduleBatchId = 0;
                await this.$store.dispatch('getTaskForProject', projectId);
                if (requestKey !== this.scheduleUndoRequestKey || projectId !== Number(this.projectId)) return;
                this.initData();
                this.loadScheduleBatches();
                $A.messageSuccess(msg);
            } catch (error) {
                if (requestKey === this.scheduleUndoRequestKey && projectId === Number(this.projectId)) $A.modalError(error);
            } finally {
                if (requestKey === this.scheduleUndoRequestKey) this.scheduleUndoLoading = false;
            }
        },

        async loadScheduleBatches() {
            const projectId = Number(this.projectId);
            const requestKey = this._scheduleBatchesRequestKey = (this._scheduleBatchesRequestKey || 0) + 1;
            if (!projectId || !this.canManageSchedule || this.readonly) {
                this.scheduleBatchesLoading = false;
                this.scheduleBatchesLoadFailed = false;
                this.lastScheduleBatchId = 0;
                return;
            }
            this.scheduleBatchesLoading = true;
            this.scheduleBatchesLoadFailed = false;
            try {
                const {data} = await this.$store.dispatch('call', {
                    url: 'projecttaskschedule/batches',
                    data: {project_id: projectId},
                });
                if (requestKey !== this._scheduleBatchesRequestKey || projectId !== Number(this.projectId)) return;
                const latest = (data || []).find(item => item.status === 'applied');
                this.lastScheduleBatchId = latest ? Number(latest.id) : 0;
            } catch (e) {
                if (requestKey === this._scheduleBatchesRequestKey && projectId === Number(this.projectId)) {
                    this.scheduleBatchesLoadFailed = true;
                }
            } finally {
                if (requestKey === this._scheduleBatchesRequestKey) this.scheduleBatchesLoading = false;
            }
        },

        initData() {
            this.lists = [];
            this.projectColumn && this.projectColumn.some(this.checkAdd);
            clearTimeout(this.dependencyLoadTimer);
            this.dependencyRequestKey++;
            this.dependencies = [];
            this.dependencyLoading = false;
            this.dependencyLoadFailed = false;
            this.dependencyLoadTimer = setTimeout(() => this.loadDependencies(), 180);
        },

        async loadDependencies() {
            clearTimeout(this.dependencyLoadTimer);
            this.dependencyLoadTimer = null;
            const requestKey = ++this.dependencyRequestKey;
            const taskIds = [...new Set([
                ...this.lists.map(item => Number(item.id)),
                ...this.editData
                    .filter(item => Number(item.project_id || this.projectId) === Number(this.projectId))
                    .map(item => Number(item.id)),
            ].filter(id => id > 0))];
            if (!this.projectId || taskIds.length === 0) {
                this.dependencies = [];
                this.dependencyLoading = false;
                this.dependencyLoadFailed = false;
                return;
            }
            this.dependencyLoading = true;
            this.dependencyLoadFailed = false;
            try {
                const {data} = await this.$store.dispatch('call', {
                    url: 'projecttaskdependency/project',
                    data: {project_id: this.projectId, task_ids: taskIds},
                });
                if (requestKey === this.dependencyRequestKey) {
                    this.dependencies = data.list || [];
                }
            } catch (e) {
                if (requestKey === this.dependencyRequestKey) {
                    this.dependencies = [];
                    this.dependencyLoadFailed = true;
                }
                console.warn(e);
            } finally {
                if (requestKey === this.dependencyRequestKey) {
                    this.dependencyLoading = false;
                }
            }
        },

        async loadMilestones() {
            const requestKey = ++this.milestoneRequestKey;
            if (!this.projectId) {
                this.milestones = [];
                return;
            }
            try {
                const {data} = await this.$store.dispatch('call', {
                    url: 'projectmilestone/lists',
                    data: {project_id: this.projectId},
                });
                if (requestKey === this.milestoneRequestKey) {
                    this.milestones = (data.milestones || []).filter(item => item.target_at);
                }
            } catch (e) {
                if (requestKey === this.milestoneRequestKey) this.milestones = [];
                console.warn(e);
            }
        },

        onProjectMilestonesUpdate(projectId) {
            if (Number(projectId) === Number(this.projectId)) this.loadMilestones();
        },

        flowTask(task) {
            return !taskMatchesProjectFlowFilter(task, this.flowFilter);
        },

        filtrLength(list) {
            return list.filter(taskData => {
                if (taskData.complete_at && !this.completedTask) {
                    return false;
                }
                if (this.flowTask(taskData)) {
                    return false;
                }
                if (this.searchText && taskData.id != this.searchText && !$A.strExists(`${taskData.name} ${taskData.desc || ''}`, this.searchText)) {
                    return false;
                }
                return true
            }).length
        },

        checkAdd(item) {
            if (this.filtrProjectId > 0) {
                if (item.id != this.filtrProjectId) {
                    return;
                }
            }
            item.tasks && item.tasks.some(taskData => {
                let notime = !taskData.start_at || !taskData.end_at;
                let times = this.getTimeObj(taskData);
                let start = times.start;
                let end = times.end;
                //
                if (taskData.complete_at && !this.completedTask) {
                    return false;
                }
                if (this.flowTask(taskData)) {
                    return false;
                }
                if (this.searchText && taskData.id != this.searchText && !$A.strExists(`${taskData.name} ${taskData.desc || ''}`, this.searchText)) {
                    return false;
                }
                // 等级颜色
                let color = '#058ce4';
                this.taskPriority.some(level => {
                    if (level.priority === taskData.p_level) {
                        color = level.color;
                        return true;
                    }
                });
                //
                let tempTime = {start, end};
                let bakTime = $A.cloneJSON(tempTime)
                let findData = this.editData.find(({id}) => id == taskData.id);
                if (findData) {
                    tempTime = $A.cloneJSON(findData.newTime);
                }
                //
                this.lists.push({
                    id: taskData.id,
                    label: taskData.name,
                    complete: taskData.complete_at,
                    overdue: taskData.overdue,
                    time: tempTime,
                    notime: notime,
                    baktime: bakTime,
                    style: {background: color},
                });
            });
        },

        onChange(item) {
            if (this.readonly) {
                return;
            }
            const {time, baktime} = item;
            if (Math.abs(baktime.end - time.end) > 1000 || Math.abs(baktime.start - time.start) > 1000) {
                //修改时间（变化超过1秒钟)
                let findData = this.editData.find(({id}) => id == item.id);
                if (findData) {
                    findData.newTime = time;
                } else {
                    this.editData.push({
                        id: item.id,
                        project_id: this.projectId,
                        label: item.label,
                        notime: item.notime,
                        baktime: item.baktime,
                        newTime: time,
                    })
                }
            }
        },

        scheduleConflictNames(taskId) {
            const names = [];
            const timeFor = (task) => {
                const edit = this.editData.find(({id}) => Number(id) === Number(task.id));
                const listedTask = this.lists.find(({id}) => Number(id) === Number(task.id));
                if (edit) {
                    return {time: edit.newTime, notime: false};
                }
                if (listedTask) {
                    return {time: listedTask.time, notime: listedTask.notime};
                }
                return {
                    time: {
                        start: this.ganttTimestamp(task.start_at),
                        end: this.ganttTimestamp(task.end_at),
                    },
                    notime: !task.start_at || !task.end_at,
                };
            };
            this.dependencies.forEach(edge => {
                const predecessor = this.lists.find(task => Number(task.id) === Number(edge.predecessor_task_id)) || edge.predecessor;
                const successor = this.lists.find(task => Number(task.id) === Number(edge.successor_task_id)) || edge.successor;
                if (!predecessor || !successor || Number(predecessor.id) !== Number(taskId) && Number(successor.id) !== Number(taskId)) {
                    return;
                }
                const predecessorSchedule = timeFor(predecessor);
                const successorSchedule = timeFor(successor);
                if (predecessorSchedule.notime || successorSchedule.notime
                    || successorSchedule.time.start >= predecessorSchedule.time.end) {
                    return;
                }
                const otherTask = Number(predecessor.id) === Number(taskId) ? successor : predecessor;
                names.push(`#${otherTask.id} ${otherTask.label || otherTask.name}`);
            });
            return [...new Set(names)];
        },

        onClick(item) {
            this.$store.dispatch("openTask", item);
        },

        async editSubmit(save) {
            if (this.editLoad > 0) {
                return;
            }
            if (this.readonly) {
                this.editData.forEach(item => {
                    const task = this.lists.find(({id}) => Number(id) === Number(item.id));
                    task && this.$set(task, 'time', $A.cloneJSON(task.baktime));
                });
                this.editData = [];
                return;
            }
            const pending = [...this.editData];
            if (!save) {
                pending.forEach(item => {
                    const task = this.lists.find(({id}) => Number(id) === Number(item.id));
                    task && this.$set(task, 'time', $A.cloneJSON(task.baktime));
                });
                this.editData = [];
                return;
            }

            this.editLoad = 1;
            const results = new Array(pending.length);
            let nextIndex = 0;
            // Keep large schedule batches from flooding the task update API.
            const saveNext = async () => {
                while (nextIndex < pending.length) {
                    const index = nextIndex++;
                    const item = pending[index];
                    try {
                        const dataJson = {
                            task_id: item.id,
                            times: [
                                this.storageTimeFromGantt(item.newTime.start),
                                this.storageTimeFromGantt(item.newTime.end),
                            ],
                        };
                        const response = await this.$store.dispatch("taskUpdate", dataJson);
                        results[index] = {item, success: true, message: response && response.msg};
                    } catch (error) {
                        results[index] = {item, success: false, message: error && (error.msg || error.message)};
                    }
                }
            };
            await Promise.all(Array.from({length: Math.min(5, pending.length)}, saveNext));

            const failedIds = new Set();
            const errors = [];
            let successMessage = '';
            results.forEach(result => {
                const task = this.lists.find(({id}) => Number(id) === Number(result.item.id));
                const isCurrentProject = Number(result.item.project_id || this.projectId) === Number(this.projectId);
                if (result.success) {
                    if (isCurrentProject) successMessage = result.message || successMessage;
                    task && this.$set(task, 'baktime', $A.cloneJSON(task.time));
                } else {
                    errors.push(`#${result.item.id}: ${result.message || this.$L('保存失败')}`);
                    if (!isCurrentProject) {
                        return;
                    }
                    failedIds.add(Number(result.item.id));
                    // Keep the proposed bar and edit row so the user can retry or cancel it.
                    task && this.$set(task, 'time', $A.cloneJSON(result.item.newTime));
                }
            });
            this.editData = this.editData.filter(item =>
                Number(item.project_id || this.projectId) === Number(this.projectId)
                && failedIds.has(Number(item.id))
            );
            this.editLoad = 0;
            if (errors.length) {
                $A.modalError(errors.find(Boolean) || this.$L('保存失败'));
            } else if (successMessage) {
                $A.messageSuccess(successMessage);
            }
        },

        getTimeObj(taskData) {
            let start = taskData.start_at
                ? $A.dayjsUtc(this.ganttTimestamp(taskData.start_at))
                : $A.dayjsUtc(this.ganttTimestamp(taskData.created_at)).startOf('day');
            let end = taskData.end_at ? $A.dayjsUtc(this.ganttTimestamp(taskData.end_at)) : start.clone();
            if (end.unix() == start.unix()) {
                end = end.endOf('day');
            }
            return {
                start: start.valueOf(),
                end: Math.max(end.valueOf(), start.valueOf() + 60000)
            };
        },

        onSwitchColumn(e) {
            this.filtrProjectId = $A.runNum(e);
            this.initData();
        },
    }
}
</script>
