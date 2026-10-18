<template>
    <div class="task-add">
        <div class="head" :class="{empty:addData.cascader.length == 0,visible:cascaderShow}">
            <Cascader
                v-model="addData.cascader"
                :data="cascaderData"
                :clearable="false"
                :placeholder="$L('请选择项目')"
                :load-data="cascaderLoadData"
                @on-change="cascaderChange"
                @on-input-change="cascaderInputChange"
                @on-visible-change="cascaderShow=!cascaderShow"
                filterable/>
        </div>
        <ul v-if="taskTemplateList.length > 0 || hasMoreTemplates" class="task-add-template">
            <li
                v-for="item in taskTemplateList"
                :key="item.id"
                :class="{active: templateActiveID === item.id, cross: item.project_id != addData.project_id}"
                :title="item.project_id != addData.project_id ? $L('来自(*)', item.project_name || '') : ''"
                @click="setTaskTemplate(item)">
                {{ item.name }}
            </li>
            <li
                v-if="hasMoreTemplates"
                @click="openTemplateBrowser">
                {{ $L('更多') }}...
            </li>
        </ul>
        <div class="task-add-form">
            <div class="title">
                <Input
                    v-model="addData.name"
                    ref="input"
                    type="textarea"
                    :rows="1"
                    :autosize="{ minRows: 1, maxRows: 8 }"
                    :maxlength="255"
                    :placeholder="$L('任务描述')"
                    enterkeyhint="done"
                    @on-keydown="onKeydown"/>
                <div v-if="aiVisible" class="ai-btn" @click="onAI">
                    <i class="taskfont">&#xe8a1;</i>
                </div>
            </div>
            <TEditorTask
                ref="editorTaskRef"
                class="desc"
                v-model="addData.content"
                :placeholder="$L(windowLandscape ? '详细描述，选填...（点击右键使用工具栏）' : '详细描述，选填...')"
                :placeholderFull="$L('详细描述...')"/>
            <div class="advanced-option" :class="{'advanced-open': advanced}">
                <Button @click="advanced=!advanced">{{$L('高级选项')}}</Button>
                <ul class="advanced-priority">
                    <li v-for="(item, key) in taskPriority" :key="key">
                        <ETooltip :disabled="$isEEUIApp || windowTouch" :content="taskPriorityContent(item)">
                            <i
                                class="taskfont"
                                :style="{color:item.color}"
                                v-html="addData.p_name == item.name ? '&#xe61d;' : '&#xe61c;'"
                                @click="choosePriority(item)"></i>
                        </ETooltip>
                    </li>
                </ul>
                <DatePicker
                    v-if="taskDays > 0 || taskTimeOpen"
                    :open="taskTimeOpen"
                    v-model="addData.times"
                    :options="timeOptions"
                    :placeholder="$L('选择计划范围')"
                    format="yyyy/MM/dd HH:mm"
                    type="datetimerange"
                    placement="bottom"
                    @on-change="taskTimeChange(addData)"
                    @on-open-change="taskTimeOpenChange">
                    <div class="advanced-time" @click="taskTimeOpenChange(!taskTimeOpen)">
                        <Icon type="ios-clock-outline" />
                        <em type="primary" :style="addData.p_color ? {backgroundColor:addData.p_color} : {}">{{taskDays}}</em>
                    </div>
                </DatePicker>
            </div>
        </div>

        <Form v-if="advanced" class="task-add-advanced" v-bind="formOptions" @submit.native.prevent>
            <FormItem :label="$L('计划时间')">
                <DatePicker
                    v-model="addData.times"
                    :options="timeOptions"
                    :editable="false"
                    :placeholder="$L('选择计划范围')"
                    format="yyyy/MM/dd HH:mm"
                    type="datetimerange"
                    @on-change="taskTimeChange(addData)"/>
            </FormItem>
            <FormItem :label="$L('预估工时')">
                <InputNumber v-model="addData.estimated_minutes" :min="0" :max="1000000" :step="30" :placeholder="$L('分钟')" />
            </FormItem>
            <FormItem :label="$L('负责人')" >
                <UserSelect
                    v-model="addData.owner"
                    contextmenu-remove
                    :multiple-max="10"
                    :title="$L('选择任务负责人')"
                    :project-id="addData.project_id"
                    :avatar-size="24"
                    @input="syncTemplateSubtaskOwners"
                    border/>
            </FormItem>
            <FormItem :label="$L('协助人员')" >
                <UserSelect
                    v-model="addData.assist"
                    contextmenu-remove
                    :multiple-max="10"
                    :title="$L('选择任务协助人员')"
                    :project-id="addData.project_id"
                    :disabled-choice="addData.owner"
                    :avatar-size="24"
                    border/>
            </FormItem>
            <FormItem>
                <div slot="label" class="visibility-text" @click="showCisibleDropdown">
                    {{ $L('可见性') }}
                    <i class="taskfont">&#xe740;</i>
                </div>
                <div
                    v-if="addData.visibility_appoint == 1 || addData.visibility_appoint == 2"
                    ref="visibilityText"
                    class="ivu-input task-add-visibility"
                    @click="showCisibleDropdown">
                    {{ addData.visibility_appoint == 1 ? $L('项目人员可见') : $L('任务人员可见') }}
                </div>
                <UserSelect
                    v-else
                    ref="visibleUserSelectRef"
                    v-model="addData.visibility_appointor"
                    :avatar-size="24"
                    :title="$L('选择指定人员')"
                    :project-id="addData.project_id"
                    @on-show-change="visibleUserSelectShowChange"
                    border/>
            </FormItem>
            <div class="subtasks">
                <div v-if="addData.subtasks.length > 0" class="sublist">
                    <Row>
                        <Col span="12">
                            <Checkbox v-model="allSubtasksSelected" :indeterminate="hasPartialSubtaskSelection">{{$L('全选')}}</Checkbox>
                            {{$L('任务描述')}}
                        </Col>
                        <Col span="8">{{$L('计划时间')}}</Col>
                        <Col span="4">{{$L('负责人')}}</Col>
                    </Row>
                    <Row v-for="row in subtaskRows" :key="row.path">
                        <Col span="12">
                            <div class="subtask-name-row" :style="{paddingLeft: `${row.depth * 16}px`}">
                                <Checkbox v-model="row.item._createSubtask" @on-change="onSubtaskSelectionChange(row, $event)">{{$L('创建')}}</Checkbox>
                                <Input
                                    v-model="row.item.name"
                                    :maxlength="255"
                                    clearable
                                    @on-clear="removeTaskSubtask(row)"/>
                                <Button type="text" icon="md-add" :disabled="!canAddTaskChild(row)" @click="addTaskSubtask(row.item)"/>
                                <Button type="text" icon="md-close" @click="removeTaskSubtask(row)"/>
                            </div>
                        </Col>
                        <Col span="8" :title="timeTitle(row.item.times)">
                            <DatePicker
                                v-model="row.item.times"
                                :options="timeOptions"
                                :editable="false"
                                :placeholder="$L('选择时间')"
                                format="yyyy/MM/dd HH:mm"
                                type="datetimerange"
                                @on-change="taskTimeChange(row.item)"/>
                        </Col>
                        <Col span="4">
                            <UserSelect
                                v-model="row.item.owner"
                                contextmenu-remove
                                :multiple-max="10"
                                :title="$L('选择负责人')"
                                :project-id="addData.project_id"
                                :avatar-size="24"
                                @input="row.item._templateOwnerStrategy = 'custom'"
                                border/>
                        </Col>
                    </Row>
                    <Button v-if="subtaskRows.length < 50" size="small" icon="md-add" @click="addTaskSubtask()">{{$L('添加子任务')}}</Button>
                </div>
                <Input
                    type="text"
                    v-model="subName"
                    :class="['enter-input', subName == '' ? 'empty' : '']"
                    @on-keydown="onSubNameKeydown"
                    :placeholder="$L('+ 输入子任务，回车添加子任务')"/>
            </div>
        </Form>

        <div class="ivu-modal-footer">
            <div class="adaption">
                <Button type="default" @click="close">{{$L('取消')}}</Button>
                <ButtonGroup class="page-manage-add-task-button-group">
                    <Button type="primary" :loading="loadIng > 0" @click="onAdd(false)">{{$L('添加任务')}}</Button>
                    <Dropdown @on-click="onAdd(true)" transfer>
                        <Button type="primary">
                            <Icon type="ios-arrow-down"></Icon>
                        </Button>
                        <DropdownMenu slot="list">
                            <DropdownItem :disabled="loadIng > 0">{{$L('提交继续添加')}}</DropdownItem>
                        </DropdownMenu>
                    </Dropdown>
                </ButtonGroup>
            </div>
        </div>

        <TaskExistTips ref="taskExistTipsRef" @onContinue="onAdd(addContinue, true)"/>
        <TaskTemplateBrowser v-if="taskTemplateShareEnabled" v-model="templateBrowserVisible" :current-project-id="addData.project_id" @pick="onPickFromBrowser" />
    </div>
</template>

<script>
import {mapGetters, mapState} from "vuex";
import emitter from "../../../store/events";
import UserSelect from "../../../components/UserSelect.vue";
import TaskExistTips from "./TaskExistTips.vue";
import TEditorTask from "../../../components/TEditorTask.vue";
import nostyle from "../../../components/VMEditor/engine/nostyle";
import {MarkdownConver} from "../../../utils/markdown";
import {extractPlainText} from "../../../utils/text";
import {AINormalizeJsonContent, TASK_AI_SYSTEM_PROMPT, withLanguagePreferencePrompt} from "../../../utils/ai";
import TaskTemplateBrowser from './TaskTemplateBrowser.vue'

export default {
    name: "TaskAdd",
    components: {TEditorTask, UserSelect, TaskExistTips, TaskTemplateBrowser},
    props: {
        value: {
            type: Boolean,
            default: false
        },
    },
    data() {
        return {
            addData: {
                // 基本信息
                cascader: [],
                name: "",
                content: "",
                owner: [],
                assist: [],
                project_id: 0,
                column_id: 0,
                times: [],
                milestone_name: '',
                estimated_minutes: null,
                subtasks: [],
                // 优先级
                p_level: 0,
                p_name: '',
                p_color: '',
                // 可见性
                visibility_appoint: 1,
                visibility_appointor: [],
            },
            addDefault: {},

            cascaderShow: false,
            cascaderData: [],
            cascaderValue: '',
            cascaderLoading: 0,
            cascaderAlready: [],

            advanced: false,
            subName: '',

            taskTimeOpen: false,

            timeOptions: {shortcuts: $A.timeOptionShortcuts()},

            loadIng: 0,
            isMounted: false,

            beforeClose: [],

            addContinue: false,

            templateActiveID: 0,
            activeTemplateSource: null,
            templateApplySequence: 0,
            templateLoadSequence: 0,
            templateCompareData: {name: '', content: '', estimated_minutes: null, subtasks: []},
            templateBrowserVisible: false,
        }
    },

    created() {
        this.addDefault = $A.cloneJSON(this.addData);
    },

    async mounted() {
        this.initCascaderData();
        await this.initProjectData();
        this.$nextTick(() => {
            this.$refs.input.focus();
            this.templateCompareData = this.captureTemplateCompareData();
        })
        this.isMounted = true
    },

    beforeDestroy() {
        this.beforeClose.some(func => {
            typeof func === "function" && func()
        })
        this.beforeClose = [];
    },

    computed: {
        ...mapGetters(['aiVisible']),

        ...mapState(['cacheProjects', 'projectId', 'cacheColumns', 'taskPriority', 'taskTemplates', 'formOptions']),

        taskTemplateShareEnabled() {
            const project = (this.cacheProjects || []).find(({id}) => id == this.addData.project_id)
            return !project || project.task_template_share !== 'close'
        },

        allSubtasksSelected: {
            get() {
                return this.subtaskRows.length > 0 && this.subtaskRows.every(({item}) => item._createSubtask !== false)
            },
            set(selected) {
                this.subtaskRows.forEach(({item}) => this.$set(item, '_createSubtask', selected))
            },
        },

        hasPartialSubtaskSelection() {
            return this.subtaskRows.some(({item}) => item._createSubtask === false)
                && this.subtaskRows.some(({item}) => item._createSubtask !== false)
        },

        subtaskRows() {
            return this.flattenSubtasks(this.addData.subtasks)
        },

        taskDays() {
            const {times} = this.addData;
            const temp = $A.newDateString(times, "YYYY-MM-DD HH:mm");
            if (temp[0] && temp[1]) {
                const d = Math.ceil($A.dayjs(temp[1]).diff(temp[0], 'day', true));
                if (d > 0) {
                    return d;
                }
            }
            return 0;
        },

        /**
         * Chip 区显示规则：
         * - 情况 A：本项目有模板 → 显示本项目全部模板（按 sort）
         * - 情况 B：本项目无模板 → 显示其他项目前 5 个（按 use_count desc）
         * - 完全无可见模板 → 空数组（外层 v-if 隐藏整块）
         */
        taskTemplateList() {
            const all = this.taskTemplates || []
            const currentId = this.addData.project_id
            const ownTemplates = all.filter(t => t.project_id == currentId)
            if (!this.taskTemplateShareEnabled) {
                return [...ownTemplates].sort((a, b) => (a.sort || 0) - (b.sort || 0) || a.id - b.id)
            }
            if (ownTemplates.length > 0) {
                return [...ownTemplates].sort((a, b) => (a.sort || 0) - (b.sort || 0) || a.id - b.id)
            }
            const others = all.filter(t => t.project_id != currentId)
            return [...others]
                .sort((a, b) => (b.use_count || 0) - (a.use_count || 0))
                .slice(0, 3)
        },

        /**
         * 是否存在"未在 chip 区展示的可见模板"——决定"更多"按钮显隐。
         */
        hasMoreTemplates() {
            if (!this.taskTemplateShareEnabled) {
                return false
            }
            const all = this.taskTemplates || []
            const currentId = this.addData.project_id
            const ownCount = all.filter(t => t.project_id == currentId).length
            const otherCount = all.filter(t => t.project_id != currentId).length
            if (ownCount > 0) {
                return otherCount > 0
            }
            return otherCount > 3
        },
    },

    watch: {
        'addData.owner'(owner, newOwner) {
            if (JSON.stringify(owner) === JSON.stringify(newOwner)) {
                return;
            }
            this.addData.assist = this.addData.assist.filter(item => {
                return owner.indexOf(item) === -1;
            })
            if (this.addData.assist.length === 0 && owner.indexOf(this.userId) === -1) {
                this.addData.assist = [this.userId];
            }
        },
        'addData.project_id'(projectId) {
            const requestSequence = ++this.templateLoadSequence;
            ++this.templateApplySequence;
            this.templateActiveID = 0;
            this.activeTemplateSource = null;
            if (projectId > 0) {
                $A.IDBSave("cacheAddTaskProjectId", projectId);
                this.$store.dispatch("updateTaskTemplates", projectId).then(() => {
                    if (requestSequence === this.templateLoadSequence && projectId == this.addData.project_id) {
                        this.setTaskDefaultTemplate()
                    }
                })
            }
        },
        'addData.column_id'(columnId) {
            if (columnId > 0) {
                $A.IDBSave("cacheAddTaskColumnId", columnId);
            }
            const {project_id} = this.addData;
            if (project_id && columnId) {
                this.$set(this.addData, 'cascader', [project_id, columnId]);
            } else {
                this.$set(this.addData, 'cascader', []);
            }
        }
    },

    methods: {
        /**
         * 初始化级联数据
         */
        initCascaderData() {
            const data = $A.cloneJSON(this.cacheProjects).sort((a, b) => {
                if (a.top_at || b.top_at) {
                    return $A.sortDay(b.top_at, a.top_at);
                }
                return b.id - a.id;
            });
            this.cascaderData = data.map(project => {
                const children = this.cacheColumns.filter(({project_id}) => project_id == project.id).map(column => {
                    return {
                        value: column.id,
                        label: column.name
                    }
                });
                const data = {
                    value: project.id,
                    label: project.name,
                    children,
                };
                if (children.length == 0) {
                    data.loading = false;
                }
                return data
            });
        },

        /**
         * 初始化项目、列表、优先级
         */
        async initProjectData() {
            // 项目、列表
            let cacheAddTaskProjectId = await $A.IDBInt("cacheAddTaskProjectId");
            let project = this.cacheProjects.find(({id}) => id == this.projectId)
                || this.cacheProjects.find(({id}) => id == cacheAddTaskProjectId)
                || this.cacheProjects.find(({id}) => id > 0);
            if (project) {
                let cacheAddTaskColumnId = await $A.IDBInt("cacheAddTaskColumnId");
                let column = this.cacheColumns.find(({project_id, id}) => project_id == project.id && id == cacheAddTaskColumnId)
                    || this.cacheColumns.find(({project_id}) => project_id == project.id);
                if (column) {
                    this.addData.project_id = column.project_id;
                    this.addData.column_id = column.id;
                } else {
                    this.$store.dispatch("getColumns", project.id).then(() => {
                        column = this.cacheColumns.find(({project_id, id}) => project_id == project.id && id == cacheAddTaskColumnId)
                            || this.cacheColumns.find(({project_id}) => project_id == project.id);
                        if (column) {
                            this.addData.project_id = column.project_id;
                            this.addData.column_id = column.id;
                        }
                    }).catch(() => {});
                }
            }
            // 优先级
            if (this.taskPriority.length > 0) {
                const defaultItem = this.taskPriority.find(item => item.is_default === 1) || this.taskPriority[0];
                await this.choosePriority(defaultItem);
            }
        },

        async taskTimeChange(data) {
            const times = $A.newDateString(data.times, "YYYY-MM-DD HH:mm");
            this.$set(data, 'times', await this.$store.dispatch("taskDefaultTime", times))
            if (data === this.addData) {
                await this.applyTemplateSubtaskDates()
            } else {
                this.$set(data, '_templateSchedule', false)
                await this.applyTemplateSubtaskDates(data.children, data.times)
            }
        },

        flattenSubtasks(items, depth = 0, rows = [], ancestors = [], path = 'subtask') {
            (items || []).forEach((item, index) => {
                const currentPath = `${path}.${index}`
                rows.push({item, depth, ancestors, path: currentPath})
                this.flattenSubtasks(item.children, depth + 1, rows, [...ancestors, item], currentPath)
            })
            return rows
        },

        canAddTaskChild(row) {
            return row.depth < 4 && this.subtaskRows.length < 50
        },

        addTaskSubtask(parent = null) {
            const subtask = {name: '', owner: [this.userId], times: [], children: [], _createSubtask: true}
            if (parent && !Array.isArray(parent.children)) this.$set(parent, 'children', [])
            ;(parent ? parent.children : this.addData.subtasks).push(subtask)
        },

        removeTaskSubtask(row) {
            const {item, ancestors} = row
            const list = ancestors.length ? (ancestors[ancestors.length - 1].children || []) : this.addData.subtasks
            const index = list.indexOf(item)
            if (index !== -1) list.splice(index, 1)
        },

        onSubtaskSelectionChange(row, selected) {
            if (selected) {
                row.ancestors.forEach(parent => this.$set(parent, '_createSubtask', true))
                return
            }
            const deselectChildren = items => (items || []).forEach(item => {
                this.$set(item, '_createSubtask', false)
                deselectChildren(item.children)
            })
            deselectChildren(row.item.children)
        },

        serializeTaskSubtasks(items, parentOwner) {
            return (items || [])
                .filter(item => item._createSubtask !== false && item.name && item.name.trim())
                .map(item => {
                    const owner = item._templateOwnerStrategy === 'parent'
                        ? [...(parentOwner || [])]
                        : [...(item.owner || [])]
                    const subtask = {
                        name: item.name.trim(),
                        milestone_name: item.milestone_name || '',
                        owner,
                        times: item.times || [],
                        subtasks: this.serializeTaskSubtasks(item.children, owner),
                    }
                    return subtask
                })
        },

        syncTemplateSubtaskOwners(subtasks = this.addData.subtasks, parentOwner = this.addData.owner) {
            const owner = Array.isArray(parentOwner) ? [...parentOwner] : [];
            (subtasks || []).forEach(subtask => {
                if (subtask._templateOwnerStrategy === 'parent') {
                    this.$set(subtask, 'owner', [...owner])
                }
                this.syncTemplateSubtaskOwners(subtask.children, subtask.owner)
            })
        },

        async applyTemplateSubtaskDates(subtasks = this.addData.subtasks, parentTimes = this.addData.times || []) {
            const hasParentRange = Array.isArray(parentTimes) && parentTimes.length === 2 && parentTimes[0] && parentTimes[1]
            for (const subtask of (subtasks || [])) {
                if (subtask._templateSchedule) {
                    if (!hasParentRange) {
                        this.$set(subtask, 'times', [])
                    } else {
                        const startOffset = Number(subtask._templateSchedule.start_offset_days)
                        const endOffset = Number(subtask._templateSchedule.end_offset_days)
                        const parentStart = $A.daytz(parentTimes[0])
                        const parentEnd = $A.daytz(parentTimes[1])
                        const childStart = parentStart.clone().add(startOffset, 'day')
                        const childEnd = parentStart.clone().add(endOffset, 'day')
                            .hour(parentEnd.hour()).minute(parentEnd.minute()).second(parentEnd.second())
                        const times = [childStart.format('YYYY-MM-DD HH:mm'), childEnd.format('YYYY-MM-DD HH:mm')]
                        this.$set(subtask, 'times', await this.$store.dispatch('taskDefaultTime', times))
                    }
                }
                const childParentTimes = Array.isArray(subtask.times) && subtask.times.length === 2 && subtask.times[0] && subtask.times[1]
                    ? subtask.times
                    : parentTimes
                await this.applyTemplateSubtaskDates(subtask.children, childParentTimes)
            }
        },

        taskTimeOpenChange(val) {
            this.taskTimeOpen = val;
        },

        timeTitle(value) {
            return value ? $A.newDateString(value) : null
        },

        onKeydown(e) {
            if (e.keyCode === 13) {
                if (e.shiftKey) {
                    return;
                }
                e.preventDefault();
                this.onAdd();
            }
        },

        onSubNameKeydown(e) {
            if (e.isComposing || e.key === 'Process' || e.keyCode === 229) return;
            if (e.keyCode === 13) {
                this.addSubTask();
            }
        },

        addSubTask() {
            if (this.subName.trim() !== '') {
                this.addData.subtasks.push({
                    name: this.subName.trim(),
                    owner: [this.userId],
                    times: [],
                    _createSubtask: true,
                });
                this.subName = '';
            }
        },

        taskPriorityContent(item) {
            const days = $A.runNum(item.days);
            if (days <= 0) {
                return item.name + ' (' + this.$L('无时间限制') + ')';
            }
            return item.name + ' (' + days + this.$L('[day_unit].天') + ')';
        },

        async choosePriority(item) {
            const start = $A.daytz();
            const days = $A.runNum(item.days);
            if (days > 0) {
                const end = start.clone().add(days, 'day');
                this.$set(this.addData, 'times', await this.$store.dispatch("taskDefaultTime", $A.newDateString([start, end], 'YYYY-MM-DD 00:00')))
            } else {
                this.$set(this.addData, 'times', [])
            }
            this.$set(this.addData, 'p_level', item.priority)
            this.$set(this.addData, 'p_name', item.name)
            this.$set(this.addData, 'p_color', item.color)
        },

        cascaderLoadData(item, callback) {
            item.loading = true;
            this.$store.dispatch("getColumns", item.value).then((data) => {
                item.children = data.map(column => {
                    return {
                        value: column.id,
                        label: column.name
                    }
                });
                item.loading = false;
                callback();
            }).catch(() => {
                item.loading = false;
                callback();
            });
        },

        cascaderChange(value) {
            if (value[1]) {
                this.$set(this.addData, 'project_id', value[0])
                this.$set(this.addData, 'column_id', value[1])
            }
        },

        cascaderInputChange(key) {
            this.cascaderValue = key || "";
            //
            if (this.cascaderAlready[this.cascaderValue] === true) {
                return;
            }
            this.cascaderAlready[this.cascaderValue] = true;
            //
            setTimeout(() => {
                this.cascaderLoading++;
            }, 1000)
            this.$store.dispatch("getProjects", {
                keys: {
                    name: this.cascaderValue,
                },
                getcolumn: 'yes'
            }).then(() => {
                this.cascaderLoading--;
                this.initCascaderData();
            }).catch(() => {
                this.cascaderLoading--;
            });
        },

        setData(data) {
            if (!this.isMounted) {
                this.__setData && clearTimeout(this.__setData)
                this.__setData = setTimeout(_ => this.setData(data) , 10)
                return
            }
            if (typeof data.beforeClose !== "undefined") {
                this.beforeClose.push(data.beforeClose)
                delete data.beforeClose;
            }
            this.addData = Object.assign({}, this.addData, data);
        },

        async onAdd(continued = false, affirm = false) {
            if (!this.addData.name) {
                $A.messageError("任务描述不能为空");
                return;
            }

            // 存在任务提示
            if (!affirm && this.addData.owner.length > 0) {
                this.loadIng++;
                this.$refs.taskExistTipsRef.isExistTask({
                    userids: this.addData.owner,
                    timerange: this.addData.times
                }, 600).then(res => {
                    if (!res) {
                        this.onAdd(continued, true)
                    } else {
                        this.addContinue = continued
                    }
                    this.loadIng--;
                });
                return;
            }

            this.loadIng++;
            const selectedTemplate = this.activeTemplateSource;
            const templateCanBeRecorded = selectedTemplate
                && (this.taskTemplateShareEnabled || selectedTemplate.project_id == this.addData.project_id);
            const taskData = {
                ...this.addData,
                template_id: templateCanBeRecorded ? selectedTemplate.id : 0,
                template_version: templateCanBeRecorded ? selectedTemplate.version : 0,
                subtasks: this.serializeTaskSubtasks(this.addData.subtasks, this.addData.owner),
            }
            this.$store.dispatch("taskAdd", taskData).then(({msg}) => {
                $A.messageSuccess(msg);
                if (continued === true) {
                    this.addData = Object.assign({}, this.addData, this.templateCompareData, {subtasks: []});
                    this.$refs.input.focus();
                } else {
                    this.addData = $A.cloneJSON(this.addDefault);
                    this.close()
                }
            }).catch(({msg}) => {
                $A.modalError(msg);
            }).finally(() => {
                this.loadIng--;
            });
        },

        close() {
            this.$emit("input", !this.value)
        },

        showCisibleDropdown(event){
            const list = [
                {label: '项目人员', value: 1},
                {label: '任务人员', value: 2},
                {label: '指定成员', value: 3},
            ];
            this.$store.commit('menu/operation', {
                event,
                list,
                size: 'large',
                active: this.addData.visibility_appoint,
                onUpdate: (value) => {
                    this.dropVisible(value)
                }
            })
        },

        visibleUserSelectShowChange(isShow){
            if(!isShow && (this.addData.visibility_appointor.length == 0 || !this.addData.visibility_appointor[0])){
                let old = this.addData.old_visibility_appoint;
                this.addData.visibility_appoint = old > 2 ? 1 : (old || 1);
                if(this.addData.visibility_appoint < 3 ){
                }
            }
        },

        dropVisible(command) {
            switch (command) {
                case 1:
                case 2:
                    this.addData.visibility_appoint = command
                    break;
                case 3:
                    this.addData.old_visibility_appoint = this.addData.visibility_appoint
                    this.addData.visibility_appoint = command
                    this.$nextTick(() => {
                        this.$refs.visibleUserSelectRef.onSelection()
                    });
                    break;
            }
        },

        openTemplateBrowser() {
            if (!this.taskTemplateShareEnabled) {
                return
            }
            this.templateBrowserVisible = true
        },

        onPickFromBrowser(item) {
            this.setTaskTemplate(item)
        },

        async setTaskTemplate(item, force = false) {
            if (!this.taskTemplateShareEnabled && item.project_id != this.addData.project_id) {
                return;
            }
            const targetProjectId = this.addData.project_id;
            if (force) {
                const applySequence = ++this.templateApplySequence;
                const projectId = this.addData.project_id;
                this.templateActiveID = item.id;
                this.activeTemplateSource = {
                    id: Number(item.id),
                    version: Number(item.version) || 1,
                    project_id: Number(item.project_id),
                    name: item.name || '',
                };
                this.addData.name = item.title || '';
                this.addData.content = nostyle(item.content || '', {sanitize: false});
                this.addData.milestone_name = item.milestone_name || '';
                this.addData.estimated_minutes = item.estimated_minutes == null ? null : Number(item.estimated_minutes);
                this.addData.subtasks = this.mapTemplateSubtasks(Array.isArray(item.subtasks) ? item.subtasks : [], this.addData.owner)
                this.syncTemplateSubtaskOwners()
                await this.applyTemplateSubtaskDates()
                if (applySequence !== this.templateApplySequence || projectId != this.addData.project_id) return;
                if (this.addData.subtasks.length > 0) this.advanced = true;
                this.$nextTick(() => {
                    this.$refs.input.focus();
                    this.templateCompareData = this.captureTemplateCompareData();
                });
                return;
            }
            const currentSubtasks = this.captureSubtaskSelection(this.addData.subtasks || [])
            const previousSubtasks = this.templateCompareData.subtasks || []
            if ((this.addData.name !== this.templateCompareData.name && this.addData.name !== '') ||
                (this.addData.content !== this.templateCompareData.content && this.addData.content !== '') ||
                (this.addData.milestone_name !== this.templateCompareData.milestone_name && this.addData.milestone_name !== '') ||
                this.addData.estimated_minutes !== this.templateCompareData.estimated_minutes ||
                JSON.stringify(currentSubtasks) !== JSON.stringify(previousSubtasks)) {
                $A.modalConfirm({
                    content: '当前已有修改的内容，是否要覆盖？',
                    onOk: () => {
                        if (targetProjectId == this.addData.project_id) this.setTaskTemplate(item, true)
                    }
                });
            } else {
                this.setTaskTemplate(item, true);
            }
        },

        mapTemplateSubtasks(items, parentOwner = this.addData.owner) {
            const owner = Array.isArray(parentOwner) ? [...parentOwner] : []
            return (items || [])
                .map(subtask => typeof subtask === 'string' ? {name: subtask} : subtask)
                .filter(subtask => typeof subtask?.name === 'string' && subtask.name.trim())
                .map(subtask => {
                        const ownerStrategy = subtask.owner_strategy === 'parent' ? 'parent' : 'current'
                        const hasSchedule = Number.isInteger(subtask.start_offset_days) && Number.isInteger(subtask.end_offset_days)
                        const subtaskOwner = ownerStrategy === 'parent' ? owner : [this.userId]
                        return {
                            name: subtask.name.trim(),
                            milestone_name: subtask.milestone_name || '',
                            owner: [...subtaskOwner],
                            times: [],
                            _createSubtask: true,
                            _templateOwnerStrategy: ownerStrategy,
                            _templateSchedule: hasSchedule ? {
                                start_offset_days: subtask.start_offset_days,
                                end_offset_days: subtask.end_offset_days,
                            } : false,
                            children: this.mapTemplateSubtasks(subtask.children, subtaskOwner),
                        }
                    })
        },

        captureTemplateCompareData() {
            return {
                name: this.addData.name,
                content: this.addData.content,
                milestone_name: this.addData.milestone_name,
                estimated_minutes: this.addData.estimated_minutes,
                subtasks: this.captureSubtaskSelection(this.addData.subtasks || []),
            }
        },

        captureSubtaskSelection(items) {
            return (items || []).map(item => ({
                    name: item.name || '',
                    milestone_name: item.milestone_name || '',
                    selected: item._createSubtask !== false,
                    children: this.captureSubtaskSelection(item.children),
                })).filter(item => item.name)
        },

        setTaskDefaultTemplate() {
            // 默认模板仅取本项目的，避免跨项目模板抢占本项目新任务
            const defaultTemplate = (this.taskTemplates || []).find(t => t.is_default && t.project_id == this.addData.project_id);
            if (defaultTemplate) {
                this.setTaskTemplate(defaultTemplate);
            }
        },

        onAI() {
            emitter.emit('openAIAssistant', {
                sessionKey: 'task-create',
                title: this.$L('AI 任务助手'),
                placeholder: this.$L('请简要描述任务目标、背景或预期交付，AI 将生成标题、详细说明和子任务'),
                onBeforeSend: this.handleTaskAIBeforeSend,
                onRender: this.handleTaskAIRender,
                onApply: this.handleTaskAIApply,
            });
        },

        buildTaskAIContextData() {
            const prompts = [];
            const currentTitle = (this.addData.name || '').trim();
            const currentContent = extractPlainText(this.addData.content, 2000, true);
            if (currentTitle || currentContent) {
                prompts.push('## 当前任务信息');
                if (currentTitle) {
                    prompts.push(`当前标题：${currentTitle}`);
                }
                if (currentContent) {
                    prompts.push(`当前内容：${currentContent}`);
                }
                prompts.push('请在此基础上优化改进，而不是完全重写。');
            }

            const currentTemplate = this.templateActiveID
                ? (this.taskTemplates || []).find(item => item.id === this.templateActiveID)
                : null;
            if (currentTemplate) {
                const templateName = (currentTemplate.name || currentTemplate.title || '').trim();
                const templateContent = extractPlainText(nostyle(currentTemplate.content, {sanitize: false}), 1200, true);
                prompts.push('## 任务模板要求');
                if (templateName) {
                    prompts.push(`模板名称：${templateName}`);
                }
                if (templateContent) {
                    prompts.push(`模板内容结构：${templateContent}`);
                }
                prompts.push('请严格按照此模板的结构和格式要求生成内容。');
            }

            const statusInfo = [];
            if (Array.isArray(this.addData.owner) && this.addData.owner.length > 0) {
                statusInfo.push('已设置负责人');
            }
            if (Array.isArray(this.addData.times) && this.addData.times.length > 0) {
                statusInfo.push('已设置计划时间');
            }
            const priorityName = (this.addData.p_name || '').trim();
            if (priorityName) {
                statusInfo.push(`优先级：${priorityName}`);
            }
            if (statusInfo.length > 0) {
                prompts.push('## 任务状态');
                prompts.push(statusInfo.join('，'));
                prompts.push('请在任务描述中体现相应的要求和约束。');
            }

            const projectInfo = this.cacheProjects.find(({id}) => id == this.addData.project_id);
            const columnInfo = this.cacheColumns.find(({id}) => id == this.addData.column_id);
            if ((projectInfo && projectInfo.name) || (columnInfo && columnInfo.name)) {
                prompts.push('## 所属项目');
                if (projectInfo && projectInfo.name) {
                    prompts.push(`项目：${projectInfo.name}`);
                }
                if (columnInfo && columnInfo.name) {
                    prompts.push(`任务列表：${columnInfo.name}`);
                }
            }

            const subtasks = this.subtaskRows
                .map(({item, depth}) => `${'  '.repeat(depth)}${item.name || ''}`.trimEnd())
                .filter(Boolean)
                .slice(0, 20);
            if (subtasks.length > 0) {
                prompts.push('## 当前子任务');
                subtasks.forEach((name, index) => {
                    prompts.push(`${index + 1}. ${name}`);
                });
            }

            return prompts.join('\n').trim();
        },

        handleTaskAIBeforeSend(context = []) {
            const prepared = [
                ['system', withLanguagePreferencePrompt(TASK_AI_SYSTEM_PROMPT)]
            ];
            const contextPrompt = this.buildTaskAIContextData();
            if (contextPrompt) {
                let assistantContext = [
                    '以下是已有的上下文信息，可辅助你理解：',
                    contextPrompt,
                ].join('\n');
                if ($A.getObject(context, [0,0]) === 'human') {
                    assistantContext += "\n----\n请根据以上信息，结合以下用户输入的内容生成项目任务：++++";
                }
                prepared.push(['human', assistantContext]);
            }
            if (context.length > 0) {
                prepared.push(...context);
            }
            return prepared;
        },

        handleTaskAIApply({rawOutput}) {
            if (!rawOutput) {
                $A.messageWarning('AI 未生成内容');
                return;
            }
            const parsed = this.parseTaskAIContent(rawOutput);
            if (!parsed) {
                $A.modalError('AI 内容解析失败，请重试');
                return;
            }
            if (parsed.title) {
                this.addData.name = parsed.title;
                this.$nextTick(() => {
                    this.$refs.input && this.$refs.input.focus();
                });
            }
            if (parsed.description && this.$refs.editorTaskRef) {
                const html = MarkdownConver(parsed.description);
                this.$refs.editorTaskRef.setContent(html, {format: 'raw'});
            }
            if (parsed.subtasks.length > 0) {
                const mainOwner = Array.isArray(this.addData.owner) && this.addData.owner.length > 0
                    ? [this.addData.owner[0]]
                    : (this.userId ? [this.userId] : []);
                const subtasks = parsed.subtasks.map(name => ({
                    name,
                    owner: [...mainOwner],
                    times: [],
                    _createSubtask: true,
                }));
                this.$set(this.addData, 'subtasks', subtasks);
                this.advanced = true;
            }
        },

        parseTaskAIContent(content) {
            const payload = AINormalizeJsonContent(content);
            if (!payload || typeof payload !== 'object') {
                return null;
            }
            const title = this.pickFirstString([payload.title, payload.name, payload.task_title]);
            const description = this.pickFirstString([
                payload.description_markdown,
                payload.description,
                payload.content_markdown,
                payload.content,
                payload.body,
                payload.detail,
            ]);
            const subtasks = this.normalizeAISubtasks(payload.subtasks || payload.tasks || payload.checklist || payload.steps);
            if (!title && !description && subtasks.length === 0) {
                return null;
            }
            return {
                title,
                description,
                subtasks,
            };
        },

        normalizeAISubtasks(value) {
            let raw = [];
            if (Array.isArray(value)) {
                raw = value.map(item => {
                    if (typeof item === 'string') {
                        return item;
                    }
                    if (item && typeof item === 'object') {
                        return item.title || item.name || item.task || item.content || '';
                    }
                    return '';
                });
            } else if (typeof value === 'string') {
                raw = value.split(/[\n\r;；]+/);
            }
            const cleaned = raw
                .map(item => String(item || '').replace(/^[\d\.-]*\s*/, '').replace(/^[•*\-]\s*/, '').trim())
                .filter(Boolean);
            return Array.from(new Set(cleaned)).slice(0, 8);
        },

        pickFirstString(list = []) {
            for (const item of list) {
                if (typeof item === 'string' && item.trim()) {
                    return item.trim();
                }
            }
            return '';
        },

        handleTaskAIRender({rawOutput}) {
            if (!rawOutput) {
                return '';
            }
            const parsed = this.parseTaskAIContent(rawOutput);
            if (!parsed) {
                return rawOutput;
            }
            const blocks = [];
            if (parsed.title) {
                blocks.push(`## ${parsed.title}`);
            }
            if (parsed.description) {
                blocks.push(parsed.description);
            }
            if (parsed.subtasks.length > 0) {
                const list = parsed.subtasks.map((name, index) => `${index + 1}. ${name}`);
                blocks.push(list.join('\n'));
            }
            return blocks.join('\n\n').trim() || rawOutput;
        }
    }
}
</script>
