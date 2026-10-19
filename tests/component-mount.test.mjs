import './helpers/jsdom-setup.mjs';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {createRequire} from 'node:module';
import test from 'node:test';
import compiler from 'vue-template-compiler';
import dayjs from 'dayjs';
import utc from 'dayjs/plugin/utc.js';
import timezone from 'dayjs/plugin/timezone.js';
import {parseScheduleHolidays, parseScheduleWorkdays} from '../resources/assets/js/utils/projectSchedule.js';

dayjs.extend(utc);
dayjs.extend(timezone);

const require = createRequire(import.meta.url);
const {parse} = require('@babel/parser');
const Vue = require('vue');

Vue.config.productionTip = false;
Vue.config.devtools = false;
Vue.config.errorHandler = error => {
    throw error;
};

const componentStub = name => ({
    name,
    methods: {
        focus() {},
    },
    render(h) {
        return h('div', {class: `stub-${name}`}, this.$slots.default);
    },
});
for (const name of ['Modal', 'Form', 'FormItem', 'Button', 'InputNumber', 'DatePicker', 'Input', 'Icon', 'Tag', 'Poptip', 'Select', 'Option', 'UserAvatar', 'UserSelect', 'Draggable', 'RadioGroup', 'Radio', 'CheckboxGroup', 'Checkbox', 'DrawerOverlay']) {
    Vue.component(name, componentStub(name));
}

Vue.prototype.$L = (text, ...args) => {
    let index = 0;
    return String(text).replace(/\(\*\)/g, () => (args[index++] ?? ''));
};
const modalErrors = [];
const copiedTexts = [];
Vue.prototype.copyText = value => copiedTexts.push(value);
const appHelpers = {
    dayjs,
    modalError: message => modalErrors.push(message),
    messageSuccess() {},
};
Vue.prototype.$A = appHelpers;
// Component scripts (not just templates) call the global helpers directly.
globalThis.$A = appHelpers;
globalThis.$L = Vue.prototype.$L;

const read = path => readFileSync(new URL(`../${path}`, import.meta.url), 'utf8');

/**
 * Compile one single-file component the way the existing node tests do: strip its
 * imports, evaluate the script with stub bindings, then attach the client render
 * function compiled from its template. No bundler or dev server involved.
 */
function loadComponent(path, bindings = {}) {
    const {template, script} = compiler.parseComponent(read(path));
    const compiled = compiler.compile(template.content);
    if (compiled.errors.length) throw new Error(`${path}: ${compiled.errors.join('; ')}`);
    const ast = parse(script.content, {sourceType: 'module'});
    let code = script.content;
    for (const node of [...ast.program.body].reverse()) {
        if (node.type !== 'ImportDeclaration') continue;
        for (const specifier of node.specifiers) bindings[specifier.local.name] ??= {};
        code = code.slice(0, node.start) + code.slice(node.end);
    }
    const options = new Function(...Object.keys(bindings), code.replace('export default', 'return'))(...Object.values(bindings));
    options.render = new Function(compiled.render);
    options.staticRenderFns = compiled.staticRenderFns.map(body => new Function(body));
    return options;
}

const flush = async (times = 4) => {
    for (let i = 0; i < times; i++) await Vue.nextTick();
};

test('MilestoneReminderAttempts renders attempts and closes through update:visible', async () => {
    const calls = [];
    const store = {
        state: {},
        dispatch: (action, payload) => {
            calls.push(payload);
            return Promise.resolve({
                data: {
                    items: [{id: 1, attempted_at: '2026-10-19 09:00:00', status: 'sent', result_code: ''}],
                    total: 1,
                    page: 1,
                },
            });
        },
    };
    const emitted = [];
    const options = loadComponent('resources/assets/js/pages/manage/components/MilestoneReminderAttempts.vue');
    const vm = new Vue({
        components: {Panel: options},
        data: {visible: false, milestone: {id: 3, name: '上线'}},
        render(h) {
            return h('Panel', {
                ref: 'panel',
                props: {visible: this.visible, projectId: 7, milestone: this.milestone},
                on: {'update:visible': value => emitted.push(value)},
            });
        },
    }).$mount();
    assert.ok(vm.$refs.panel, 'child component should mount');
    vm.$refs.panel.$store = store;

    vm.visible = true;
    await flush();

    assert.equal(calls.length, 1);
    assert.deepEqual(calls[0].data, {project_id: 7, milestone_id: 3, page: 1, per_page: 20});

    const text = vm.$el.textContent;
    assert.match(text, /上线/);
    assert.match(text, /2026-10-19 09:00:00/);
    assert.match(text, /已发送/);

    vm.$refs.panel.updateVisible(false);
    await flush();
    assert.deepEqual(emitted, [false]);
    vm.$destroy();
});

test('TaskWork renders logs, reports estimate changes and hides editing when read-only', async () => {
    const calls = [];
    const store = {
        state: {},
        dispatch: (action, payload) => {
            calls.push(payload);
            if (payload.url === 'projecttaskwork/task') {
                return Promise.resolve({
                    data: {
                        logs: [{id: 11, work_date: '2026-10-19', minutes: 120, userid: 7, user: {name: '张三'}, note: '联调'}],
                        logs_total: 1,
                        logs_page: 1,
                        summary: {estimated_minutes: 240, logged_minutes: 120, remaining_minutes: 120, overrun_minutes: 0},
                        can_edit: true,
                        work_date_today: '2026-10-19',
                    },
                });
            }
            return Promise.resolve({data: {estimated_minutes: 300}});
        },
    };
    const options = loadComponent('resources/assets/js/pages/manage/components/TaskWork.vue', {
        emitter: {on() {}, off() {}},
    });

    const mountWork = async (readonly) => {
        const emitted = [];
        // The component loads its data from an immediate watcher during mount, so the
        // store has to be reachable before the instance is created.
        const previousStore = Vue.prototype.$store;
        Vue.prototype.$store = store;
        const vm = new Vue({
            components: {Panel: options},
            data: {readonly},
            render(h) {
                return h('Panel', {
                    ref: 'panel',
                    props: {
                        taskId: 5,
                        projectId: 2,
                        userId: 7,
                        projectOwner: false,
                        readonly: this.readonly,
                        estimatedMinutes: 240,
                    },
                    on: {'estimate-updated': value => emitted.push(value)},
                });
            },
        }).$mount();
        Vue.prototype.$store = previousStore;
        await flush();
        // Later interactions (estimate save, delete) dispatch through the instance.
        vm.$refs.panel.$store = store;
        return {vm, emitted};
    };

    const {vm, emitted} = await mountWork(false);
    assert.equal(calls[0].url, 'projecttaskwork/task');
    assert.deepEqual(calls[0].data, {project_id: 2, task_id: 5, page: 1, per_page: 50});

    const text = vm.$el.textContent;
    assert.match(text, /张三/);
    assert.match(text, /120/);
    assert.match(text, /联调/);
    assert.match(text, /已登记/);
    assert.match(text, /登记工时/);

    const panel = vm.$refs.panel;
    panel.workEstimateInput = 300;
    await panel.saveWorkEstimate();
    await flush();
    assert.deepEqual(emitted, [300]);

    const readOnly = await mountWork(true);
    assert.match(readOnly.vm.$el.textContent, /张三/);
    assert.equal(readOnly.vm.$el.textContent.includes('登记工时'), false);

    readOnly.vm.$destroy();
    vm.$destroy();
});

test('MilestoneManager saves, deletes and reorders through the expected endpoints', async () => {
    const calls = [];
    const events = [];
    const store = {
        state: {},
        dispatch: (action, payload) => {
            calls.push(payload);
            if (payload.url === 'projectmilestone/save') return Promise.resolve({data: {id: 9}});
            if (payload.url === 'projectmilestone/sort') return Promise.resolve({msg: '已保存'});
            return Promise.resolve({data: {}});
        },
    };
    const milestones = [
        {id: 1, name: '里程碑A', status: 'open', task_count: 2, completed_count: 1, overdue_count: 0, progress_percent: 50},
        {id: 2, name: '里程碑B', status: 'completed', task_count: 0, completed_count: 0, overdue_count: 0, progress_percent: 0},
    ];
    const options = loadComponent('resources/assets/js/pages/manage/components/MilestoneManager.vue', {
        Draggable: {props: ['list'], render(h) { return h('div', {class: 'draggable-stub'}, this.$slots.default); }},
        mapState: () => ({formOptions: () => ({})}),
        UserSelect: {props: ['value', 'projectId', 'multipleMax', 'title'], render(h) { return h('div'); }},
        MilestoneReminderAttempts: {props: ['visible', 'projectId', 'milestone'], render(h) { return h('div'); }},
        emitter: {on() {}, off() {}, emit: (...args) => events.push(args)},
    });

    const previousStore = Vue.prototype.$store;
    Vue.prototype.$store = store;
    const vm = new Vue({
        components: {Panel: options},
        render(h) {
            return h('Panel', {
                ref: 'panel',
                props: {visible: true, projectId: 3, milestones, canManage: true, loading: false, loadFailed: false},
            });
        },
    }).$mount();
    Vue.prototype.$store = previousStore;
    await flush();

    const panel = vm.$refs.panel;
    panel.$store = store;
    assert.match(vm.$el.textContent, /里程碑A/);
    assert.match(vm.$el.textContent, /里程碑B/);

    // A blank name is rejected locally instead of hitting the API.
    modalErrors.length = 0;
    panel.form.name = '   ';
    await panel.save();
    assert.equal(calls.length, 0);
    assert.equal(modalErrors.length, 1);

    panel.form.name = '新里程碑';
    panel.form.description = '说明';
    panel.form.target_at = '';
    panel.form.status = 'open';
    panel.form.reminder_days = -1;
    await panel.save();
    const saveCall = calls.find(call => call.url === 'projectmilestone/save');
    assert.deepEqual(saveCall.data, {
        project_id: 3,
        milestone_id: 0,
        name: '新里程碑',
        description: '说明',
        target_at: '',
        status: 'open',
        reminder_days: -1,
        owner_userid: 0,
    });
    assert.deepEqual(events.at(-1), ['projectMilestonesUpdate', 3]);
    // The form resets after a successful create.
    assert.equal(panel.form.name, '');

    await panel.remove({id: 2});
    const deleteCall = calls.find(call => call.url === 'projectmilestone/delete');
    assert.deepEqual(deleteCall.data, {project_id: 3, milestone_id: 2});
    assert.deepEqual(events.at(-1), ['projectMilestonesUpdate', 3]);

    panel.milestones.reverse();
    await panel.saveSort();
    const sortCall = calls.find(call => call.url === 'projectmilestone/sort');
    assert.deepEqual(sortCall.data, {project_id: 3, milestones: [2, 1]});

    vm.$destroy();
});

test('ProjectCapacityOverview loads the selected month, renders totals and enforces the 93 day cap', async () => {
    const calls = [];
    const store = {
        state: {},
        dispatch: (action, payload) => {
            calls.push(payload);
            return Promise.resolve({
                data: {
                    members: [{userid: 7, user: {name: '张三'}, task_count: 2, estimated_minutes: 480, logged_minutes: 120, remaining_minutes: 360, overrun_minutes: 0, capacity_member: true, available_minutes: 960, capacity_utilization_percent: 50}],
                    totals: {estimated_minutes: 480, logged_minutes: 120, remaining_minutes: 360, overrun_minutes: 0},
                    capacity_baseline: {timezone: 'Asia/Shanghai'},
                },
            });
        },
    };
    const options = loadComponent('resources/assets/js/pages/manage/components/ProjectCapacityOverview.vue', {
        buildCapacityCsv: () => '',
    });

    const previousStore = Vue.prototype.$store;
    Vue.prototype.$store = store;
    const vm = new Vue({
        components: {Panel: options},
        data: {visible: false},
        render(h) {
            return h('Panel', {
                ref: 'panel',
                props: {visible: this.visible, projectId: 3, timezone: 'Asia/Shanghai'},
                on: {'update:visible': () => {}},
            });
        },
    }).$mount();
    Vue.prototype.$store = previousStore;

    // The instance gets its store before the dialog opens so the visible watcher can load.
    const panel = vm.$refs.panel;
    panel.$store = store;

    vm.visible = true;
    await flush();

    assert.equal(calls.length, 1);
    assert.equal(calls[0].url, 'projecttaskwork/capacity');
    assert.equal(calls[0].data.project_id, 3);
    const monthStart = dayjs().tz('Asia/Shanghai').startOf('month').format('YYYY-MM-DD');
    const monthEnd = dayjs().tz('Asia/Shanghai').endOf('month').format('YYYY-MM-DD');
    assert.equal(calls[0].data.from, monthStart);
    assert.equal(calls[0].data.to, monthEnd);

    const text = vm.$el.textContent;
    assert.match(text, /8小时 0分钟/);
    assert.match(text, /2小时 0分钟/);
    assert.match(text, /Asia\/Shanghai/);

    // Ranges over 93 days are rejected locally, without a request.
    modalErrors.length = 0;
    const before = calls.length;
    panel.range = [dayjs().tz('Asia/Shanghai').toDate(), dayjs().tz('Asia/Shanghai').add(120, 'day').toDate()];
    await panel.load();
    assert.equal(calls.length, before);
    assert.deepEqual(modalErrors, ['日期范围最多 93 天']);

    vm.$destroy();
});

test('ProjectSettingDrawer fills from the project and saves normalized schedule fields', async () => {
    const calls = [];
    const store = {
        state: {},
        dispatch: (action, payload) => {
            calls.push(typeof action === 'string' ? {action, payload} : action);
            return Promise.resolve({data: {id: 3}, msg: '已保存'});
        },
    };
    const project = {
        id: 3,
        name: '工作台',
        desc: '说明',
        archive_method: 'custom',
        archive_days: 30,
        ai_auto_analyze: 'close',
        task_template_share: 'open',
        task_dependency: 'open',
        task_schedule_mode: 'automatic',
        task_schedule_workdays: '["1","2","3","4","5"]',
        task_schedule_holidays: ['2026-10-02', '2026-10-01'],
        task_schedule_timezone: 'Asia/Shanghai',
        task_schedule_buffer_days: '2',
        department_owner_view: 'open',
    };
    const options = loadComponent('resources/assets/js/pages/manage/components/ProjectSettingDrawer.vue', {
        DrawerOverlay: {props: ['value'], render(h) { return h('div', this.$slots.default); }},
        mapState: () => ({
            formOptions: () => ({}),
            systemConfig: () => ({department_owner_project_view: 'open', task_ai_auto_analyze: 'open'}),
        }),
        mapGetters: () => ({aiVisible: () => true}),
        parseScheduleHolidays,
        parseScheduleWorkdays,
    });

    const emitted = [];
    const vm = new Vue({
        components: {Panel: options},
        data: {visible: false},
        render(h) {
            return h('Panel', {
                ref: 'panel',
                props: {visible: this.visible, projectId: 3, project},
                on: {'update:visible': value => emitted.push(value)},
            });
        },
    }).$mount();
    const panel = vm.$refs.panel;
    panel.$store = store;

    vm.visible = true;
    await flush();

    assert.equal(panel.settingData.name, '工作台');
    assert.equal(panel.settingData.ai_auto_analyze, 'close');
    assert.deepEqual(panel.settingData.task_schedule_workdays, [1, 2, 3, 4, 5]);
    assert.equal(panel.settingData.task_schedule_holidays, '2026-10-01\n2026-10-02');
    assert.equal(panel.settingData.task_schedule_buffer_days, 2);

    await panel.onSetting();
    await flush();

    const update = calls.find(call => call.payload && call.payload.url === 'project/update');
    assert.ok(update, 'project/update should be dispatched');
    assert.equal(update.payload.data.project_id, 3);
    assert.equal(update.payload.data.name, '工作台');
    assert.deepEqual(update.payload.data.task_schedule_holidays, ['2026-10-01', '2026-10-02']);
    assert.deepEqual(emitted, [false]);
    assert.ok(calls.some(call => call.action === 'saveProject'), 'saved project should refresh the store');

    vm.$destroy();
});

test('ProjectInviteDialog fetches the link, copies it and refreshes on demand', async () => {
    const calls = [];
    copiedTexts.length = 0;
    const store = {
        state: {},
        dispatch: (action, payload) => {
            calls.push(payload);
            return Promise.resolve({data: {url: 'https://example.com/invite/abc'}});
        },
    };
    const options = loadComponent('resources/assets/js/pages/manage/components/ProjectInviteDialog.vue', {
        mapState: () => ({formOptions: () => ({})}),
    });
    const vm = new Vue({
        components: {Panel: options},
        data: {visible: false},
        render(h) {
            return h('Panel', {ref: 'panel', props: {visible: this.visible, projectId: 9}});
        },
    }).$mount();
    const panel = vm.$refs.panel;
    panel.$store = store;

    vm.visible = true;
    await flush();

    assert.deepEqual(calls[0].data, {project_id: 9, refresh: 'no'});
    assert.equal(calls[0].url, 'project/invite');
    assert.equal(panel.inviteData.url, 'https://example.com/invite/abc');
    assert.deepEqual(copiedTexts, ['https://example.com/invite/abc']);

    panel.get(true);
    await flush();
    assert.deepEqual(calls[1].data, {project_id: 9, refresh: 'yes'});

    vm.$destroy();
});

test('ProjectTransferDialog transfers the project and refreshes the caches', async () => {
    const calls = [];
    const store = {
        state: {},
        dispatch: (action, payload) => {
            calls.push(payload);
            return Promise.resolve({msg: '已移交'});
        },
    };
    const options = loadComponent('resources/assets/js/pages/manage/components/ProjectTransferDialog.vue', {
        UserSelect: {props: ['value', 'projectId', 'multipleMax', 'title'], render(h) { return h('div'); }},
        mapState: () => ({formOptions: () => ({})}),
    });
    const emitted = [];
    const vm = new Vue({
        components: {Panel: options},
        data: {visible: false},
        render(h) {
            return h('Panel', {
                ref: 'panel',
                props: {visible: this.visible, projectId: 9},
                on: {'update:visible': value => emitted.push(value)},
            });
        },
    }).$mount();
    const panel = vm.$refs.panel;
    panel.$store = store;

    vm.visible = true;
    await flush();
    assert.deepEqual(panel.transferData.owner_userid, []);

    panel.transferData.owner_userid = [7];
    panel.submit();
    await flush();

    assert.deepEqual(calls[0].data, {project_id: 9, owner_userid: 7});
    assert.deepEqual(emitted, [false]);

    vm.$destroy();
});
