<template>
    <Modal :value="visible" :title="$L('项目里程碑')" :mask-closable="false" width="680" @input="onVisibleChange">
        <div class="project-milestone-manager">
            <div class="project-milestone-manager-toolbar">
                <p class="form-tip">{{$L('里程碑用于标记项目交付目标；任务可以关联一个里程碑。')}}</p>
                <Button size="small" type="text" :loading="loading" @click="$emit('refresh')">{{$L('重新加载')}}</Button>
            </div>
            <Form v-if="canManage" :model="form" v-bind="formOptions" @submit.native.prevent>
                <FormItem :label="$L('名称')">
                    <Input v-model="form.name" :maxlength="100" :placeholder="$L('请输入里程碑名称')"/>
                </FormItem>
                <FormItem :label="$L('说明')">
                    <Input v-model="form.description" type="textarea" :autosize="{ minRows: 2, maxRows: 3 }" :maxlength="500" :placeholder="$L('选填')"/>
                </FormItem>
                <FormItem :label="$L('负责人')">
                    <UserSelect v-model="form.owner_userids" :project-id="projectId" :multiple-max="1" :title="$L('选择负责人')"/>
                </FormItem>
                <FormItem :label="$L('目标日期')">
                    <DatePicker v-model="form.target_at" type="date" format="yyyy-MM-dd" :placeholder="$L('选填')" clearable transfer/>
                </FormItem>
                <FormItem :label="$L('提醒')">
                    <Select v-model="form.reminder_days" :disabled="!form.target_at" style="width:180px">
                        <Option :value="-1">{{$L('关闭提醒')}}</Option>
                        <Option :value="0">{{$L('目标日期当天提醒')}}</Option>
                        <Option :value="1">{{$L('提前(*)天提醒', 1)}}</Option>
                        <Option :value="3">{{$L('提前(*)天提醒', 3)}}</Option>
                        <Option :value="7">{{$L('提前(*)天提醒', 7)}}</Option>
                    </Select>
                    <div class="form-tip">{{$L('提醒默认关闭；设置目标日期后可开启，并通过项目群聊发送一次弱提醒。')}}</div>
                </FormItem>
                <FormItem :label="$L('状态')">
                    <Select v-model="form.status" style="width:180px">
                        <Option value="open">{{$L('进行中')}}</Option>
                        <Option value="completed">{{$L('已完成')}}</Option>
                    </Select>
                </FormItem>
                <div class="adaption">
                    <Button v-if="form.id" type="default" :disabled="saving" @click="resetForm">{{$L('取消编辑')}}</Button>
                    <Button type="primary" :loading="saving" @click="save">{{$L(form.id ? '保存修改' : '添加里程碑')}}</Button>
                </div>
            </Form>
            <div v-if="loading" class="form-tip">{{$L('加载中...')}}</div>
            <div v-else-if="loadFailed" class="project-capacity-load-state">
                <span><Icon type="md-warning"/> {{$L('数据加载失败')}}</span>
                <Button size="small" type="text" :disabled="loading" @click="$emit('refresh')">{{$L('重试')}}</Button>
            </div>
            <div v-else-if="milestones.length === 0" class="form-tip">{{$L('暂无里程碑')}}</div>
            <Draggable
                v-else
                class="project-milestone-list"
                tag="ul"
                :list="milestones"
                :animation="150"
                :disabled="!canManage || sortSaving"
                item-key="id"
                handle=".project-milestone-drag-handle"
                @end="saveSort">
                <li v-for="item in milestones" :key="item.id">
                    <div class="project-milestone-main">
                        <div class="project-milestone-heading">
                            <strong>{{item.name}}</strong>
                            <UserAvatar v-if="item.owner_userid" :userid="item.owner_userid" :size="20" showName/>
                            <span v-if="item.target_at">{{item.target_at}}</span>
                            <Tag :color="item.status === 'completed' ? 'success' : 'default'">{{$L(item.status === 'completed' ? '已完成' : '进行中')}}</Tag>
                        </div>
                        <p v-if="item.description">{{item.description}}</p>
                        <div class="project-milestone-stats">
                            <span>{{$L('关联任务')}} {{item.task_count || 0}}</span>
                            <span>{{$L('已完成')}} {{item.completed_count || 0}}</span>
                            <span>{{$L('逾期')}} {{item.overdue_count || 0}}</span>
                            <span>{{$L('完成率')}} {{item.progress_percent || 0}}%</span>
                            <Button size="small" type="text" :loading="taskLoading && taskId === item.id" @click="toggleTasks(item)">{{$L(taskId === item.id ? '收起任务' : '查看任务')}}</Button>
                        </div>
                        <div v-if="canManage && item.reminder_days >= 0 && item.target_at" class="project-milestone-reminder-state">
                            <template v-if="item.reminded_at">{{$L('已发送')}} · {{item.reminded_at}}</template>
                            <template v-else-if="item.reminder_attempt_count">
                                {{$L('重试次数')}} {{item.reminder_attempt_count}} · {{item.reminder_last_attempt_at}} ·
                                {{reminderErrorText(item)}}
                            </template>
                            <template v-else>{{$L('等待提醒')}}</template>
                            <Button size="small" type="text" @click="openReminder(item)">{{$L('提醒记录')}}</Button>
                        </div>
                        <div v-if="item.flow_statuses && item.flow_statuses.length" class="project-milestone-statuses">
                            <Tag v-for="status in item.flow_statuses" :key="status.name" :color="status.color || 'default'">{{status.name}} {{status.count}}</Tag>
                        </div>
                        <div v-if="taskId === item.id" class="project-milestone-tasks">
                            <div v-if="taskLoading && tasks.length === 0" class="form-tip">{{$L('加载中...')}}</div>
                            <div v-else-if="taskLoadFailed" class="project-capacity-load-state">
                                <span><Icon type="md-warning"/> {{$L('数据加载失败')}}</span>
                                <Button size="small" type="text" :disabled="taskLoading" @click="loadTasks(tasks.length === 0)">{{$L('重试')}}</Button>
                            </div>
                            <div v-else-if="tasks.length === 0" class="form-tip">{{$L('没有关联任务')}}</div>
                            <ul v-else>
                                <li v-for="task in tasks" :key="task.id" @click="$emit('open-task', task)">
                                    <span class="project-milestone-task-name">#{{task.id}} {{task.name}}</span>
                                    <span class="project-milestone-task-meta">
                                        <Tag v-if="task.complete_at" color="success">{{$L('已完成')}}</Tag>
                                        <Tag v-else-if="task.flow_item_name" :color="task.flow_item_color || 'default'">{{task.flow_item_name}}</Tag>
                                        <Tag v-else-if="task.overdue" color="error">{{$L('逾期')}}</Tag>
                                        <span v-if="task.column_name">{{task.column_name}}</span>
                                        <span v-if="task.end_at">{{task.end_at.slice(0, 10)}}</span>
                                    </span>
                                </li>
                            </ul>
                            <Button v-if="!taskLoadFailed && tasks.length < taskTotal" size="small" long :loading="taskLoading" @click="loadTasks(false)">{{$L('加载更多')}}</Button>
                        </div>
                    </div>
                    <div v-if="canManage" class="project-milestone-actions">
                        <span class="project-milestone-drag-handle" :title="$L('拖拽调整排序')"><Icon type="md-menu" /></span>
                        <Button size="small" @click="edit(item)">{{$L('编辑')}}</Button>
                        <Poptip confirm :title="$L('删除后将解除该里程碑与所有任务的关联，确定继续吗？')" :ok-text="$L('删除')" :cancel-text="$L('取消')" @on-ok="remove(item)">
                            <Button size="small" type="error">{{$L('删除')}}</Button>
                        </Poptip>
                    </div>
                </li>
            </Draggable>
        </div>
        <div slot="footer" class="adaption">
            <Button type="default" @click="onVisibleChange(false)">{{$L('关闭')}}</Button>
        </div>

        <MilestoneReminderAttempts
            :visible.sync="reminderShow"
            :project-id="projectId"
            :milestone="reminderMilestone" />
    </Modal>
</template>

<script>
import Draggable from 'vuedraggable';
import {mapState} from "vuex";
import UserSelect from "../../../components/UserSelect.vue";
import MilestoneReminderAttempts from "./MilestoneReminderAttempts.vue";
import emitter from "../../../store/events";

export default {
    name: "MilestoneManager",

    components: {
        Draggable,
        UserSelect,
        MilestoneReminderAttempts,
    },

    props: {
        visible: {type: Boolean, default: false},
        projectId: {type: [Number, String], default: 0},
        milestones: {type: Array, default: () => []},
        canManage: {type: Boolean, default: false},
        loading: {type: Boolean, default: false},
        loadFailed: {type: Boolean, default: false},
    },

    data() {
        return {
            form: this.emptyForm(),
            saving: false,
            sortSaving: false,
            taskId: 0,
            tasks: [],
            taskTotal: 0,
            taskPage: 1,
            taskLoading: false,
            taskLoadFailed: false,
            reminderShow: false,
            reminderMilestone: null,
        };
    },

    computed: {
        ...mapState(['formOptions']),
    },

    watch: {
        canManage(value) {
            if (!value) this.resetForm();
        },
        milestones() {
            if (this.taskId && !this.milestones.some(item => Number(item.id) === Number(this.taskId))) {
                this.resetTasks();
            }
        },
    },

    created() {
        this._saveRequestKey = 0;
        this._sortRequestKey = 0;
        this._taskRequestKey = 0;
    },

    methods: {
        emptyForm() {
            return {id: 0, name: '', description: '', owner_userids: [], target_at: '', status: 'open', reminder_days: -1};
        },

        onVisibleChange(value) {
            this.$emit('update:visible', value);
        },

        resetForm() {
            this.form = this.emptyForm();
        },

        resetTasks() {
            this._taskRequestKey++;
            this.taskId = 0;
            this.tasks = [];
            this.taskTotal = 0;
            this.taskPage = 1;
            this.taskLoading = false;
            this.taskLoadFailed = false;
        },

        edit(item) {
            if (!this.canManage || !item || !this.milestones.some(({id}) => Number(id) === Number(item.id))) return;
            this.form = {
                ...item,
                owner_userids: item.owner_userid ? [Number(item.owner_userid)] : [],
                target_at: item.target_at ? $A.dayjs(item.target_at).toDate() : '',
            };
        },

        async save() {
            if (this.saving) return;
            if (!this.form.name.trim()) {
                $A.modalError(this.$L('请输入里程碑名称'));
                return;
            }
            const projectId = Number(this.projectId);
            const form = {...this.form};
            const submittedForm = JSON.stringify(form);
            const requestKey = this._saveRequestKey = (this._saveRequestKey || 0) + 1;
            this.saving = true;
            try {
                const targetAt = form.target_at
                    ? $A.dayjs(form.target_at).format('YYYY-MM-DD')
                    : '';
                const {data} = await this.$store.dispatch("call", {
                    url: 'projectmilestone/save',
                    data: {
                        project_id: projectId,
                        milestone_id: form.id || 0,
                        name: form.name,
                        description: form.description,
                        target_at: targetAt,
                        status: form.status,
                        reminder_days: form.reminder_days,
                        owner_userid: Number(form.owner_userids[0]) || 0,
                    },
                });
                emitter.emit('projectMilestonesUpdate', projectId);
                if (requestKey === this._saveRequestKey && projectId === Number(this.projectId)) {
                    if (JSON.stringify(this.form) === submittedForm) {
                        this.resetForm();
                    } else if (!form.id && data && data.id) {
                        // Keep edits made while the create request was in flight attached to
                        // the newly created milestone instead of creating a duplicate.
                        this.$set(this.form, 'id', Number(data.id));
                    }
                }
            } catch ({msg}) {
                if (projectId === Number(this.projectId)) $A.modalError(msg);
            } finally {
                if (requestKey === this._saveRequestKey) this.saving = false;
            }
        },

        async remove(item) {
            if (!this.canManage || !item || !this.milestones.some(({id}) => Number(id) === Number(item.id))) return;
            const projectId = Number(this.projectId);
            const milestoneId = Number(item.id);
            try {
                await this.$store.dispatch("call", {
                    url: 'projectmilestone/delete',
                    data: {project_id: projectId, milestone_id: milestoneId},
                });
                if (projectId === Number(this.projectId) && Number(this.form.id) === milestoneId) this.resetForm();
                emitter.emit('projectMilestonesUpdate', projectId);
            } catch ({msg}) {
                if (projectId === Number(this.projectId)) $A.modalError(msg);
            }
        },

        async saveSort() {
            if (!this.canManage || this.sortSaving) return;
            const projectId = Number(this.projectId);
            const requestKey = this._sortRequestKey = (this._sortRequestKey || 0) + 1;
            this.sortSaving = true;
            try {
                const {msg} = await this.$store.dispatch("call", {
                    url: 'projectmilestone/sort',
                    data: {project_id: projectId, milestones: this.milestones.map(item => Number(item.id))},
                });
                if (requestKey === this._sortRequestKey && projectId === Number(this.projectId)) {
                    $A.messageSuccess(msg);
                    emitter.emit('projectMilestonesUpdate', projectId);
                }
            } catch ({msg}) {
                if (requestKey === this._sortRequestKey && projectId === Number(this.projectId)) {
                    $A.modalError(msg);
                    this.$emit('refresh');
                }
            } finally {
                if (requestKey === this._sortRequestKey) this.sortSaving = false;
            }
        },

        async toggleTasks(item) {
            this._taskRequestKey++;
            this.taskLoading = false;
            if (Number(this.taskId) === Number(item.id)) {
                this.resetTasks();
                return;
            }
            this.taskId = Number(item.id);
            this.tasks = [];
            this.taskTotal = 0;
            this.taskPage = 1;
            await this.loadTasks(true);
        },

        async loadTasks(reset = false) {
            const milestoneId = Number(this.taskId);
            if (!milestoneId || this.taskLoading) return;
            const requestKey = this._taskRequestKey = (this._taskRequestKey || 0) + 1;
            const projectId = Number(this.projectId);
            const page = reset ? 1 : this.taskPage + 1;
            this.taskLoading = true;
            this.taskLoadFailed = false;
            try {
                const {data} = await this.$store.dispatch("call", {
                    url: 'projectmilestone/tasks',
                    method: 'post',
                    data: {project_id: projectId, milestone_id: milestoneId, page, per_page: 20},
                });
                if (requestKey !== this._taskRequestKey
                    || projectId !== Number(this.projectId)
                    || milestoneId !== Number(this.taskId)) return;
                this.tasks = reset ? (data.list || []) : [...this.tasks, ...(data.list || [])];
                this.taskTotal = Number(data.total) || 0;
                this.taskPage = Number(data.page) || page;
            } catch (e) {
                if (requestKey === this._taskRequestKey
                    && projectId === Number(this.projectId)
                    && milestoneId === Number(this.taskId)) {
                    this.taskLoadFailed = true;
                }
            } finally {
                if (requestKey === this._taskRequestKey) this.taskLoading = false;
            }
        },

        openReminder(item) {
            if (!this.canManage || !item || !item.id) return;
            this.reminderMilestone = {id: Number(item.id), name: item.name || ''};
            this.reminderShow = true;
        },

        reminderErrorText(item) {
            if (item.reminder_last_error_code === 'bot_unavailable') return this.$L('提醒机器人不可用');
            if (item.reminder_last_error_code === 'chat_unavailable') return this.$L('项目群聊不可用');
            return this.$L('提醒消息投递失败');
        },
    },
};
</script>
