<template>
    <Modal
        v-model="show"
        :title="$L('搜索模板')"
        :width="640"
        class="task-template-browser">
        <div class="search-wrap">
            <Input
                ref="search"
                v-model="keyword"
                :placeholder="$L('搜索模板')"
                clearable
                @on-change="onKeywordChange"
                @keydown.native.up.prevent="moveSelection(-1)"
                @keydown.native.down.prevent="moveSelection(1)"
                @keydown.native.enter.prevent="confirmSelection" />
        </div>
        <div ref="listWrap" class="list-wrap" @scroll="onScroll">
            <div
                v-for="(item, idx) in items"
                :key="item.id"
                :class="['item', {selected: idx === selectedIndex}]"
                @click="selectedIndex = idx"
                @mouseenter="selectedIndex = idx">
                <div class="item-name">
                    {{ item.name }}
                    <span v-if="item.version" class="version">v{{ item.version }}</span>
                </div>
                <div class="item-meta">
                    <span class="origin">{{ $L('来自(*)', item.project_name || '') }}</span>
                    <span v-if="item.user_name" class="creator">· @{{ item.user_name }}</span>
                </div>
            </div>
            <div v-if="loading" class="loading">
                <Loading />
                {{ $L('加载中') }}
            </div>
            <div v-if="!loading && fetchError" class="empty fetch-error">
                {{ $L('请求失败，请稍后重试。') }}
                <Button type="text" size="small" @click="retryFetch">{{ $L('重试') }}</Button>
            </div>
            <div v-else-if="!loading && items.length === 0" class="empty">{{ $L('暂无可用模板') }}</div>
        </div>
        <div v-if="selectedItem" class="template-preview">
            <div class="preview-title">{{ selectedItem.title || selectedItem.name }}</div>
            <div v-if="selectedItem.version" class="preview-version">v{{ selectedItem.version }}</div>
            <div v-if="selectedItem.updated_at" class="preview-updated-at">
                {{$L('更新时间')}}：{{ formatUpdatedAt(selectedItem.updated_at) }}
            </div>
            <div v-if="Number(selectedItem.estimated_minutes) > 0" class="preview-milestone">
                {{$L('预估工时')}}：{{ (Number(selectedItem.estimated_minutes) / 60).toFixed(1) }} {{$L('小时')}}
            </div>
            <div v-if="selectedItem.milestone_name" class="preview-milestone">{{ $L('项目里程碑') }}：{{ selectedItem.milestone_name }}</div>
            <div v-if="selectedItem.content" class="preview-content">
                <VMPreviewNostyle :value="selectedItem.content"/>
            </div>
            <div v-else class="preview-empty">{{ $L('暂无内容') }}</div>
            <div v-if="selectedItem.subtasks && selectedItem.subtasks.length" class="preview-subtasks">
                <div class="preview-subtasks-title">{{ $L('子任务') }}</div>
                <ol>
                    <li v-for="(row, index) in subtaskRows(selectedItem.subtasks)" :key="index" :style="{marginLeft: `${row.depth * 16}px`}">
                        {{ subtaskName(row.subtask) }}<span v-if="row.subtask.milestone_name"> · {{ row.subtask.milestone_name }}</span>
                    </li>
                </ol>
            </div>
        </div>
        <div slot="footer" class="template-browser-footer">
            <Button @click="show = false">{{ $L('取消') }}</Button>
            <Button type="primary" :disabled="!selectedItem" @click="confirmSelection">{{ $L('使用此模板') }}</Button>
        </div>
    </Modal>
</template>

<script>
import VMPreviewNostyle from '../../../components/VMEditor/nostyle.vue'

export default {
    name: 'TaskTemplateBrowser',
    components: {VMPreviewNostyle},
    props: {
        value: { type: Boolean, default: false },
        currentProjectId: { type: Number, default: 0 },
    },
    data() {
        return {
            keyword: '',
            page: 1,
            pageSize: 20,
            total: 0,
            items: [],
            loading: false,
            fetchError: false,
            selectedIndex: 0,
            keywordTimer: null,
            fetchSeq: 0,
        }
    },
    beforeDestroy() {
        clearTimeout(this.keywordTimer)
    },
    computed: {
        show: {
            get() { return this.value },
            set(v) { this.$emit('input', v) },
        },
        selectedItem() {
            return this.items[this.selectedIndex] || null
        },
    },
    watch: {
        show(v) {
            if (v) {
                this.keyword = ''
                this.page = 1
                this.items = []
                this.selectedIndex = 0
                this.fetch()
                this.$nextTick(() => {
                    this.$refs.search && this.$refs.search.focus && this.$refs.search.focus()
                })
            }
        },
        currentProjectId() {
            if (!this.show) return
            clearTimeout(this.keywordTimer)
            this.page = 1
            this.items = []
            this.total = 0
            this.selectedIndex = 0
            this.fetch()
        },
    },
    methods: {
        formatUpdatedAt(value) {
            return value ? $A.newDateString(value) : ''
        },
        subtaskName(subtask) {
            return typeof subtask === 'string' ? subtask : (subtask && subtask.name) || ''
        },
        subtaskRows(items, depth = 0, rows = []) {
            (items || []).forEach(subtask => {
                rows.push({subtask, depth})
                this.subtaskRows(typeof subtask === 'string' ? [] : subtask.children, depth + 1, rows)
            })
            return rows
        },
        onKeywordChange() {
            clearTimeout(this.keywordTimer)
            this.keywordTimer = setTimeout(() => {
                this.page = 1
                this.items = []
                this.selectedIndex = 0
                this.fetch()
            }, 200)
        },
        onScroll() {
            const el = this.$refs.listWrap
            if (!el || this.loading || this.fetchError) return
            if (el.scrollTop + el.clientHeight >= el.scrollHeight - 40) {
                if (this.items.length < this.total) {
                    this.page += 1
                    this.fetch(true)
                }
            }
        },
        async fetch(append = false) {
            const seq = ++this.fetchSeq
            this.loading = true
            this.fetchError = false
            try {
                const {data} = await this.$store.dispatch('call', {
                    url: 'project/task/template_search',
                    data: {
                        keyword: this.keyword,
                        current_project_id: this.currentProjectId || 0,
                        page: this.page,
                        page_size: this.pageSize,
                    },
                })
                if (seq !== this.fetchSeq) return  // 后续请求已发起，丢弃此次结果
                this.total = data.total || 0
                this.items = append ? [...this.items, ...(data.items || [])] : (data.items || [])
            } catch (e) {
                if (seq === this.fetchSeq) this.fetchError = true
            } finally {
                if (seq === this.fetchSeq) this.loading = false
            }
        },
        retryFetch() {
            this.fetch(this.page > 1 && this.items.length > 0)
        },
        moveSelection(delta) {
            if (this.items.length === 0) return
            const next = this.selectedIndex + delta
            this.selectedIndex = Math.max(0, Math.min(this.items.length - 1, next))
        },
        confirmSelection() {
            const item = this.items[this.selectedIndex]
            if (item) this.pick(item)
        },
        pick(item) {
            this.$emit('pick', item)
            this.show = false
        },
    },
}
</script>

<style lang="scss" scoped>
.task-template-browser {
    .search-wrap {
        margin-bottom: 8px;
    }
    .list-wrap {
        max-height: 300px;
        overflow-y: auto;
        > div {
            &:last-child {
                margin-bottom: 12px;
            }
        }
    }
    .template-preview {
        margin-top: 12px;
        padding: 12px;
        border: 1px solid #e8eaec;
        border-radius: 4px;
        .preview-title {
            margin-bottom: 8px;
            font-weight: 600;
            overflow-wrap: anywhere;
        }
        .preview-updated-at {
            margin-bottom: 8px;
            color: #909399;
            font-size: 12px;
        }
        .preview-milestone {
            margin-bottom: 8px;
            color: #606266;
            font-size: 12px;
        }
        .preview-version {
            margin-bottom: 6px;
            color: #909399;
            font-size: 12px;
        }
        .preview-content {
            max-height: 180px;
            overflow: auto;
            overflow-wrap: anywhere;
        }
        .preview-empty {
            color: #909399;
            font-size: 12px;
        }
    }
    .template-browser-footer {
        display: flex;
        justify-content: flex-end;
        gap: 8px;
    }
    .item {
        padding: 10px 12px;
        border-radius: 4px;
        cursor: pointer;
        &.selected {
            background: rgba(64, 158, 255, 0.1);
        }
        .item-name {
            font-weight: 500;
            .version {
                margin-left: 6px;
                color: #909399;
                font-size: 11px;
                font-weight: normal;
            }
        }
        .item-meta {
            margin-top: 4px;
            font-size: 12px;
            color: #909399;
            .creator {
                margin-left: 4px;
            }
        }
    }
    .loading {
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 8px;
        .common-loading {
            width: 18px;
            height: 18px;
            margin: 0;
        }
    }
    .loading, .empty {
        text-align: center;
        padding: 20px;
        color: #909399;
    }
}
</style>
