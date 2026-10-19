<template>
    <Modal :value="visible" :title="$L('移交项目')" :mask-closable="false" @input="onVisibleChange">
        <Form :model="transferData" v-bind="formOptions" @submit.native.prevent>
            <FormItem prop="owner_userid" :label="$L('新项目负责人')">
                <UserSelect v-model="transferData.owner_userid" :multiple-max="1" :title="$L('选择项目负责人')"/>
            </FormItem>
        </Form>
        <div slot="footer" class="adaption">
            <Button type="default" @click="onVisibleChange(false)">{{$L('取消')}}</Button>
            <Button type="primary" :loading="load > 0" @click="submit">{{$L('移交')}}</Button>
        </div>
    </Modal>
</template>

<script>
import {mapState} from "vuex";
import UserSelect from "../../../components/UserSelect.vue";

export default {
    name: "ProjectTransferDialog",

    components: {
        UserSelect,
    },

    props: {
        visible: {type: Boolean, default: false},
        projectId: {type: [Number, String], default: 0},
    },

    data() {
        return {
            transferData: {},
            load: 0,
        };
    },

    computed: {
        ...mapState(['formOptions']),
    },

    watch: {
        visible(value) {
            if (value) {
                this.transferData = {};
                this.$set(this.transferData, 'owner_userid', []);
            }
        },
    },

    methods: {
        onVisibleChange(value) {
            this.$emit('update:visible', value);
        },

        submit() {
            this.load++;
            this.$store.dispatch("call", {
                url: 'project/transfer',
                data: {
                    project_id: this.projectId,
                    owner_userid: this.transferData.owner_userid[0],
                },
            }).then(({msg}) => {
                $A.messageSuccess(msg);
                this.$emit('update:visible', false);
                this.$store.dispatch("getProjectOne", this.projectId).catch(() => {});
                this.$store.dispatch("getTaskForProject", this.projectId).catch(() => {});
            }).catch(({msg}) => {
                $A.modalError(msg);
            }).finally(_ => {
                this.load--;
            });
        },
    },
};
</script>
