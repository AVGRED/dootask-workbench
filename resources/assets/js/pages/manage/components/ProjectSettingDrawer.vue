<template>
        <DrawerOverlay
            :value="visible"
            @input="$emit('update:visible', $event)"
            placement="right"
            :size="720"
            class-name="project-setting-drawer"
            :mask-closable="false">
            <template #title>{{$L('项目设置')}}</template>
            <Form class="project-setting-content" :model="settingData" v-bind="formOptions" @submit.native.prevent>
                <FormItem prop="name" :label="$L('项目名称')">
                    <Input type="text" v-model="settingData.name" :maxlength="32" :placeholder="$L('必填')"></Input>
                </FormItem>
                <FormItem prop="desc" :label="$L('项目介绍')">
                    <Input ref="projectDesc" type="textarea" :autosize="{ minRows: 3, maxRows: 5 }" v-model="settingData.desc" :maxlength="255" :placeholder="`${$L('选填')} (${$L('支持 Markdown 格式')})`"></Input>
                </FormItem>
                <FormItem :label="$L('自动归档')" prop="archive_method">
                    <RadioGroup :value="settingData.archive_method" @on-change="formArchived">
                        <Radio label="system">{{$L('系统默认')}}</Radio>
                        <Radio label="custom">{{$L('自定义')}}</Radio>
                    </RadioGroup>
                    <div v-if="settingData.archive_method==='system'" class="form-tip">{{$L('根据系统设置的自动归档规则执行')}}</div>
                    <template v-else-if="settingData.archive_method=='custom'">
                        <div class="form-tip">{{$L('任务完成(*)天后自动归档。', settingData.archive_days || 'n')}}</div>
                        <div class="setting-auto-day">
                            <Input v-model="settingData.archive_days" type="number">
                                <span slot="append">{{$L('[day_unit].天')}}</span>
                            </Input>
                        </div>
                    </template>
                </FormItem>
                <FormItem v-if="aiVisible" :label="$L('AI任务分析')" prop="ai_auto_analyze">
                    <RadioGroup v-model="settingData.ai_auto_analyze">
                        <Radio label="open" :disabled="systemConfig.task_ai_auto_analyze === 'close'">{{$L('开启')}}</Radio>
                        <Radio label="close">{{$L('关闭')}}</Radio>
                    </RadioGroup>
                    <div v-if="systemConfig.ai_auto_analyze === 'close'" class="form-tip">{{$L('系统已关闭AI任务分析功能。')}}</div>
                    <div v-else-if="settingData.ai_auto_analyze === 'open'" class="form-tip">{{$L('新建任务后AI自动分析并给出建议。')}}</div>
                    <div v-else class="form-tip">{{$L('关闭后本项目将不再自动分析任务。')}}</div>
                </FormItem>
                <FormItem :label="$L('共享模板')" prop="task_template_share">
                    <RadioGroup v-model="settingData.task_template_share">
                        <Radio label="open">{{$L('开启')}}</Radio>
                        <Radio label="close">{{$L('关闭')}}</Radio>
                    </RadioGroup>
                    <div v-if="settingData.task_template_share === 'open'" class="form-tip">{{$L('开启后，添加任务时可使用其他项目共享的任务模板。')}}</div>
                    <div v-else class="form-tip">{{$L('关闭后，添加任务时仅加载本项目模板，不显示其他项目共享模板。')}}</div>
                </FormItem>
                <FormItem :label="$L('任务依赖')" prop="task_dependency">
                    <RadioGroup v-model="settingData.task_dependency">
                        <Radio label="open">{{$L('开启')}}</Radio>
                        <Radio label="close">{{$L('关闭')}}</Radio>
                    </RadioGroup>
                    <div v-if="settingData.task_dependency === 'open'" class="form-tip">{{$L('开启后可设置任务前置/后置关系，并在甘特图中查看依赖。')}}</div>
                    <div v-else class="form-tip">{{$L('关闭后本项目不再展示或修改任务依赖。')}}</div>
                </FormItem>
                <FormItem :label="$L('自动排期')" prop="task_schedule_mode">
                    <RadioGroup v-model="settingData.task_schedule_mode">
                        <Radio label="manual">{{$L('手动排期')}}</Radio>
                        <Radio label="automatic">{{$L('依赖预演')}}</Radio>
                    </RadioGroup>
                    <div class="form-tip">{{$L('依赖预演会在调整甘特图任务后计算后续任务时间；确认前不会修改任务。')}}</div>
                </FormItem>
                <FormItem :label="$L('节假日例外')" prop="task_schedule_holidays">
                    <Input v-model="settingData.task_schedule_holidays" type="textarea" :autosize="{minRows: 2, maxRows: 5}" :placeholder="$L('每行填写一个日期，例如 2026-10-01')"/>
                    <div class="form-tip">{{$L('这些日期即使落在工作日中也按休息日计算，最多366个日期。')}}</div>
                </FormItem>
                <template v-if="settingData.task_schedule_mode === 'automatic'">
                    <FormItem :label="$L('工作日')" prop="task_schedule_workdays">
                        <CheckboxGroup v-model="settingData.task_schedule_workdays">
                            <Checkbox :label="1">{{$L('星期一')}}</Checkbox>
                            <Checkbox :label="2">{{$L('星期二')}}</Checkbox>
                            <Checkbox :label="3">{{$L('星期三')}}</Checkbox>
                            <Checkbox :label="4">{{$L('星期四')}}</Checkbox>
                            <Checkbox :label="5">{{$L('星期五')}}</Checkbox>
                            <Checkbox :label="6">{{$L('星期六')}}</Checkbox>
                            <Checkbox :label="7">{{$L('星期日')}}</Checkbox>
                        </CheckboxGroup>
                    </FormItem>
                    <FormItem :label="$L('排期时区')" prop="task_schedule_timezone">
                        <Input v-model="settingData.task_schedule_timezone" :placeholder="$L('例如 Asia/Shanghai')"/>
                    </FormItem>
                    <FormItem :label="$L('依赖缓冲工作日')" prop="task_schedule_buffer_days">
                        <Input v-model="settingData.task_schedule_buffer_days" type="number" :min="0" :max="30"/>
                    </FormItem>
                </template>
                <FormItem v-if="systemConfig.department_owner_project_view === 'open'" :label="$L('负责人视角')" prop="department_owner_view">
                    <RadioGroup v-model="settingData.department_owner_view">
                        <Radio label="open">{{$L('开启')}}</Radio>
                        <Radio label="close">{{$L('关闭')}}</Radio>
                    </RadioGroup>
                    <div v-if="settingData.department_owner_view === 'open'" class="form-tip">{{$L('开启后，部门负责人可只读查看本项目及其全员可见任务。')}}</div>
                    <div v-else class="form-tip">{{$L('关闭后，本项目及其群聊对部门负责人视角隐藏。')}}</div>
                </FormItem>
            </Form>
            <div class="project-setting-footer">
                <Button type="default" @click="$emit('update:visible', false)">{{$L('取消')}}</Button>
                <Button type="primary" :loading="settingLoad > 0" @click="onSetting">{{$L('保存')}}</Button>
            </div>
        </DrawerOverlay>
</template>

<script>
import DrawerOverlay from "../../../components/DrawerOverlay";
import {mapGetters, mapState} from "vuex";
import {parseScheduleHolidays, parseScheduleWorkdays} from "../../../utils/projectSchedule";

export default {
    name: "ProjectSettingDrawer",

    components: {
        DrawerOverlay,
    },

    props: {
        visible: {type: Boolean, default: false},
        projectId: {type: [Number, String], default: 0},
        project: {type: Object, default: () => ({})},
    },

    data() {
        return {
            settingData: {},
            settingLoad: 0,
        };
    },

    computed: {
        ...mapState(['formOptions', 'systemConfig']),
        ...mapGetters(['aiVisible']),
    },

    watch: {
        visible(value) {
            if (value) this.loadForm();
        },
    },

    methods: {
        loadForm() {
            const project = this.project || {};
            this.settingData = {
                name: project.name,
                desc: project.desc,
                archive_method: project.archive_method,
                archive_days: project.archive_days,
                ai_auto_analyze: project.ai_auto_analyze || 'open',
                task_template_share: project.task_template_share || 'open',
                task_dependency: project.task_dependency || 'open',
                task_schedule_mode: project.task_schedule_mode || 'manual',
                task_schedule_workdays: parseScheduleWorkdays(project.task_schedule_workdays),
                task_schedule_holidays: parseScheduleHolidays(project.task_schedule_holidays).join('\n'),
                task_schedule_timezone: project.task_schedule_timezone || Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC',
                task_schedule_buffer_days: Number(project.task_schedule_buffer_days) || 0,
                department_owner_view: project.department_owner_view || 'open',
            };
            this.$nextTick(() => {
                setTimeout(() => this.$refs.projectDesc && this.$refs.projectDesc.resizeTextarea(), 0);
            });
        },

        formArchived(value) {
            this.settingData = {...this.settingData, archive_method: value};
        },

        onSetting() {
            this.settingLoad++;
            const settingData = {...this.settingData};
            settingData.task_schedule_holidays = parseScheduleHolidays(settingData.task_schedule_holidays);
            this.$store.dispatch("call", {
                url: 'project/update',
                data: Object.assign(settingData, {
                    project_id: this.projectId
                }),
            }).then(({data, msg}) => {
                $A.messageSuccess(msg);
                this.$emit('update:visible', false);
                this.$store.dispatch("saveProject", data);
            }).catch(({msg}) => {
                $A.modalError(msg);
            }).finally(_ => {
                this.settingLoad--;
            });
        },
    },
};
</script>
