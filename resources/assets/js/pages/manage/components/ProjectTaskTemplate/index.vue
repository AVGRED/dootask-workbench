
<template>
    <div class="project-task-template">
        <div class="header">
            <div class="title">
                {{$L('任务模板')}}
                <Loading v-if="loadIng > 0"/>
            </div>
            <div class="actions">
                <Button
                    v-if="templates.length"
                    :type="sortMode ? 'primary' : 'default'"
                    :loading="sortLoading"
                    icon="md-move"
                    @click="toggleSortMode">
                    {{$L(sortMode ? '完成排序' : '调整排序')}}
                </Button>
                <Button type="primary" icon="md-add" @click="handleAdd">
                    {{$L('新建模板')}}
                </Button>
            </div>
        </div>

        <div class="content">
            <div v-if="!templates.length" class="empty">
                <div class="empty-text">{{$L('当前项目暂无任务模板')}}</div>
                <Button type="primary" icon="md-add" @click="handleAdd">{{$L('新建模板')}}</Button>
            </div>
            <Draggable
                v-else
                class="template-list"
                tag="div"
                :list="templates"
                :animation="150"
                :disabled="!sortMode || sortLoading"
                item-key="id"
                handle=".template-drag-handle"
                @end="handleSortEnd">
                <div
                    v-for="item in templates"
                    :key="item.id"
                    class="template-item">
                    <div
                        :class="['template-item-inner', {'is-sorting': sortMode}]">
                        <div
                            v-if="sortMode"
                            class="template-drag-handle"
                            :title="$L('拖拽调整排序')">
                            <Icon type="md-menu" />
                        </div>
                        <div class="template-main">
                            <div class="template-title">
                                <span>{{ item.name }}</span>
                                <span v-if="item.version" class="version-tag">v{{ item.version }}</span>
                                <span v-if="item.is_default" class="default-tag">{{$L('默认')}}</span>
                            </div>
                            <div class="template-content">
                                <div v-if="item.title" class="task-title">{{ item.title }}</div>
                                <div v-if="Number(item.estimated_minutes) > 0" class="template-milestone">{{ $L('预估工时') }}：{{ (Number(item.estimated_minutes) / 60).toFixed(1) }} {{$L('小时')}}</div>
                                <div v-if="item.milestone_name" class="template-milestone">{{ $L('项目里程碑') }}：{{ item.milestone_name }}</div>
                                <div v-if="item.content" class="task-content">
                                    <VMPreviewNostyle ref="descPreview" :value="item.content"/>
                                </div>
                        <div v-if="item.subtasks && item.subtasks.length" class="task-subtasks">
                            <Tag v-for="(row, index) in getSubtaskRows(item.subtasks)" :key="index">{{'· '.repeat(row.depth)}}{{ subtaskName(row.subtask) }}{{ row.subtask.milestone_name ? ` · ${row.subtask.milestone_name}` : '' }}</Tag>
                                </div>
                            </div>
                            <div v-if="item.updated_at" class="template-updated-at">
                                {{$L('更新时间')}}：{{formatUpdatedAt(item.updated_at)}}
                            </div>
                            <div class="template-actions">
                                <Button :disabled="sortMode" @click="handleHistory(item)">{{$L('历史版本')}}</Button>
                                <Button :disabled="sortMode" @click="handleSetDefault(item)" type="primary" :icon="item.is_default ? 'md-checkmark' : ''">
                                    {{$L(item.is_default ? '取消默认' : '设为默认')}}
                                </Button>
                                <Button :disabled="sortMode" @click="handleEdit(item)" type="primary">
                                    {{$L('编辑')}}
                                </Button>
                                <Button :disabled="sortMode" @click="handleDelete(item)" type="error">
                                    {{$L('删除')}}
                                </Button>
                            </div>
                        </div>
                    </div>
                </div>
            </Draggable>
        </div>

        <!-- 编辑模板弹窗 -->
        <Modal
            v-model="showEditModal"
            :title="editingTemplate.id ? $L('编辑模板') : $L('新建模板')"
            :mask-closable="false"
            :width="850">
            <Form
                ref="editForm"
                :model="editingTemplate"
                :rules="formRules"
                v-bind="formOptions"
                @submit.native.prevent>
                <FormItem prop="name" :label="$L('模板名称')">
                    <Input ref="templateName" v-model="editingTemplate.name" :disabled="systemTemplateIsMultiple" :placeholder="$L('请输入模板名称')"/>
                </FormItem>
                <FormItem prop="title" :label="$L('任务标题')">
                    <Input v-model="editingTemplate.title" :disabled="systemTemplateIsMultiple" :placeholder="$L('请输入任务标题')"/>
                </FormItem>
                <FormItem prop="content" :label="$L('任务内容')">
                    <Input
                        type="textarea"
                        v-model="editingTemplate.content"
                        :disabled="systemTemplateIsMultiple"
                        :placeholder="$L('请输入任务内容')"
                        :autosize="{ minRows: 4, maxRows: 12 }"/>
                </FormItem>
                <FormItem :label="$L('项目里程碑')">
                    <Select v-model="editingTemplate.milestone_name" clearable :placeholder="$L('项目里程碑')">
                        <Option v-for="milestone in milestones" :key="milestone.id" :value="milestone.name">{{milestone.name}}</Option>
                    </Select>
                </FormItem>
                <FormItem :label="$L('预估工时')">
                    <InputNumber v-model="editingTemplate.estimated_minutes" :min="0" :max="1000000" :step="30" :placeholder="$L('分钟')" />
                </FormItem>
                <FormItem :label="$L('子任务')">
                    <div class="template-subtask-editor">
                        <div v-for="row in getSubtaskRows(editingTemplate.subtasks)" :key="row.path" class="template-subtask-row" :style="{marginLeft: `${row.depth * 18}px`}">
                            <Input v-model="row.subtask.name" :maxlength="255" :placeholder="$L('任务描述')" />
                            <Select v-model="row.subtask.owner_strategy" :placeholder="$L('负责人')">
                                <Option value="current">{{$L('自己')}}</Option>
                                <Option value="parent">{{$L('任务负责人')}}</Option>
                            </Select>
                            <InputNumber v-model="row.subtask.start_offset_days" :min="0" :max="3650" :placeholder="$L('开始偏移天数')" />
                            <InputNumber v-model="row.subtask.end_offset_days" :min="0" :max="3650" :placeholder="$L('结束偏移天数')" />
                            <Select v-model="row.subtask.milestone_name" clearable :placeholder="$L('项目里程碑')">
                                <Option v-for="milestone in milestones" :key="milestone.id" :value="milestone.name">{{milestone.name}}</Option>
                            </Select>
                            <Button type="text" icon="md-add" :disabled="!canAddTemplateChild(row)" @click="addTemplateSubtask(row.subtask)" />
                            <Button type="text" icon="md-close" @click="removeTemplateSubtask(row)" />
                        </div>
                        <Button v-if="countSubtasks(editingTemplate.subtasks) < 50" size="small" icon="md-add" @click="addTemplateSubtask()">{{$L('添加子任务')}}</Button>
                        <div class="template-subtask-tip">{{$L('最多50项，支持5层嵌套；相对父任务开始日期，留空则沿用父任务计划时间')}}</div>
                    </div>
                </FormItem>
                <FormItem v-if="!editingTemplate.id">
                    <div class="project-task-template-system">
                        <div v-if="!systemTemplateShow" @click="onSystemTemplate" class="tip-title">{{$L('使用示例模板')}}</div>
                        <ul v-else>
                            <li
                                :class="{selected:systemTemplateIsMultiple}"
                                @click="systemTemplateIsMultiple=!systemTemplateIsMultiple">
                                <i class="taskfont" v-html="systemTemplateIsMultiple ? '&#xe627;' : '&#xe625;'"></i>
                                {{$L('多选')}}
                            </li>
                            <li
                                v-for="(item, index) in systemTemplateData"
                                :key="index"
                                :class="{selected:systemTemplateIsMultiple && systemTemplateMultipleData.indexOf(item)!==-1}"
                                @click="useSystemTemplate(item)">{{item.name}}</li>
                        </ul>
                    </div>
                </FormItem>
            </Form>
            <div slot="footer" class="adaption">
                <Button type="default" @click="showEditModal=false">{{$L('取消')}}</Button>
                <Button type="primary" :loading="loadIng > 0" @click="handleSave">
                    {{ $L('保存') }}
                    {{ systemTemplateIsMultiple && systemTemplateMultipleData.length > 0 ? ` (${systemTemplateMultipleData.length})` : '' }}
                </Button>
            </div>
        </Modal>

        <!-- 模板历史版本 -->
        <Modal
            v-model="showHistoryModal"
            :title="$L('历史版本')"
            :mask-closable="false"
            :width="760">
            <div v-if="historyLoading" class="template-history-loading"><Loading /></div>
            <template v-else-if="historyTemplate">
                <div class="template-history-current">
                    <strong>{{$L('当前版本')}} · v{{ currentRevision.version }}</strong>
                    <span v-if="currentRevision.updated_at">{{$L('更新时间')}}：{{formatUpdatedAt(currentRevision.updated_at)}}</span>
                </div>
                <div v-if="!historyRevisions.length" class="template-history-empty">{{$L('暂无内容')}}</div>
                <div v-for="revision in historyRevisions" :key="revision.id" class="template-history-item">
                    <div class="template-history-heading">
                        <strong>v{{ revision.version }} · {{ revision.name }}</strong>
                        <span>{{formatUpdatedAt(revision.created_at)}}<template v-if="revision.changed_by_name"> · {{revision.changed_by_name}}</template></span>
                    </div>
                    <div v-if="revision.title" class="template-history-title">{{revision.title}}</div>
                    <div v-if="revision.content" class="template-history-content">{{revision.content}}</div>
                    <div v-if="revision.subtasks && revision.subtasks.length" class="template-history-subtasks">
                        <Tag v-for="(row, index) in getSubtaskRows(revision.subtasks)" :key="index">{{'· '.repeat(row.depth)}}{{subtaskName(row.subtask)}}</Tag>
                    </div>
                    <div class="template-history-actions">
                        <Button size="small" @click="toggleRevisionDiff(revision)">{{$L(comparingVersion === revision.version ? '收起差异' : '查看差异')}}</Button>
                        <Button type="primary" size="small" :loading="restoringVersion === revision.version" @click="handleRestoreRevision(revision)">{{$L('恢复此版本')}}</Button>
                    </div>
                    <div v-if="comparingVersion === revision.version" class="template-history-diff">
                        <div v-for="field in getRevisionDiff(revision)" :key="field.key" class="template-history-diff-field">
                            <strong>{{$L(field.label)}}</strong>
                            <div class="template-history-diff-values">
                                <div>
                                    <span>v{{revision.version}}</span>
                                    <pre>{{field.previous || $L('暂无内容')}}</pre>
                                </div>
                                <div>
                                    <span>{{$L('当前版本')}} v{{currentRevision.version}}</span>
                                    <pre>{{field.current || $L('暂无内容')}}</pre>
                                </div>
                            </div>
                        </div>
                        <div v-if="!getRevisionDiff(revision).length" class="template-history-empty">{{$L('暂无内容')}}</div>
                    </div>
                </div>
                <div v-if="historyRevisions.length < historyTotal" class="template-history-more">
                    <Button :loading="historyMoreLoading" :disabled="historyLoading" @click="loadOlderHistory">{{$L('加载更多')}}</Button>
                </div>
            </template>
            <div slot="footer">
                <Button @click="showHistoryModal=false">{{$L('关闭')}}</Button>
            </div>
        </Modal>
    </div>
</template>

<script>
import {mapState} from 'vuex'
import emitter from '../../../../store/events'
import Draggable from 'vuedraggable';
import VMPreviewNostyle from "../../../../components/VMEditor/nostyle.vue";
import AllTaskTemplates from "./templates";
import {languageName} from "../../../../i18n";

export default {
    name: 'ProjectTaskTemplate',
    components: {VMPreviewNostyle, Draggable},
    props: {
        projectId: {
            type: [Number, String],
            required: true
        }
    },
    data() {
        return {
            loadIng: 0,
            templates: [],
            milestones: [],
            sortMode: false,
            sortLoading: false,
            showEditModal: false,
            editingTemplate: this.getEmptyTemplate(),
            showHistoryModal: false,
            historyLoading: false,
            historyTemplate: null,
            currentRevision: {},
            historyRevisions: [],
            historyTotal: 0,
            historyPage: 0,
            historyMoreLoading: false,
            comparingVersion: null,
            restoringVersion: null,
            formRules: {
                name: [
                    { required: true, message: this.$L('请输入模板名称'), trigger: 'blur' }
                ]
            },

            systemTemplateShow: false,
            systemTemplateData: [],
            systemTemplateIsMultiple: false,
            systemTemplateMultipleData: [],
            templateListRequestKey: 0,
            milestoneListRequestKey: 0,
            sortRequestKey: 0,
            historyRequestKey: 0,
        }
    },
    computed: {
        ...mapState(['formOptions'])
    },
    created() {
        this.loadTemplates()
        this.loadMilestones()
        emitter.on('projectMilestonesUpdate', this.onProjectMilestonesUpdate)
        emitter.on('projectTaskTemplateUpdate', this.onProjectTaskTemplateUpdate)
    },
    beforeDestroy() {
        emitter.off('projectMilestonesUpdate', this.onProjectMilestonesUpdate)
        emitter.off('projectTaskTemplateUpdate', this.onProjectTaskTemplateUpdate)
    },
    watch: {
        projectId(projectId, previousProjectId) {
            if (Number(projectId) === Number(previousProjectId)) return
            ++this.templateListRequestKey
            ++this.milestoneListRequestKey
            ++this.sortRequestKey
            ++this.historyRequestKey
            this.templates = []
            this.milestones = []
            this.sortMode = false
            this.sortLoading = false
            this.showEditModal = false
            this.editingTemplate = this.getEmptyTemplate()
            this.showHistoryModal = false
            this.historyTemplate = null
            this.historyRevisions = []
            this.currentRevision = {}
            this.historyTotal = 0
            this.historyPage = 0
            this.historyLoading = false
            this.historyMoreLoading = false
            this.comparingVersion = null
            this.restoringVersion = null
            this.systemTemplateShow = false
            this.systemTemplateIsMultiple = false
            this.systemTemplateMultipleData = []
            this.loadTemplates()
            this.loadMilestones()
        },
        showEditModal(val) {
            if (!val) {
                this.$refs.editForm.resetFields()
                this.systemTemplateShow = false
                this.systemTemplateIsMultiple = false
            }
        }
    },
    methods: {
        // 获取空模板对象
        getEmptyTemplate() {
            return {
                id: null,
                project_id: this.projectId,
                name: '',
                title: '',
                content: '',
                milestone_name: '',
                estimated_minutes: null,
                subtasks: [],
                is_default: false
            }
        },

        // 开启/关闭排序模式
        toggleSortMode() {
            if (this.sortLoading) return
            this.sortMode = !this.sortMode
        },

        // 拖拽排序完成
        async handleSortEnd(event) {
            if (!this.sortMode) {
                return
            }
            if (event && event.oldIndex === event.newIndex) {
                return
            }
            const list = this.templates.map(template => template.id)
            if (!list.length) {
                return
            }
            const projectId = this.projectId
            const requestKey = this.sortRequestKey = this.sortRequestKey + 1
            this.sortLoading = true
            try {
                const {msg} = await this.$store.dispatch('call', {
                    url: 'project/task/template_sort',
                    method: 'post',
                    data: {
                        project_id: projectId,
                        list
                    },
                    spinner: 2000
                })
                if (requestKey === this.sortRequestKey && Number(projectId) === Number(this.projectId)) {
                    $A.messageSuccess(msg || '排序已保存')
                    await this.loadTemplates()
                }
            } catch ({msg}) {
                if (requestKey === this.sortRequestKey && Number(projectId) === Number(this.projectId)) {
                    $A.messageError(msg || '排序保存失败')
                    await this.loadTemplates()
                }
            } finally {
                if (requestKey === this.sortRequestKey) this.sortLoading = false
            }
        },

        // 加载模板列表
        async loadTemplates() {
            const projectId = this.projectId
            const requestKey = this.templateListRequestKey = this.templateListRequestKey + 1
            this.loadIng++
            try {
                const {data} = await this.$store.dispatch("call", {
                    url: 'project/task/template_list',
                    data: {
                        project_id: projectId
                    },
                    spinner: 3000
                })
                if (requestKey !== this.templateListRequestKey || Number(projectId) !== Number(this.projectId)) return
                this.templates = data || []
                if (!this.templates.length) {
                    this.sortMode = false
                }
            } catch ({msg}) {
                if (requestKey === this.templateListRequestKey && Number(projectId) === Number(this.projectId)) {
                    $A.messageError(msg || '加载模板失败')
                }
            } finally {
                this.loadIng--
            }
        },

        async loadMilestones() {
            const projectId = this.projectId
            const requestKey = this.milestoneListRequestKey = this.milestoneListRequestKey + 1
            try {
                const {data} = await this.$store.dispatch('call', {
                    url: 'projectmilestone/lists',
                    data: {project_id: projectId},
                    spinner: 2000
                })
                if (requestKey !== this.milestoneListRequestKey || Number(projectId) !== Number(this.projectId)) return
                this.milestones = data?.milestones || []
            } catch ({msg}) {
                if (requestKey !== this.milestoneListRequestKey || Number(projectId) !== Number(this.projectId)) return
                this.milestones = []
                $A.messageError(msg || '加载项目里程碑失败')
            }
        },

        onProjectMilestonesUpdate(projectId) {
            if (Number(projectId) === Number(this.projectId)) {
                this.loadMilestones()
            }
        },

        onProjectTaskTemplateUpdate(projectId, templateId) {
            if (Number(projectId) !== Number(this.projectId)) return
            this.loadTemplates()
            if (this.historyTemplate && Number(this.historyTemplate.id) === Number(templateId)) {
                this.handleHistory(this.historyTemplate)
            }
        },

        // 新建模板
        handleAdd() {
            this.editingTemplate = this.getEmptyTemplate()
            this.showEditModal = true
        },

        // 编辑模板
        handleEdit(template) {
            this.editingTemplate = {
                ...template,
                subtasks: (Array.isArray(template.subtasks) ? template.subtasks : []).map(subtask => this.normalizeSubtask(subtask)),
            }
            this.showEditModal = true
        },

        normalizeSubtask(subtask) {
            if (typeof subtask === 'string') {
                return {name: subtask, milestone_name: '', owner_strategy: 'current', start_offset_days: null, end_offset_days: null, children: []}
            }
            return {
                name: subtask?.name || '',
                milestone_name: subtask?.milestone_name || '',
                owner_strategy: ['current', 'parent'].includes(subtask?.owner_strategy) ? subtask.owner_strategy : 'current',
                start_offset_days: Number.isInteger(subtask?.start_offset_days) ? subtask.start_offset_days : null,
                end_offset_days: Number.isInteger(subtask?.end_offset_days) ? subtask.end_offset_days : null,
                children: Array.isArray(subtask?.children) ? subtask.children.map(child => this.normalizeSubtask(child)) : [],
            }
        },

        getSubtaskRows(items, depth = 0, path = 'root', rows = [], ancestors = []) {
            (items || []).forEach((subtask, index) => {
                const rowPath = `${path}.${index}`
                const row = {subtask, items, index, depth, path: rowPath, ancestors}
                rows.push(row)
                this.getSubtaskRows(subtask.children, depth + 1, rowPath, rows, [...ancestors, subtask])
            })
            return rows
        },

        countSubtasks(items) {
            return this.getSubtaskRows(items).length
        },

        subtaskName(subtask) {
            return typeof subtask === 'string' ? subtask : (subtask?.name || '')
        },

        formatUpdatedAt(value) {
            return value ? $A.newDateString(value) : ''
        },

        async handleHistory(template) {
            this.historyTemplate = template
            this.comparingVersion = null
            this.showHistoryModal = true
            this.historyLoading = false
            this.historyMoreLoading = false
            this.historyRevisions = []
            this.historyTotal = 0
            this.historyPage = 0
            return this.loadHistoryPage(false)
        },

        async loadHistoryPage(append = true) {
            const template = this.historyTemplate
            if (!template || this.historyLoading || this.historyMoreLoading) return
            const requestKey = this.historyRequestKey = (this.historyRequestKey || 0) + 1
            const projectId = this.projectId
            const page = append ? this.historyPage + 1 : 1
            if (append) this.historyMoreLoading = true
            else this.historyLoading = true
            try {
                const {data} = await this.$store.dispatch('call', {
                    url: 'projecttasktemplate/revisions',
                    data: {project_id: projectId, template_id: template.id, page, per_page: 20},
                    spinner: 2000
                })
                if (requestKey !== this.historyRequestKey || this.projectId !== projectId || this.historyTemplate?.id !== template.id) return
                if (!append) this.currentRevision = data?.current || {}
                this.historyRevisions = append
                    ? [...this.historyRevisions, ...(data?.revisions || [])]
                    : (data?.revisions || [])
                this.historyTotal = Number(data?.total) || 0
                this.historyPage = Number(data?.page) || page
            } catch ({msg}) {
                if (requestKey === this.historyRequestKey) {
                    if (!append) this.showHistoryModal = false
                    $A.messageError(msg || '加载历史版本失败')
                }
            } finally {
                if (requestKey === this.historyRequestKey) {
                    if (append) this.historyMoreLoading = false
                    else this.historyLoading = false
                }
            }
        },

        loadOlderHistory() {
            return this.loadHistoryPage(true)
        },

        toggleRevisionDiff(revision) {
            this.comparingVersion = this.comparingVersion === revision.version ? null : revision.version
        },

        getRevisionDiff(revision) {
            const previousSubtasks = (revision.subtasks || []).map(subtask => this.normalizeSubtask(subtask))
            const currentSubtasks = (this.currentRevision.subtasks || []).map(subtask => this.normalizeSubtask(subtask))
            const fields = [
                {key: 'name', label: '模板名称', previous: revision.name || '', current: this.currentRevision.name || ''},
                {key: 'title', label: '任务标题', previous: revision.title || '', current: this.currentRevision.title || ''},
                {key: 'content', label: '任务内容', previous: revision.content || '', current: this.currentRevision.content || ''},
                {key: 'milestone_name', label: '项目里程碑', previous: revision.milestone_name || '', current: this.currentRevision.milestone_name || ''},
                {key: 'estimated_minutes', label: `${this.$L('预估工时')} (${this.$L('分钟')})`, previous: Number(revision.estimated_minutes) || 0, current: Number(this.currentRevision.estimated_minutes) || 0},
                {
                    key: 'subtasks',
                    label: '子任务',
                    previous: previousSubtasks,
                    current: currentSubtasks,
                    comparePrevious: JSON.stringify(previousSubtasks),
                    compareCurrent: JSON.stringify(currentSubtasks),
                },
            ]
            return fields
                .filter(field => (field.comparePrevious ?? field.previous) !== (field.compareCurrent ?? field.current))
                .map(field => ({
                    ...field,
                    previous: field.key === 'subtasks' ? this.formatRevisionSubtasks(field.previous) : field.previous,
                    current: field.key === 'subtasks' ? this.formatRevisionSubtasks(field.current) : field.current,
                }))
        },

        formatRevisionSubtasks(subtasks) {
            const lines = []
            const append = (items, depth = 0) => (items || []).forEach(subtask => {
                const normalized = this.normalizeSubtask(subtask)
                const owner = this.$L(normalized.owner_strategy === 'parent' ? '任务负责人' : '自己')
                const dateRange = normalized.start_offset_days === null
                    ? ''
                    : ` · ${this.$L('开始偏移天数')}: ${normalized.start_offset_days} · ${this.$L('结束偏移天数')}: ${normalized.end_offset_days}`
                const milestone = normalized.milestone_name ? ` · ${this.$L('项目里程碑')}: ${normalized.milestone_name}` : ''
                lines.push(`${'· '.repeat(depth)}${normalized.name} · ${owner}${dateRange}${milestone}`)
                append(normalized.children, depth + 1)
            })
            append(subtasks)
            return lines.join('\n')
        },

        handleRestoreRevision(revision) {
            const projectId = this.projectId
            const templateId = this.historyTemplate?.id
            const template = this.historyTemplate
            $A.modalConfirm({
                title: '恢复此版本',
                content: this.$L('确定恢复到历史版本吗？'),
                onOk: async () => {
                    if (Number(projectId) !== Number(this.projectId) || !templateId) return
                    this.restoringVersion = revision.version
                    try {
                        const {msg} = await this.$store.dispatch('call', {
                            url: 'projecttasktemplate/restore',
                            method: 'post',
                            data: {project_id: projectId, template_id: templateId, version: revision.version},
                            spinner: 3000
                        })
                        if (Number(projectId) === Number(this.projectId)) $A.messageSuccess(msg || '恢复成功')
                        if (Number(projectId) === Number(this.projectId) && this.historyTemplate?.id === templateId) {
                            await Promise.all([this.loadTemplates(), this.handleHistory(template)])
                        }
                    } catch ({msg}) {
                        if (Number(projectId) === Number(this.projectId)) $A.messageError(msg || '恢复失败')
                    } finally {
                        if (Number(projectId) === Number(this.projectId)) this.restoringVersion = null
                    }
                }
            })
        },

        addTemplateSubtask(parent = null) {
            const row = this.normalizeSubtask({name: ''})
            ;(parent ? parent.children : this.editingTemplate.subtasks).push(row)
        },

        canAddTemplateChild(row) {
            return row.depth < 4 && this.countSubtasks(this.editingTemplate.subtasks) < 50
        },

        removeTemplateSubtask(row) {
            row.items.splice(row.index, 1)
        },

        prepareTemplateSubtasks(items) {
            return (items || [])
                .map(subtask => this.normalizeSubtask(subtask))
                .filter(subtask => subtask.name.trim())
                .map(subtask => ({...subtask, name: subtask.name.trim(), children: this.prepareTemplateSubtasks(subtask.children)}))
        },

        // 保存模板
        async handleSave() {
            if (!this.editingTemplate.name) {
                $A.messageWarning('请输入模板名称')
                return
            }
            const subtasks = this.prepareTemplateSubtasks(this.editingTemplate.subtasks)
            const estimatedMinutes = this.editingTemplate.estimated_minutes === null || this.editingTemplate.estimated_minutes === ''
                ? null
                : Math.max(0, Math.min(1000000, Math.round(Number(this.editingTemplate.estimated_minutes) || 0)))
            const subtaskRows = this.getSubtaskRows(subtasks)
            if (subtaskRows.length > 50) {
                $A.messageWarning(this.$L('子任务最多50项'))
                return
            }
            if (subtaskRows.some(({subtask}) => (subtask.start_offset_days === null) !== (subtask.end_offset_days === null) ||
                (subtask.start_offset_days !== null && subtask.end_offset_days < subtask.start_offset_days))) {
                $A.messageWarning(this.$L('相对日期设置不完整或结束日期早于开始日期'))
                return
            }
            const projectId = this.projectId
            let savePromises = []
            if (this.systemTemplateIsMultiple) {
                if (this.systemTemplateMultipleData.length === 0) {
                    $A.messageWarning('请选择示例模板')
                    return
                }
                savePromises = this.systemTemplateMultipleData.map(item => {
                    const template = {
                        ...this.editingTemplate,
                        id: null,
                        name: item.name,
                        title: item.title,
                        content: item.content,
                        estimated_minutes: estimatedMinutes,
                        subtasks: item.subtasks?.length ? item.subtasks : subtasks,
                    }
                    return this.handleSaveCall(template, projectId)
                })
            } else {
                const template = {...this.editingTemplate, project_id: projectId, estimated_minutes: estimatedMinutes, subtasks}
                savePromises.push(this.handleSaveCall(template, projectId))
            }

            try {
                const results = await Promise.all(savePromises)
                if (Number(projectId) !== Number(this.projectId)) return
                $A.messageSuccess(results.length === 1 ? results[0].msg : '全部保存成功')
                this.showEditModal = false
                this.loadTemplates()
            } catch (error) {
                if (Number(projectId) === Number(this.projectId)) $A.messageError(error.msg || '保存失败')
            }
        },

        // 保存模板请求
        async handleSaveCall(data, projectId = this.projectId) {
            this.loadIng++
            try {
                return await this.$store.dispatch("call", {
                    url: 'project/task/template_save',
                    data: {...data, project_id: projectId},
                    method: 'post',
                    spinner: 3000
                })
            } finally {
                this.loadIng--
            }
        },

        // 删除模板
        async handleDelete(template) {
            const projectId = this.projectId
            const templateId = template.id
            $A.modalConfirm({
                title: '确认删除',
                content: '确定要删除该模板吗？',
                onOk: async () => {
                    if (Number(projectId) !== Number(this.projectId)) return
                    this.loadIng++
                    try {
                        const {msg} = await this.$store.dispatch("call", {
                            url: 'project/task/template_delete',
                            data: {
                                id: templateId
                            },
                            spinner: 3000
                        })
                        if (Number(projectId) !== Number(this.projectId)) return
                        $A.messageSuccess(msg || '删除成功')
                        await this.loadTemplates()
                    } catch ({msg}) {
                        if (Number(projectId) === Number(this.projectId)) $A.messageError(msg || '删除失败')
                    } finally {
                        this.loadIng--
                    }
                }
            })
        },

        // 设置默认模板
        async handleSetDefault(template) {
            const projectId = this.projectId
            const templateId = template.id
            this.loadIng++
            try {
                const {msg} = await this.$store.dispatch("call", {
                    url: 'project/task/template_default',
                    data: {
                        id: templateId,
                        project_id: projectId
                    },
                    spinner: 3000
                })
                if (Number(projectId) !== Number(this.projectId)) return
                $A.messageSuccess(msg || '设置成功')
                await this.loadTemplates()
            } catch ({msg}) {
                if (Number(projectId) === Number(this.projectId)) $A.messageError(msg || '设置失败')
            } finally {
                this.loadIng--
            }
        },

        onSystemTemplate() {
            this.systemTemplateData = typeof AllTaskTemplates[languageName] === "undefined" ? AllTaskTemplates['en'] : AllTaskTemplates[languageName]
            this.systemTemplateShow = true
        },

        // 使用系统模板
        useSystemTemplate(item) {
            this.editingTemplate.name = item.name
            this.editingTemplate.title = item.title
            this.editingTemplate.content = item.content
            this.editingTemplate.subtasks = (item.subtasks || []).map(subtask => this.normalizeSubtask(subtask))
            //
            if (this.systemTemplateIsMultiple) {
                const index = this.systemTemplateMultipleData.indexOf(item)
                if (index === -1) {
                    this.systemTemplateMultipleData.push(item)
                } else {
                    this.systemTemplateMultipleData.splice(index, 1)
                }
            }
        }
    }
}
</script>

<style lang="scss" scoped>
.template-subtask-editor {
    .template-subtask-row,
    .template-subtask-labels {
        display: grid;
        grid-template-columns: minmax(120px, 2fr) minmax(100px, 1fr) repeat(2, minmax(100px, 1fr)) minmax(120px, 1.2fr) 32px 32px;
        gap: 8px;
        align-items: center;
        margin-bottom: 8px;
    }

    .template-subtask-labels {
        margin-top: -4px;
        color: #909399;
        font-size: 12px;
    }

    .template-subtask-tip {
        margin-top: 6px;
        color: #909399;
        font-size: 12px;
    }
}

.template-updated-at {
    margin-top: 8px;
    color: #909399;
    font-size: 12px;
}

.template-history-loading,
.template-history-empty {
    padding: 24px;
    text-align: center;
    color: #909399;
}

.template-history-more {
    padding: 12px 0;
    text-align: center;
}

.template-history-current,
.template-history-heading {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 12px;
}

.template-history-current {
    padding: 0 0 14px;
    color: #606266;
}

.template-history-heading span {
    color: #909399;
    font-size: 12px;
}

.template-history-item {
    padding: 14px 0;
    border-top: 1px solid #ebeef5;
}

.template-history-title {
    margin-top: 8px;
    font-weight: 600;
}

.template-history-content {
    margin-top: 6px;
    color: #606266;
    white-space: pre-wrap;
    overflow-wrap: anywhere;
}

.template-history-subtasks {
    margin-top: 8px;
}

.template-history-actions {
    margin-top: 10px;
    text-align: right;
}

.template-history-diff {
    margin-top: 12px;
    padding: 12px;
    border-radius: 4px;
    background: #f7f8fa;
}

.template-history-diff-field + .template-history-diff-field {
    margin-top: 12px;
}

.template-history-diff-values {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
    margin-top: 6px;
}

.template-history-diff-values > div {
    min-width: 0;
    padding: 8px;
    border: 1px solid #ebeef5;
    border-radius: 4px;
    background: #fff;
}

.template-history-diff-values span {
    color: #909399;
    font-size: 12px;
}

.template-history-diff-values pre {
    margin: 6px 0 0;
    color: #606266;
    font: inherit;
    white-space: pre-wrap;
    overflow-wrap: anywhere;
}

@media (max-width: 640px) {
    .template-history-diff-values {
        grid-template-columns: minmax(0, 1fr);
    }
}
</style>
