import './helpers/jsdom-setup.mjs';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {createRequire} from 'node:module';
import test from 'node:test';
import compiler from 'vue-template-compiler';
import dayjs from 'dayjs';

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
    render(h) {
        return h('div', {class: `stub-${name}`}, this.$slots.default);
    },
});
for (const name of ['Modal', 'Form', 'FormItem', 'Button', 'InputNumber', 'DatePicker', 'Input', 'Icon', 'Tag', 'Poptip', 'Select', 'Option', 'UserAvatar', 'UserSelect', 'Draggable']) {
    Vue.component(name, componentStub(name));
}

Vue.prototype.$L = (text, ...args) => {
    let index = 0;
    return String(text).replace(/\(\*\)/g, () => (args[index++] ?? ''));
};
const appHelpers = {
    dayjs,
    modalError() {},
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
