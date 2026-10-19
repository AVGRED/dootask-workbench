<template>
    <Modal :value="visible" :title="$L('成员工时汇总')" :mask-closable="false" :width="modalWidth" @input="onVisibleChange">
        <div class="project-capacity-overview">
            <div class="project-capacity-filter">
                <DatePicker v-model="range" type="daterange" format="yyyy-MM-dd" :editable="false" @on-change="invalidate"/>
                <Button type="primary" :loading="loading" @click="load">{{$L('查询')}}</Button>
                <Button v-if="summary.members && summary.members.length" :disabled="loading || !summaryMatchesSelection()" @click="exportCsv">{{$L('导出')}}</Button>
            </div>
            <p class="form-tip">{{$L('计划工时按任务负责人分摊；实际工时按登记日期汇总。结果仅统计所选期间及有完整计划时间的主任务。')}}</p>
            <p v-if="summaryMatchesSelection() && summary.capacity_baseline" class="form-tip">{{$L('工作日')}} · 8 {{$L('小时')}} · {{summary.capacity_baseline.timezone}}</p>
            <div v-if="loadFailed" class="project-capacity-load-state">
                <span><Icon type="md-warning"/> {{$L('数据加载失败')}}</span>
                <Button size="small" type="text" :disabled="loading" @click="load">{{$L('重试')}}</Button>
            </div>
            <div v-else-if="loading && !summary.members" class="form-tip">{{$L('加载中...')}}</div>
            <div v-if="summaryMatchesSelection() && summary.totals" class="project-capacity-totals">
                <span>{{$L('预估')}} {{formatMinutes(summary.totals.estimated_minutes)}}</span>
                <span>{{$L('已登记')}} {{formatMinutes(summary.totals.logged_minutes)}}</span>
                <span>{{$L('剩余')}} {{formatMinutes(summary.totals.remaining_minutes)}}</span>
                <span v-if="summary.totals.overrun_minutes > 0">{{$L('超出')}} {{formatMinutes(summary.totals.overrun_minutes)}}</span>
            </div>
            <Table v-if="!loadFailed && summaryMatchesSelection() && summary.members" :columns="columns" :data="summary.members" :loading="loading"/>
        </div>
    </Modal>
</template>

<script>
import {buildCapacityCsv} from "../../../utils/capacityCsv";

export default {
    name: "ProjectCapacityOverview",

    props: {
        visible: {type: Boolean, default: false},
        projectId: {type: [Number, String], default: 0},
        timezone: {type: String, default: ''},
    },

    data() {
        return {
            loading: false,
            loadFailed: false,
            requestKey: 0,
            range: [$A.dayjs().startOf('month').toDate(), $A.dayjs().endOf('month').toDate()],
            rangeProjectId: 0,
            summary: {},
            summaryRangeKey: '',
        };
    },

    computed: {
        modalWidth() {
            return this.windowWidth < 800 ? '96%' : 720;
        },
        columns() {
            return [
                {title: this.$L('成员'), key: 'user', render: (h, {row}) => h('span', row.user && row.user.name || `#${row.userid}`)},
                {title: this.$L('任务数'), key: 'task_count', width: 80},
                {title: this.$L('预估工时'), key: 'estimated_minutes', render: (h, {row}) => h('span', {
                    attrs: {title: `${this.$L('工作日')} · 8 ${this.$L('小时')}`},
                }, `${this.formatMinutes(row.estimated_minutes)} / ${row.capacity_member ? this.formatMinutes(row.available_minutes) : '—'} (${row.capacity_utilization_percent === null ? '—' : `${row.capacity_utilization_percent}%`})`)},
                {title: this.$L('已登记'), key: 'logged_minutes', render: (h, {row}) => h('span', this.formatMinutes(row.logged_minutes))},
                {title: this.$L('剩余'), key: 'remaining_minutes', render: (h, {row}) => h('span', this.formatMinutes(row.remaining_minutes))},
            ];
        },
    },

    watch: {
        visible(value) {
            if (value) {
                this.resetRange();
                this.load();
            } else {
                this.requestKey++;
            }
        },
    },

    methods: {
        onVisibleChange(value) {
            this.$emit('update:visible', value);
        },

        resetRange() {
            const projectId = Number(this.projectId);
            if (projectId === this.rangeProjectId) return;
            const today = $A.dayjs().tz(this.timezone || $A.timezoneName || 'UTC');
            const asLocalDate = date => new Date(date.year(), date.month(), date.date());
            if (today.isValid()) {
                this.range = [asLocalDate(today.startOf('month')), asLocalDate(today.endOf('month'))];
            }
            this.rangeProjectId = projectId;
        },

        formatMinutes(minutes) {
            const value = Number(minutes) || 0;
            const hours = Math.floor(value / 60);
            const remainder = value % 60;
            return `${hours}${this.$L('小时')} ${remainder}${this.$L('分钟')}`;
        },

        summaryMatchesSelection() {
            if (!this.summaryRangeKey) return false;
            const [fromValue, toValue] = this.range || [];
            if (!fromValue || !toValue) return false;
            const from = $A.dayjs(fromValue);
            const to = $A.dayjs(toValue);
            if (!from.isValid() || !to.isValid()) return false;
            const key = `${Number(this.projectId)}|${from.format('YYYY-MM-DD')}|${to.format('YYYY-MM-DD')}`;
            return key === this.summaryRangeKey;
        },

        invalidate() {
            this.requestKey++;
            this.loading = false;
            this.loadFailed = false;
            this.summary = {};
            this.summaryRangeKey = '';
        },

        async load() {
            const requestKey = ++this.requestKey;
            this.loading = false;
            this.summary = {};
            this.summaryRangeKey = '';
            this.loadFailed = false;
            const [fromValue, toValue] = this.range || [];
            if (!fromValue || !toValue) return;
            const fromDate = $A.dayjs(fromValue).startOf('day');
            const toDate = $A.dayjs(toValue).startOf('day');
            const utcDay = date => Date.UTC(date.year(), date.month(), date.date());
            if (!fromDate.isValid() || !toDate.isValid()) {
                $A.modalError(this.$L('日期范围无效'));
                return;
            }
            if ((utcDay(toDate) - utcDay(fromDate)) / 86400000 > 92) {
                $A.modalError(this.$L('日期范围最多 93 天'));
                return;
            }
            const projectId = Number(this.projectId);
            const rangeKey = `${projectId}|${fromDate.format('YYYY-MM-DD')}|${toDate.format('YYYY-MM-DD')}`;
            this.loading = true;
            this.loadFailed = false;
            try {
                const {data} = await this.$store.dispatch('call', {
                    url: 'projecttaskwork/capacity',
                    data: {
                        project_id: projectId,
                        from: fromDate.format('YYYY-MM-DD'),
                        to: toDate.format('YYYY-MM-DD'),
                    },
                });
                if (requestKey !== this.requestKey || projectId !== Number(this.projectId)) return;
                this.summary = data || {};
                this.summaryRangeKey = rangeKey;
            } catch (e) {
                if (requestKey === this.requestKey && projectId === Number(this.projectId)) this.loadFailed = true;
            } finally {
                if (requestKey === this.requestKey) this.loading = false;
            }
        },

        exportCsv() {
            if (!this.summaryMatchesSelection() || this.loading) return;
            const members = this.summary.members;
            if (!Array.isArray(members) || members.length === 0) return;
            const [fromValue, toValue] = this.range || [];
            if (!fromValue || !toValue) return;
            const columns = [
                this.$L('成员'),
                this.$L('任务数'),
                `${this.$L('预估工时')} / ${this.$L('工作日')} (8 ${this.$L('小时')})`,
                this.$L('已登记'),
                this.$L('剩余'),
                this.$L('超出'),
            ];
            const rows = members.map(row => [
                row.user && row.user.name || `#${row.userid}`,
                row.task_count,
                `${this.formatMinutes(row.estimated_minutes)} / ${row.capacity_member ? this.formatMinutes(row.available_minutes) : '—'} (${row.capacity_utilization_percent == null ? '—' : `${row.capacity_utilization_percent}%`})`,
                this.formatMinutes(row.logged_minutes),
                this.formatMinutes(row.remaining_minutes),
                this.formatMinutes(row.overrun_minutes),
            ]);
            const csv = buildCapacityCsv([columns, ...rows]);
            const blobUrl = window.URL.createObjectURL(new Blob([csv], {type: 'text/csv;charset=utf-8'}));
            const link = document.createElement('a');
            link.href = blobUrl;
            link.download = `project-capacity-${$A.dayjs(fromValue).format('YYYY-MM-DD')}-${$A.dayjs(toValue).format('YYYY-MM-DD')}.csv`;
            document.body.appendChild(link);
            link.click();
            link.remove();
            window.setTimeout(() => window.URL.revokeObjectURL(blobUrl), 0);
        },
    },
};
</script>
