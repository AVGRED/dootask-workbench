<template>
    <Modal :value="visible" :title="$L('邀请链接')" :mask-closable="false" @input="onVisibleChange">
        <Form :model="inviteData" v-bind="formOptions" @submit.native.prevent>
            <FormItem :label="$L('链接地址')">
                <Input ref="inviteInput" v-model="inviteData.url" type="textarea" :rows="3" @on-focus="focus" readonly/>
                <div class="form-tip">
                    {{$L('可通过此链接直接加入项目。')}}
                    <Poptip
                        confirm
                        placement="bottom"
                        :ok-text="$L('确定')"
                        :cancel-text="$L('取消')"
                        @on-ok="get(true)"
                        transfer>
                        <div slot="title">
                            <p><strong>{{$L('注意：刷新将导致原来的邀请链接失效！')}}</strong></p>
                        </div>
                        <a href="javascript:void(0)">{{$L('刷新链接')}}</a>
                    </Poptip>
                </div>
            </FormItem>
        </Form>
        <div slot="footer" class="adaption">
            <Button type="default" @click="onVisibleChange(false)">{{$L('取消')}}</Button>
            <Button type="primary" :loading="load > 0" @click="copy">{{$L('复制')}}</Button>
        </div>
    </Modal>
</template>

<script>
import {mapState} from "vuex";

export default {
    name: "ProjectInviteDialog",

    props: {
        visible: {type: Boolean, default: false},
        projectId: {type: [Number, String], default: 0},
    },

    data() {
        return {
            inviteData: {},
            load: 0,
        };
    },

    computed: {
        ...mapState(['formOptions']),
    },

    watch: {
        visible(value) {
            if (value) {
                this.inviteData = {};
                this.get();
            }
        },
    },

    methods: {
        onVisibleChange(value) {
            this.$emit('update:visible', value);
        },

        get(refresh) {
            this.load++;
            this.$store.dispatch("call", {
                url: 'project/invite',
                data: {
                    project_id: this.projectId,
                    refresh: refresh === true ? 'yes' : 'no'
                },
            }).then(({data}) => {
                this.inviteData = data;
                this.copy();
            }).catch(({msg}) => {
                $A.modalError(msg);
            }).finally(_ => {
                this.load--;
            });
        },

        copy() {
            if (!this.inviteData.url) {
                return;
            }
            this.focus();
            this.copyText(this.inviteData.url);
        },

        focus() {
            this.$nextTick(_ => {
                this.$refs.inviteInput && this.$refs.inviteInput.focus({cursor: 'all'});
            });
        },
    },
};
</script>
