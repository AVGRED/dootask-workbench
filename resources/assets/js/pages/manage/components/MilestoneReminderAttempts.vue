<template>
    <Modal :value="visible" :title="$L('提醒记录')" :mask-closable="false" width="600" @input="updateVisible">
        <div class="project-milestone-reminder-attempts">
            <p v-if="milestoneName" class="form-tip">{{milestoneName}}</p>
            <div v-if="loading && attempts.length === 0" class="form-tip">{{$L('加载中...')}}</div>
            <div v-else-if="loadFailed" class="project-capacity-load-state">
                <span><Icon type="md-warning"/> {{$L('数据加载失败')}}</span>
                <Button size="small" type="text" :disabled="loading" @click="load(false)">{{$L('重试')}}</Button>
            </div>
            <div v-else-if="attempts.length === 0" class="form-tip">{{$L('没有提醒记录')}}</div>
            <ul v-else>
                <li v-for="attempt in attempts" :key="attempt.id">
                    <span>{{attempt.attempted_at}}</span>
                    <strong>{{statusLabel(attempt.status)}}</strong>
                    <span v-if="attempt.result_code">{{attempt.result_code}}</span>
                </li>
            </ul>
            <Button v-if="!loadFailed && attempts.length < total" size="small" long :loading="loading" @click="load(true)">{{$L('加载更多')}}</Button>
        </div>
        <div slot="footer" class="adaption">
            <Button type="default" @click="updateVisible(false)">{{$L('关闭')}}</Button>
        </div>
    </Modal>
</template>

<script>
export default {
    name: "MilestoneReminderAttempts",

    props: {
        visible: {type: Boolean, default: false},
        projectId: {type: [Number, String], default: 0},
        milestone: {type: Object, default: null},
    },

    data() {
        return {
            attempts: [],
            total: 0,
            page: 0,
            loading: false,
            loadFailed: false,
            requestKey: 0,
        };
    },

    computed: {
        milestoneId() {
            return this.milestone && this.milestone.id ? Number(this.milestone.id) : 0;
        },
        milestoneName() {
            return this.milestone && this.milestone.name ? this.milestone.name : '';
        },
    },

    watch: {
        visible(value) {
            if (value) {
                this.reset();
                this.load(false);
            } else {
                this.requestKey++;
            }
        },
        milestone() {
            // Switching milestones while the dialog stays open must not show stale rows.
            if (this.visible) {
                this.reset();
                this.load(false);
            }
        },
    },

    methods: {
        updateVisible(value) {
            this.$emit('update:visible', value);
        },

        reset() {
            this.requestKey++;
            this.attempts = [];
            this.total = 0;
            this.page = 0;
            this.loading = false;
            this.loadFailed = false;
        },

        async load(append = false) {
            if (this.loading && append) return;
            const projectId = Number(this.projectId);
            const milestoneId = this.milestoneId;
            if (!projectId || !milestoneId) return;
            const requestKey = ++this.requestKey;
            const page = append ? this.page + 1 : 1;
            this.loading = true;
            this.loadFailed = false;
            try {
                const {data} = await this.$store.dispatch('call', {
                    url: 'projectmilestone/reminderattempts',
                    method: 'post',
                    data: {project_id: projectId, milestone_id: milestoneId, page, per_page: 20},
                });
                if (requestKey !== this.requestKey
                    || projectId !== Number(this.projectId)
                    || milestoneId !== this.milestoneId) return;
                this.attempts = append
                    ? [...this.attempts, ...(data.items || [])]
                    : (data.items || []);
                this.total = Number(data.total) || 0;
                this.page = Number(data.page) || page;
            } catch (e) {
                if (requestKey === this.requestKey
                    && projectId === Number(this.projectId)
                    && milestoneId === this.milestoneId) {
                    this.loadFailed = true;
                }
            } finally {
                if (requestKey === this.requestKey) this.loading = false;
            }
        },

        statusLabel(status) {
            if (status === 'sent') return this.$L('已发送');
            if (status === 'bot_unavailable') return this.$L('提醒机器人不可用');
            if (status === 'chat_unavailable') return this.$L('项目群聊不可用');
            return this.$L('提醒消息投递失败');
        },
    },
};
</script>
