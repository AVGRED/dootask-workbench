<template>
    <div class="project-panel" :class="[tabTypeActive]">
        <PageTitle :title="projectData.name"/>
        <div class="project-titbox">
            <div class="project-title">
                <div class="project-back" @click="onBack">
                    <i class="taskfont">&#xe676;</i>
                </div>
                <h1 @click="showName" class="user-select-auto" v-html="transformEmojiToHtml(projectData.name)"></h1>
                <div v-if="loading" class="project-load"><Loading/></div>
            </div>
            <ul class="project-icons">
                <li class="project-avatar" :class="{'cursor-default': !isOwnerOrDeputy || isDepartmentReadonly}" @click="projectDropdown('user')">
                    <ul>
                        <li>
                            <UserAvatarTip :userid="projectData.owner_userid" :size="36" :borderWidth="2" :openDelay="0">
                                <p>{{$L('项目负责人')}}</p>
                            </UserAvatarTip>
                            <Badge v-if="(windowWidth <= 980 || projectData.cacheParameter.chat) && projectUser.length > 0" type="normal" :overflow-count="999" :count="projectData.project_user.length"/>
                        </li>
                        <template v-for="item in (windowWidth > 980 && !projectData.cacheParameter.chat ? projectUser : [])">
                            <li v-if="item.userid === -1" :key="item.userid" class="more">
                                <ETooltip :disabled="$isEEUIApp || windowTouch" :content="$L('共(*)个成员', projectData.project_user.length)">
                                    <Icon type="ios-more"/>
                                </ETooltip>
                            </li>
                            <li v-else :key="item.userid" :class="{'is-deputy': isDeputyUid(item.userid)}">
                                <UserAvatarTip :userid="item.userid" :size="36" :borderWidth="2" :openDelay="0">
                                    <p v-if="isDeputyUid(item.userid)">{{$L('项目管理员')}}</p>
                                </UserAvatarTip>
                            </li>
                        </template>
                    </ul>
                </li>
                <li v-if="!projectData.department_readonly" class="project-icon" @click="addTaskOpen(0)">
                    <ETooltip :disabled="$isEEUIApp || windowTouch" :content="$L('添加任务')">
                        <Icon class="menu-icon" type="md-add" />
                    </ETooltip>
                </li>
                <li :class="['project-icon', searchText!='' ? 'active' : '']">
                    <Tooltip :always="searchText!=''" @on-popper-show="searchFocus" theme="light" :rawIndex="10">
                        <Icon class="menu-icon" type="ios-search" @click="searchFocus" />
                        <div slot="content">
                            <Input v-model="searchText" ref="searchInput" :placeholder="$L('ID、名称、描述...')" class="search-input" clearable/>
                        </div>
                    </Tooltip>
                </li>
                <li :class="['project-icon', windowLandscape && projectData.cacheParameter.chat ? 'active' : '']" @click="toggleParameter('chat')">
                    <Icon class="menu-icon" type="ios-chatbubbles" />
                    <Badge class="menu-badge" :overflow-count="999" :count="msgUnread"></Badge>
                </li>
                <li class="project-icon">
                    <EDropdown @command="projectDropdown" trigger="click" transfer>
                        <Icon class="menu-icon" type="ios-more" />
                        <EDropdownMenu v-if="isDepartmentReadonly" slot="dropdown" class="project-panel-project-menu-dropdown">
                            <EDropdownItem command="favorite">{{$L(projectData.favorited ? '取消收藏' : '收藏项目')}}</EDropdownItem>
                            <EDropdownItem command="milestones" divided>{{$L('项目里程碑')}}</EDropdownItem>
                            <EDropdownItem command="log" divided>{{$L('项目动态')}}</EDropdownItem>
                            <EDropdownItem command="archived_task">{{$L('已归档任务')}}</EDropdownItem>
                            <EDropdownItem command="deleted_task">{{$L('已删除任务')}}</EDropdownItem>
                        </EDropdownMenu>
                        <EDropdownMenu v-else-if="isOwnerOrDeputy" slot="dropdown" class="project-panel-project-menu-dropdown">
                            <EDropdownItem command="setting">{{$L('项目设置')}}</EDropdownItem>
                            <EDropdownItem command="permissions">{{$L('权限设置')}}</EDropdownItem>
                            <EDropdownItem command="task_template">{{$L('任务模板')}}</EDropdownItem>
                            <EDropdownItem command="task_tag">{{$L('任务标签')}}</EDropdownItem>
                            <EDropdownItem command="workflow">{{$L('工作流设置')}}</EDropdownItem>
                            <EDropdownItem command="milestones">{{$L('项目里程碑')}}</EDropdownItem>
                            <EDropdownItem command="user" divided>{{$L('成员管理')}}</EDropdownItem>
                            <EDropdownItem command="invite">{{$L('邀请链接')}}</EDropdownItem>
                            <EDropdownItem command="favorite" divided>{{$L(projectData.favorited ? '取消收藏' : '收藏项目')}}</EDropdownItem>
                            <EDropdownItem command="log">{{$L('项目动态')}}</EDropdownItem>
                            <EDropdownItem command="archived_task">{{$L('已归档任务')}}</EDropdownItem>
                            <EDropdownItem command="deleted_task">{{$L('已删除任务')}}</EDropdownItem>
                            <EDropdownItem command="archived" divided>{{$L('归档项目')}}</EDropdownItem>
                            <!--主独占（仅主负责人可见）-->
                            <template v-if="canManageDeputy">
                                <EDropdownItem command="transfer">{{$L('移交项目')}}</EDropdownItem>
                                <EDropdownItem command="delete" style="color:#f40">{{$L('删除项目')}}</EDropdownItem>
                            </template>
                        </EDropdownMenu>
                        <EDropdownMenu v-else slot="dropdown">
                            <EDropdownItem command="task_tag">{{$L('任务标签')}}</EDropdownItem>
                            <EDropdownItem command="milestones">{{$L('项目里程碑')}}</EDropdownItem>
                            <EDropdownItem command="favorite" divided>{{$L(projectData.favorited ? '取消收藏' : '收藏项目')}}</EDropdownItem>
                            <EDropdownItem command="log">{{$L('项目动态')}}</EDropdownItem>
                            <EDropdownItem command="archived_task">{{$L('已归档任务')}}</EDropdownItem>
                            <EDropdownItem command="deleted_task">{{$L('已删除任务')}}</EDropdownItem>
                            <EDropdownItem command="exit" divided style="color:#f40">{{$L('退出项目')}}</EDropdownItem>
                        </EDropdownMenu>
                    </EDropdown>
                </li>
            </ul>
        </div>
        <Alert v-if="projectData.department_readonly" class="project-readonly-alert" type="info" show-icon>
            {{$L('当前为负责人视角：你可查看项目和任务，并参与讨论，但不能编辑项目或任务。')}}
        </Alert>
        <div class="project-subbox">
            <div class="project-subtitle user-select-auto" @click="showDesc">
                <VMPreviewNostyle ref="descPreview" :value="projectData.desc"/>
            </div>
            <div class="project-switch">
                <div v-if="completedCount > 0" class="project-checkbox">
                    <Checkbox :value="projectData.cacheParameter.completedTask" @on-change="toggleParameter('completedTask')">{{$L('显示已完成')}}</Checkbox>
                </div>
                <div class="project-select">
                    <ProjectFlowFilterDropdown
                        v-model="flowFilterOpen"
                        :touch="$isEEUIApp || windowTouch"
                        :groups="flowFilterGroups"
                        :selected="flowFilter"
                        :total="flowFilterTotal"
                        @change="flowFilterChange"
                        @clear="flowFilterClear">
                        <span :class="['project-flow', flowTrigger.class]" :style="flowTrigger.style">
                            <span class="project-flow-text">{{ flowTrigger.title }}</span>
                            <Icon v-if="flowFilterTotal > 0" type="ios-close-circle" class="project-flow-clear" @click.native.stop="flowFilterClear()"/>
                        </span>
                    </ProjectFlowFilterDropdown>
                </div>
                <Select
                    v-model="selectedTaskViewId"
                    class="project-task-view-select"
                    clearable
                    :disabled="taskViewsLoading"
                    :placeholder="$L('任务视图')"
                    @on-change="applyTaskView">
                    <Option v-for="item in taskViews" :key="item.id" :value="item.id" :label="`${item.name}${item.visibility === 'project' ? ' · '+$L('共享') : ''}${item.is_default ? ' · '+$L('默认') : ''}`"/>
                </Select>
                <div v-if="taskViewsLoadFailed" class="project-task-views-load-state">
                    <span><Icon type="md-warning"/> {{$L('数据加载失败')}}</span>
                    <Button size="small" type="text" :disabled="taskViewsLoading" @click="loadTaskViews">{{$L('重试')}}</Button>
                </div>
                <EDropdown v-if="!isDepartmentReadonly" class="project-task-view-actions" trigger="click" transfer @command="taskViewCommand">
                    <button type="button" class="project-task-view-button"><Icon type="ios-bookmarks-outline"/></button>
                    <EDropdownMenu slot="dropdown">
                        <EDropdownItem command="save">{{$L('保存当前为个人视图')}}</EDropdownItem>
                        <EDropdownItem v-if="isOwnerOrDeputy" command="save_shared">{{$L('保存为项目共享视图')}}</EDropdownItem>
                        <EDropdownItem divided disabled>{{$L('常用视图')}}</EDropdownItem>
                        <EDropdownItem command="preset_my_undone">{{$L('我的未完成')}}</EDropdownItem>
                        <EDropdownItem command="preset_overdue">{{$L('逾期任务')}}</EDropdownItem>
                        <EDropdownItem command="preset_due_week">{{$L('本周到期')}}</EDropdownItem>
                        <EDropdownItem command="preset_unscheduled">{{$L('未排期')}}</EDropdownItem>
                        <EDropdownItem command="preset_due_next_week">{{$L('下周到期')}}</EDropdownItem>
                        <EDropdownItem command="preset_near_milestone">{{$L('近期交付')}}</EDropdownItem>
                        <EDropdownItem v-if="selectedTaskView" command="copy" divided>{{$L('复制视图')}}</EDropdownItem>
                        <EDropdownItem v-if="selectedTaskView && selectedTaskView.can_edit" command="update">{{$L('更新当前视图定义')}}</EDropdownItem>
                        <EDropdownItem v-if="selectedTaskView && selectedTaskView.can_edit" command="rename">{{$L('重命名视图')}}</EDropdownItem>
                        <EDropdownItem v-if="selectedTaskView && !selectedTaskView.is_default" command="default">{{$L('设为默认视图')}}</EDropdownItem>
                        <EDropdownItem v-if="selectedTaskView && selectedTaskView.can_edit" command="delete" divided style="color:#f40">{{$L('删除视图')}}</EDropdownItem>
                    </EDropdownMenu>
                </EDropdown>
                <EDropdown v-if="tabTypeActive === 'table'" class="project-task-view-actions" trigger="click" transfer @command="toggleTaskTableField">
                    <button type="button" class="project-task-view-button"><Icon type="ios-eye-outline"/></button>
                    <EDropdownMenu slot="dropdown">
                        <EDropdownItem v-for="field in taskTableFieldOptions" :key="field.key" :command="field.key">
                            {{ tableFields.includes(field.key) ? '✓ ' : '' }}{{ $L(field.label) }}
                        </EDropdownItem>
                    </EDropdownMenu>
                </EDropdown>
                <DatePicker
                    v-model="taskDateRange"
                    class="project-task-date-filter"
                    type="daterange"
                    format="yyyy-MM-dd"
                    :editable="false"
                    clearable
                    :placeholder="$L('计划时间')"/>
                <Select
                    v-if="milestoneList.length > 0"
                    v-model="milestoneFilterId"
                    class="project-milestone-filter"
                    clearable
                    filterable
                    :placeholder="$L('按里程碑筛选')">
                    <Option v-for="item in milestoneList" :key="item.id" :value="item.id" :label="item.name"/>
                </Select>
                <ProjectTableGroupDropdown
                    v-if="tabTypeActive === 'table'"
                    v-model="tableGroupsOpen"
                    class="project-table-settings"
                    :touch="$isEEUIApp || windowTouch"
                    :selected="visibleTableGroups"
                    :counts="tableGroupsOpen ? tableGroupCounts : null"
                    @change="setTableGroupVisible">
                    <button type="button" class="project-table-settings-icon">
                        <Icon type="md-funnel"/>
                    </button>
                </ProjectTableGroupDropdown>
                <div class="project-switch-button">
                    <div class="slider" :style="tabTypeStyle"></div>
                    <div @click="tabTypeChange('column')" :class="{ 'active': tabTypeActive === 'column'}"><i class="taskfont">&#xe60c;</i></div>
                    <div @click="tabTypeChange('table')" :class="{ 'active': tabTypeActive === 'table'}"><i class="taskfont">&#xe66a;</i></div>
                    <div @click="tabTypeChange('gantt')" :class="{ 'active': tabTypeActive === 'gantt'}"><i class="taskfont">&#xe797;</i></div>
                </div>
            </div>
        </div>
        <button v-if="milestoneList.length > 0" type="button" class="project-milestone-overview" @click="openMilestoneOverview">
            <span class="project-milestone-overview-title"><Icon type="md-flag"/> {{$L('近期交付')}}</span>
            <strong>{{milestoneOverview.next ? milestoneOverview.next.name : $L('暂无进行中的里程碑')}}</strong>
            <span v-if="milestoneOverview.next && milestoneOverview.next.target_at">{{milestoneOverview.next.target_at}}</span>
            <span>{{$L('完成率')}} {{milestoneOverview.progress}}%</span>
            <span v-if="milestoneOverview.overdue > 0" class="project-milestone-overview-risk">{{$L('逾期任务')}} {{milestoneOverview.overdue}}</span>
        </button>
        <div v-if="tabTypeActive === 'gantt'" class="project-capacity-launch">
            <Button size="small" type="text" @click="openCapacityOverview">{{$L('成员工时汇总')}}</Button>
        </div>
        <div
            v-if="tabTypeActive === 'column'"
            ref="projectColumn"
            class="project-column"
            @scroll="handleColumnScroll">
            <Draggable
                :list="columnList"
                :animation="150"
                :disabled="sortDisabled || isDepartmentReadonly || $isEEUIApp || windowTouch"
                class="column-list"
                tag="ul"
                draggable=".column-item"
                @sort="sortUpdate(true)">
                <li
                    v-for="column in columnList"
                    :ref="`column_${column.id}`"
                    :key="column.id"
                    :data-id="column.id"
                    class="column-item">
                    <div
                        :class="['column-head', column.color ? 'custom-color' : '']"
                        :style="column.color ? {backgroundColor: column.color} : {}">
                        <div class="column-head-title">
                            <AutoTip><span v-html="transformEmojiToHtml(column.name)"></span></AutoTip>
                            <em>({{panelTask(column.tasks).length}})</em>
                        </div>
                        <div class="column-head-icon">
                            <div v-if="columnLoad[column.id] === true" class="loading"><Loading /></div>
                            <EDropdown
                                v-else-if="!isDepartmentReadonly"
                                trigger="click"
                                size="medium"
                                @command="dropColumn(column, $event)">
                                <Icon type="ios-more" />
                                <EDropdownMenu slot="dropdown" class="project-panel-more-dropdown-menu">
                                    <li class="project-panel-more-dropdown-warp">
                                        <ul>
                                            <EDropdownItem command="title">
                                                <div class="item">
                                                    <Icon type="md-create" />{{$L('修改')}}
                                                </div>
                                            </EDropdownItem>
                                            <EDropdownItem command="archive_completed">
                                                <div class="item">
                                                    <Icon type="ios-filing" />{{$L('归档')}}
                                                </div>
                                            </EDropdownItem>
                                            <EDropdownItem command="remove">
                                                <div class="item">
                                                    <Icon type="md-trash" />{{$L('删除')}}
                                                </div>
                                            </EDropdownItem>
                                            <EDropdownItem v-for="(c, k) in $store.state.columnColorList" :key="k" :divided="k==0" :command="c">
                                                <div class="item">
                                                    <i class="taskfont" :style="{color:c.color||'#ddd'}" v-html="c.color == column.color ? '&#xe61d;' : '&#xe61c;'"></i>{{$L(c.name)}}
                                                </div>
                                            </EDropdownItem>
                                        </ul>
                                    </li>
                                </EDropdownMenu>
                            </EDropdown>
                            <Icon v-if="!isDepartmentReadonly" class="last" type="md-add" @click="addTopShow(column.id, true)" />
                        </div>
                    </div>
                    <Scrollbar
                        class="column-task"
                        class-name="task-scrollbar"
                        @on-scroll="handleTaskScroll">
                        <div v-if="!isDepartmentReadonly && !!columnTopShow[column.id]" class="task-item additem">
                            <TaskAddSimple
                                :column-id="column.id"
                                :project-id="projectId"
                                :add-top="true"
                                @on-close="addTopShow(column.id, false)"
                                @on-priority="addTaskOpen"
                                auto-active/>
                        </div>
                        <Draggable
                            :list="column.tasks"
                            :animation="150"
                            :disabled="sortDisabled || isDepartmentReadonly || $isEEUIApp || windowTouch"
                            class="task-list"
                            draggable=".task-draggable"
                            filter=".complete"
                            group="task"
                            @sort="sortUpdate"
                            @remove="sortUpdate">
                            <div
                                v-for="item in column.tasks"
                                :key="`${column.id}_${item.id}`"
                                :data-id="item.id"
                                :class="['task-item task-draggable', item.complete_at ? 'complete' : '', taskIsHidden(item) ? 'hidden' : '']"
                                :style="$A.generateColorVarStyle(item.flow_item_color, [10], 'flow-item-custom-color', taskItemStyle(item))"
                                @click="openTask(item)">
                                <template v-if="taskItemVisible(item)">
                                    <div :class="['task-head', item.desc ? 'has-desc' : '']">
                                        <div class="task-title">
                                            <!--工作流状态-->
                                            <span v-if="item.flow_item_name" :class="item.flow_item_status" @click.stop="openMenu($event, item)">{{item.flow_item_name}}</span>
                                            <!--任务描述-->
                                            <pre>{{item.name}}</pre>
                                        </div>
                                        <div class="task-menu" @click.stop="">
                                            <TaskMenu v-if="!isDepartmentReadonly" :ref="`taskMenu_${item.id}`" :task="item" icon="ios-more"/>
                                        </div>
                                    </div>
                                    <template v-if="!item.complete_at">
                                        <div v-if="item.desc" class="task-desc"><pre v-html="item.desc"></pre></div>
                                        <TaskTag v-if="item.task_tag.length > 0" class="task-tags" :tags="item.task_tag"/>
                                        <div class="task-users">
                                            <ul>
                                                <li v-for="(user, keyu) in ownerUser(item.task_user)" :key="keyu">
                                                    <UserAvatar :userid="user.userid" size="32" :borderWidth="2" :borderColor="item.color"/>
                                                </li>
                                                <li v-if="ownerUser(item.task_user).length === 0" class="no-owner">
                                                    <Button type="primary" size="small" ghost @click.stop="openTask(item, true)">{{$L('领取任务')}}</Button>
                                                </li>
                                            </ul>
                                            <div v-if="item.file_num > 0" class="task-icon">{{item.file_num}}<Icon type="ios-link-outline" /></div>
                                            <div v-if="item.msg_num > 0" class="task-icon">{{item.msg_num}}<Icon type="ios-chatbubbles-outline" /></div>
                                        </div>
                                        <div class="task-progress">
                                            <div v-if="item.sub_num > 0" class="task-sub-num">{{item.sub_complete}}/{{item.sub_num}}</div>
                                            <Progress :percent="item.percent" :stroke-width="6" />
                                            <ETooltip
                                                v-if="item.end_at"
                                                :class="['task-time', item.today ? 'today' : '', item.overdue ? 'overdue' : '']"
                                                :disabled="$isEEUIApp || windowTouch"
                                                :open-delay="600"
                                                :content="item.end_at">
                                                <div v-if="!item.complete_at"><i class="taskfont">&#xe71d;</i>{{ expiresFormat(item.end_at) }}</div>
                                            </ETooltip>
                                        </div>
                                        <em v-if="item.p_name" class="priority-color" :style="{backgroundColor:item.p_color}"></em>
                                    </template>
                                </template>
                            </div>
                            <div v-if="!isDepartmentReadonly" class="task-item additem">
                                <TaskAddSimple
                                    :column-id="column.id"
                                    :project-id="projectId"
                                    @on-priority="addTaskOpen"/>
                            </div>
                        </Draggable>
                    </Scrollbar>
                </li>
                <li v-if="!isDepartmentReadonly" :class="['add-column', addColumnShow ? 'show-input' : '']">
                    <div class="add-column-text" @click="addColumnOpen">
                        <Icon type="md-add" />{{$L('添加列表')}}
                    </div>
                    <div class="add-column-input">
                        <Input
                            ref="addColumnName"
                            v-model="addColumnName"
                            @on-blur="addColumnBlur"
                            @on-keydown="onAddColumnKeydown"
                            @on-clear="addColumnShow=false"
                            :placeholder="$L('列表名称，回车创建')"
                            clearable/>
                    </div>
                </li>
            </Draggable>
        </div>
        <Scrollbar
            v-else-if="tabTypeActive === 'table'"
            ref="projectTableScroll"
            class="project-table"
            enable-x
            @on-scroll="handleTaskScroll">
            <div class="project-table-head">
                <Row class="task-row">
                    <Col :span="tableNameSpan">
                        <div class="ellipsis"># {{$L('任务名称')}}</div>
                    </Col>
                    <Col v-if="tableFields.includes('column')" span="3">
                        <div class="ellipsis">{{$L('列表')}}</div>
                    </Col>
                    <Col v-if="tableFields.includes('priority')" span="3">
                        <div class="sort" @click="onSort('level')">
                            <div class="head-title">{{$L('优先级')}}</div>
                            <div class="head-sort">
                                <Icon :class="{on:sortField=='level' && sortType=='asc'}" type="md-arrow-dropup" />
                                <Icon :class="{on:sortField=='level' && sortType=='desc'}" type="md-arrow-dropdown" />
                            </div>
                        </div>
                    </Col>
                    <Col v-if="tableFields.includes('owner')" span="3">
                        <div class="ellipsis">{{$L('负责人')}}</div>
                    </Col>
                    <Col v-if="tableFields.includes('due')" span="3">
                        <div class="sort" @click="onSort('end_at')">
                            <div class="head-title">{{$L('到期时间')}}</div>
                            <div class="head-sort">
                                <Icon :class="{on:sortField=='end_at' && sortType=='asc'}" type="md-arrow-dropup" />
                                <Icon :class="{on:sortField=='end_at' && sortType=='desc'}" type="md-arrow-dropdown" />
                            </div>
                        </div>
                    </Col>
                    <Col v-if="tableFields.includes('estimate')" span="3">
                        <div class="ellipsis">{{$L('预估工时')}}</div>
                    </Col>
                </Row>
            </div>
            <!--我的任务-->
            <div v-if="visibleTableGroups.includes('my')" data-table-group="my" :class="['project-table-body', !projectData.cacheParameter.showMy ? 'project-table-hide' : '']">
                <Row class="task-row">
                    <Col :span="tableNameSpan" class="row-title">
                        <i class="taskfont" @click="toggleParameter('showMy')">&#xe689;</i>
                        <div class="row-h1">{{$L('我的任务')}}</div>
                        <div class="row-num">({{myList.length}})</div>
                    </Col>
                    <Col v-for="field in tableFields" :key="field" span="3"></Col>
                </Row>
                <TaskRow
                    v-if="projectData.cacheParameter.showMy"
                    :list="transforTasks(myList)"
                    :task-visibilitys="taskRowVisibilitys"
                    :readonly="isDepartmentReadonly"
                    :table-fields="tableFields"
                    open-key="my"
                    @on-priority="addTaskOpen"
                    fast-add-task/>
                <div v-if="projectData.cacheParameter.showMy && !myList.length && isDepartmentReadonly" class="project-table-empty">{{$L('暂无任务')}}</div>
            </div>
            <!--协助的任务-->
            <div v-if="visibleTableGroups.includes('help')" data-table-group="help" :class="['project-table-body', !projectData.cacheParameter.showHelp ? 'project-table-hide' : '']">
                <Row class="task-row">
                    <Col :span="tableNameSpan" class="row-title">
                        <i class="taskfont" @click="toggleParameter('showHelp')">&#xe689;</i>
                        <div class="row-h1">{{$L('协助的任务')}}</div>
                        <div class="row-num">({{helpList.length}})</div>
                    </Col>
                    <Col v-for="field in tableFields" :key="field" span="3"></Col>
                </Row>
                <TaskRow
                    v-if="projectData.cacheParameter.showHelp"
                    :list="helpList"
                    :task-visibilitys="taskRowVisibilitys"
                    :readonly="isDepartmentReadonly"
                    :table-fields="tableFields"
                    open-key="help"
                    @on-priority="addTaskOpen"/>
                <div v-if="projectData.cacheParameter.showHelp && !helpList.length" class="project-table-empty">{{$L('暂无任务')}}</div>
            </div>
            <!--未完成任务-->
            <div v-if="visibleTableGroups.includes('undone')" data-table-group="undone" :class="['project-table-body', !projectData.cacheParameter.showUndone ? 'project-table-hide' : '']">
                <Row class="task-row">
                    <Col :span="tableNameSpan" class="row-title">
                        <i class="taskfont" @click="toggleParameter('showUndone')">&#xe689;</i>
                        <div class="row-h1">{{$L('未完成任务')}}</div>
                        <div class="row-num">({{unList.length}})</div>
                    </Col>
                    <Col v-for="field in tableFields" :key="field" span="3"></Col>
                </Row>
                <TaskRow
                    v-if="projectData.cacheParameter.showUndone"
                    :list="unList"
                    :task-visibilitys="taskRowVisibilitys"
                    :readonly="isDepartmentReadonly"
                    :table-fields="tableFields"
                    open-key="undone"
                    @on-priority="addTaskOpen"/>
                <div v-if="projectData.cacheParameter.showUndone && !unList.length" class="project-table-empty">{{$L('暂无任务')}}</div>
            </div>
            <!--已完成任务-->
            <div v-if="visibleTableGroups.includes('completed')" data-table-group="completed" :class="['project-table-body', !projectData.cacheParameter.showCompleted ? 'project-table-hide' : '']">
                <Row class="task-row">
                    <Col :span="tableNameSpan" class="row-title">
                        <i class="taskfont" @click="toggleParameter('showCompleted')">&#xe689;</i>
                        <div class="row-h1">{{$L('已完成任务')}}</div>
                        <div class="row-num">({{completedList.length}})</div>
                    </Col>
                    <Col v-for="field in tableFields" :key="field" span="3">
                        <template v-if="field === 'due'">
                        <div class="ellipsis">{{projectData.cacheParameter.showCompleted ? $L('完成时间') : ''}}</div>
                        </template>
                    </Col>
                </Row>
                <TaskRow
                    v-if="projectData.cacheParameter.showCompleted"
                    :list="completedList"
                    :task-visibilitys="taskRowVisibilitys"
                    :readonly="isDepartmentReadonly"
                    :table-fields="tableFields"
                    open-key="completed"
                    @on-priority="addTaskOpen"
                    showCompleteAt/>
                <div v-if="projectData.cacheParameter.showCompleted && !completedList.length" class="project-table-empty">{{$L('暂无任务')}}</div>
            </div>
            <div
                v-for="group in additionalTableGroups"
                :key="group.key"
                :data-table-group="group.key"
                :class="['project-table-body', !projectData.cacheParameter[group.expandedKey] ? 'project-table-hide' : '']">
                <Row class="task-row">
                    <Col :span="tableNameSpan" class="row-title">
                        <i class="taskfont" @click="toggleParameter(group.expandedKey)">&#xe689;</i>
                        <div class="row-h1">{{$L(group.title)}}</div>
                        <div class="row-num">({{group.tasks.length}})</div>
                    </Col>
                    <Col v-for="field in tableFields" :key="field" span="3"></Col>
                </Row>
                <TaskRow
                    v-if="projectData.cacheParameter[group.expandedKey]"
                    :list="group.tasks"
                    :task-visibilitys="taskRowVisibilitys"
                    :readonly="isDepartmentReadonly"
                    :table-fields="tableFields"
                    :open-key="group.key"
                    @on-priority="addTaskOpen"/>
                <div v-if="projectData.cacheParameter[group.expandedKey] && !group.tasks.length" class="project-table-empty">{{$L('暂无任务')}}</div>
            </div>
        </Scrollbar>
        <div v-else-if="tabTypeActive === 'gantt'" class="project-gantt">
            <!--甘特图-->
            <ProjectGantt :projectId="projectId" :projectColumn="columnList" :flowFilter="flowFilter" :searchText="searchText" :readonly="isDepartmentReadonly" :can-manage-schedule="isOwnerOrDeputy && !isDepartmentReadonly" :dependency-enabled="projectData.task_dependency !== 'close'" :schedule-mode="projectData.task_schedule_mode || 'manual'" :project-timezone="projectScheduleTimezone"/>
        </div>

        <!--项目设置-->
        <ProjectSettingDrawer
            :visible.sync="settingShow"
            :project-id="projectId"
            :project="projectData" />

        <!--成员工时汇总-->
        <ProjectCapacityOverview
            :visible.sync="capacityShow"
            :project-id="Number(projectId)"
            :timezone="projectScheduleTimezone" />

        <MilestoneManager
            :visible.sync="milestoneShow"
            :project-id="projectId"
            :milestones="milestoneList"
            :can-manage="milestoneCanManage"
            :loading="milestoneLoading"
            :load-failed="milestoneLoadFailed"
            @refresh="loadMilestones"
            @open-task="openMilestoneTask" />

        <!--项目权限-->
        <DrawerOverlay
            v-model="permissionShow"
            placement="right"
            :size="800">
            <ProjectPermission ref="permission" v-if="permissionShow" @close="()=>{ this.permissionShow = false }" :project-id="projectId"/>
        </DrawerOverlay>

        <!--成员管理-->
        <Modal
            v-model="userShow"
            :title="$L('成员管理')"
            :mask-closable="false">
            <Form :model="userData" v-bind="formOptions" @submit.native.prevent>
                <FormItem v-if="canManageDeputy" prop="deputy_userids" :label="$L('项目管理员')">
                    <UserSelect
                        v-model="userData.deputy_userids"
                        :uncancelable="deputyRowUncancelable"
                        :disabledChoice="deputyRowDisabledChoice"
                        :multiple="true"
                        :multiple-max="20"
                        :title="$L('选择项目管理员')"/>
                </FormItem>
                <FormItem prop="userids" :label="$L('项目成员')">
                    <UserSelect
                        v-model="userData.userids"
                        :uncancelable="memberRowUncancelable"
                        :multiple-max="100"
                        :title="$L('选择项目成员')"/>
                </FormItem>
            </Form>
            <div slot="footer" class="adaption">
                <Button type="default" @click="userShow=false">{{$L('取消')}}</Button>
                <Poptip
                    v-if="userWaitRemove.length > 0 || deputyWaitDemote.length > 0"
                    confirm
                    placement="bottom"
                    style="margin-left:8px"
                    :ok-text="$L('确定')"
                    :cancel-text="$L('取消')"
                    @on-ok="onUser"
                    transfer>
                    <div slot="title">
                        <p><strong>{{$L('请确认以下操作，注意此操作不可逆！')}}</strong></p>
                        <template v-if="userWaitRemove.length > 0">
                            <p>{{$L('移除成员负责的任务将变成无负责人。')}}</p>
                            <ul class="project-panel-wait-remove">
                                <li>{{$L('即将移除')}}：</li>
                                <li v-for="id in userWaitRemove" :key="'r'+id">
                                    <UserAvatar :userid="id" :size="20" showName/>
                                </li>
                            </ul>
                        </template>
                        <ul v-if="deputyWaitDemote.length > 0" class="project-panel-wait-remove">
                            <li>{{$L('即将罢免项目管理员')}}：</li>
                            <li v-for="id in deputyWaitDemote" :key="'d'+id">
                                <UserAvatar :userid="id" :size="20" showName/>
                            </li>
                        </ul>
                    </div>
                    <Button type="primary" :loading="userLoad > 0">{{$L('保存')}}</Button>
                </Poptip>
                <Button v-else type="primary" :loading="userLoad > 0" @click="onUser">{{$L('保存')}}</Button>
            </div>
        </Modal>

        <!--邀请链接-->
        <Modal
            v-model="inviteShow"
            :title="$L('邀请链接')"
            :mask-closable="false">
            <Form :model="inviteData" v-bind="formOptions" @submit.native.prevent>
                <FormItem :label="$L('链接地址')">
                    <Input ref="inviteInput" v-model="inviteData.url" type="textarea" :rows="3" @on-focus="inviteFocus" readonly/>
                    <div class="form-tip">
                        {{$L('可通过此链接直接加入项目。')}}
                        <Poptip
                            confirm
                            placement="bottom"
                            :ok-text="$L('确定')"
                            :cancel-text="$L('取消')"
                            @on-ok="inviteGet(true)"
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
                <Button type="default" @click="inviteShow=false">{{$L('取消')}}</Button>
                <Button type="primary" :loading="inviteLoad > 0" @click="inviteCopy">{{$L('复制')}}</Button>
            </div>
        </Modal>

        <!--移交项目-->
        <Modal
            v-model="transferShow"
            :title="$L('移交项目')"
            :mask-closable="false">
            <Form :model="transferData" v-bind="formOptions" @submit.native.prevent>
                <FormItem prop="owner_userid" :label="$L('新项目负责人')">
                    <UserSelect v-model="transferData.owner_userid" :multiple-max="1" :title="$L('选择项目负责人')"/>
                </FormItem>
            </Form>
            <div slot="footer" class="adaption">
                <Button type="default" @click="transferShow=false">{{$L('取消')}}</Button>
                <Button type="primary" :loading="transferLoad > 0" @click="onTransfer">{{$L('移交')}}</Button>
            </div>
        </Modal>

        <!--任务模板-->
        <DrawerOverlay
            v-model="taskTemplateShow"
            placement="right"
            :size="720">
            <ProjectTaskTemplate ref="taskTemplate" v-if="taskTemplateShow" :project-id="projectId"/>
        </DrawerOverlay>

        <!--任务标签-->
        <DrawerOverlay
            v-model="taskTagShow"
            placement="right"
            :beforeClose="taskTagBeforeClose"
            :size="720">
            <ProjectTaskTag ref="taskTag" v-if="taskTagShow" :project-id="projectId"/>
        </DrawerOverlay>

        <!--工作流程设置-->
        <DrawerOverlay
            v-model="workflowShow"
            placement="right"
            :beforeClose="workflowBeforeClose"
            :size="1280">
            <ProjectWorkflow ref="workflow" v-if="workflowShow" :project-id="projectId"/>
        </DrawerOverlay>

        <!--查看项目动态-->
        <DrawerOverlay
            v-model="logShow"
            placement="right"
            :size="720">
            <ProjectLog v-if="logShow" :project-id="projectId"/>
        </DrawerOverlay>

        <!--查看归档任务-->
        <DrawerOverlay
            v-model="archivedTaskShow"
            placement="right"
            :size="1180">
            <TaskArchived v-if="archivedTaskShow" :project-id="projectId"/>
        </DrawerOverlay>

        <!--查看已删除任务-->
        <DrawerOverlay
            v-model="deletedTaskShow"
            placement="right"
            :size="1180">
            <TaskDeleted v-if="deletedTaskShow" :project-id="projectId"/>
        </DrawerOverlay>
    </div>
</template>

<script>
import {debounce} from "lodash";
import Draggable from 'vuedraggable'
import {mapGetters, mapState} from "vuex";
import TaskAddSimple from "./TaskAddSimple";
import TaskRow from "./TaskRow";
import TaskArchived from "./TaskArchived";
import ProjectLog from "./ProjectLog";
import DrawerOverlay from "../../../components/DrawerOverlay";
import ProjectTaskTag from "./ProjectTaskTag";
import ProjectTaskTemplate from "./ProjectTaskTemplate";
import ProjectWorkflow from "./ProjectWorkflow";
import ProjectPermission from "./ProjectPermission";
import TaskMenu from "./TaskMenu";
import TaskDeleted from "./TaskDeleted";
import TaskTag from "./ProjectTaskTag/tags.vue";
import ProjectGantt from "./ProjectGantt";
import MilestoneManager from "./MilestoneManager.vue";
import ProjectCapacityOverview from "./ProjectCapacityOverview.vue";
import ProjectSettingDrawer from "./ProjectSettingDrawer.vue";
import ProjectTableGroupDropdown from "./ProjectTableGroupDropdown.vue";
import ProjectFlowFilterDropdown from "./ProjectFlowFilterDropdown.vue";
import UserSelect from "../../../components/UserSelect.vue";
import UserAvatarTip from "../../../components/UserAvatar/tip.vue";
import VMPreviewNostyle from "../../../components/VMEditor/nostyle.vue";
import emitter from "../../../store/events";
import transformEmojiToHtml from "../../../utils/emoji";
import {projectTableGroups, normalizeProjectTableGroups, projectWeekRanges, taskMatchesProjectGroup} from "../../../utils/projectTableGroups";
import {scrollToStableTarget} from "../../../utils/scrollToStableTarget";
import {emptyProjectFlowFilter, projectFlowFilterCount, taskMatchesProjectFlowFilter} from "../../../utils/projectFlowFilter";

export default {
    name: "ProjectPanel",
    components: {
        VMPreviewNostyle,
        UserAvatarTip,
        UserSelect,
        TaskMenu,
        ProjectTaskTag,
        ProjectTaskTemplate,
        ProjectWorkflow,
        ProjectPermission,
        DrawerOverlay,
        ProjectLog,
        TaskArchived,
        TaskRow,
        Draggable,
        TaskAddSimple,
        TaskDeleted,
        TaskTag,
        ProjectGantt,
        MilestoneManager,
        ProjectCapacityOverview,
        ProjectSettingDrawer,
        ProjectTableGroupDropdown,
        ProjectFlowFilterDropdown,
    },
    data() {
        return {
            loading: false,

            nowTime: $A.dayjs().unix(),
            nowInterval: null,
            tableToday: $A.daytz().format('YYYY-MM-DD'),
            tableGroupsOpen: false,

            columnLoad: {},
            columnTopShow: {},

            sortField: 'end_at',
            sortType: 'desc',

            searchText: '',

            addColumnShow: false,
            addColumnLoad: false,
            addColumnName: '',

            sortData: [],
            sortDisabled: false,

            settingShow: false,

            milestoneShow: false,
            capacityShow: false,
            milestoneLoading: false,
            milestoneLoadFailed: false,
            milestoneCanManage: false,
            milestoneList: [],
            milestoneFilterId: null,
            taskDateRange: [],
            tableFields: ['column', 'priority', 'owner', 'due'],
            taskViews: [],
            taskViewsLoading: false,
            taskViewsLoadFailed: false,
            selectedTaskViewId: '',

            permissionShow: false,
            permissionShowData: {},
            permissionShowLoad: 0,

            userShow: false,
            userData: {},
            userLoad: 0,

            inviteShow: false,
            inviteData: {},
            inviteLoad: 0,

            transferShow: false,
            transferData: {},
            transferLoad: 0,

            taskTemplateShow: false,
            taskTagShow: false,

            workflowShow: false,
            logShow: false,
            archivedTaskShow: false,
            deletedTaskShow: false,

            flowFilter: emptyProjectFlowFilter(),
            flowFilterOpen: false,
            flowList: [],

            columnVisibilitys: {},
            taskVisibilitys: {},
            taskRowVisibilitys: {},
        }
    },

    mounted() {
        emitter.on('projectMilestonesUpdate', this.onProjectMilestonesUpdate);
        this.nowInterval = setInterval(() => {
            this.nowTime = $A.dayjs().unix();
            this.tableToday = $A.daytz().format('YYYY-MM-DD');
        }, 1000);
    },

    destroyed() {
        emitter.off('projectMilestonesUpdate', this.onProjectMilestonesUpdate);
        this.cancelTableGroupScroll();
        clearInterval(this.nowInterval);
    },

    computed: {
        ...mapState([
            'cacheDialogs',

            'projectId',
            'projectLoad',
            'cacheTasks',
            'cacheColumns',

            'taskCompleteTemps',

            'cacheUserBasic',

            'formOptions',

            'systemConfig',
        ]),

        ...mapGetters(['aiVisible', 'projectData', 'transforTasks']),

        projectScheduleTimezone() {
            return this.projectData.task_schedule_timezone || $A.timezoneName || Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
        },

        tabTypeActive() {
            return this.projectData.cacheParameter.menuType
        },

        tableGroupOptions() {
            return projectTableGroups;
        },

        taskTableFieldOptions() {
            return [
                {key: 'column', label: '列表'},
                {key: 'priority', label: '优先级'},
                {key: 'owner', label: '负责人'},
                {key: 'due', label: '到期时间'},
                {key: 'estimate', label: '预估工时'},
            ];
        },

        tableNameSpan() {
            return 24 - this.tableFields.length * 3;
        },

        visibleTableGroups() {
            return normalizeProjectTableGroups(this.projectData.cacheParameter.tableGroups);
        },

        selectedTaskView() {
            return this.taskViews.find(item => Number(item.id) === Number(this.selectedTaskViewId)) || null;
        },

        tableWeekRanges() {
            return projectWeekRanges($A.dayjs(this.tableToday));
        },

        additionalTableGroups() {
            const groups = this.tableGroupOptions.filter(group => group.kind && this.visibleTableGroups.includes(group.key));
            if (!groups.length) return [];
            const tasks = this.panelTask(this.parentTask);
            const now = groups.some(group => group.kind === 'overdue') ? $A.daytz(this.nowTime).valueOf() : 0;
            return groups.map(group => ({
                ...group,
                tasks: tasks.filter(task => taskMatchesProjectGroup(task, group, this.tableWeekRanges, now, $A.dayjs)).sort((a, b) => {
                    if (this.sortType === 'asc') [a, b] = [b, a];
                    if (this.sortField === 'level' || a.end_at === b.end_at) {
                        return $A.sortFloat(a.p_level, b.p_level);
                    }
                    return $A.sortDay(a.end_at || '2099-12-31 23:59:59', b.end_at || '2099-12-31 23:59:59');
                }),
            }));
        },

        // 各分组任务数，与分组标题旁的数量一致（未显示的分组也计算）
        tableGroupCounts() {
            const counts = {
                my: this.myList.length,
                help: this.helpList.length,
                undone: this.unList.length,
                completed: this.completedList.length,
            };
            const groups = this.tableGroupOptions.filter(group => group.kind);
            const tasks = this.panelTask(this.parentTask);
            const now = $A.daytz(this.nowTime).valueOf();
            groups.forEach(group => {
                counts[group.key] = tasks.filter(task => taskMatchesProjectGroup(task, group, this.tableWeekRanges, now, $A.dayjs)).length;
            });
            return counts;
        },

        tabTypeStyle() {
            const style = {}
            switch (this.tabTypeActive) {
                case 'column':
                    style.left = '0'
                    break
                case 'table':
                    style.left = '33.33%'
                    break
                case 'gantt':
                    style.left = '66.66%'
                    break
                default:
                    style.display = 'none'
            }
            return style
        },

        userWaitRemove() {
            const {userids, useridbak} = this.userData;
            if (!userids) {
                return [];
            }
            let wait = [];
            useridbak.some(id => {
                if (!userids.includes(id)) {
                    wait.push(id)
                }
            })
            return wait;
        },

        deputyWaitDemote() {
            // 所有从项目管理员列表中移出的人（即使同时被踢出项目，也在罢免段显示，避免操作隐身）
            const {deputy_userids = [], deputy_useridbak = []} = this.userData;
            return deputy_useridbak.filter(id => !deputy_userids.includes(id));
        },

        msgUnread() {
            const {cacheDialogs, projectData} = this;
            const dialog = cacheDialogs.find(({id}) => id === projectData.dialog_id);
            return $A.getDialogNum(dialog);
        },

        panelTask() {
            const {searchText, flowFilter} = this;
            return function (list) {
                if (!this.projectData.cacheParameter.completedTask) {
                    list = list.filter(({complete_at}) => {
                        return !complete_at;
                    });
                }
                list = list.filter(task => taskMatchesProjectFlowFilter(task, flowFilter));
                if (searchText) {
                    list = list.filter(({id, name, desc}) => {
                        return id == searchText || $A.strExists(`${name} ${desc}`, searchText)
                    });
                }
                return list;
            }
        },

        canManageDeputy() {
            return this.projectData?.owner_userid === this.userId;
        },

        isDepartmentReadonly() {
            return !!this.projectData?.department_readonly;
        },

        isOwnerOrDeputy() {
            if (this.isDepartmentReadonly) return false;
            if (!this.projectData) return false;
            if (this.projectData.owner_userid === this.userId) return true;
            return (this.projectData.deputy_userids || []).includes(this.userId);
        },

        memberRowUncancelable() {
            // 项目成员行：负责人 + 当前项目管理员选择（响应式）都不可移除
            if (!this.projectData) return [];
            const deputies = (this.userData && Array.isArray(this.userData.deputy_userids))
                ? this.userData.deputy_userids
                : (this.projectData.deputy_userids || []);
            return [
                this.projectData.owner_userid,
                ...deputies,
            ];
        },

        deputyRowUncancelable() {
            // 项目管理员行：防御性锁定负责人（理论上负责人不会出现在该 v-model 里）
            if (!this.projectData) return [];
            return [this.projectData.owner_userid];
        },

        deputyRowDisabledChoice() {
            // 项目管理员候选：排除负责人（不能任命负责人为项目管理员）
            if (!this.projectData) return [];
            return [this.projectData.owner_userid];
        },

        projectMemberUserids() {
            return (this.projectData.project_user || []).map(({userid}) => userid);
        },

        projectUser() {
            const {projectData, windowWidth} = this;
            if (!projectData.project_user) {
                return [];
            }
            const max = windowWidth > 1200 ? 8 : 3;
            const deputyIds = projectData.deputy_userids || [];
            const list = projectData.project_user
                .filter(({userid}) => userid != projectData.owner_userid)
                .slice()
                .sort((a, b) => {
                    const aD = deputyIds.includes(a.userid) ? 0 : 1;
                    const bD = deputyIds.includes(b.userid) ? 0 : 1;
                    return aD - bD;
                });
            if (list.length <= max) {
                return list;
            }
            const array = list.slice(0, max - 1);
            array.push({userid: -1});
            array.push(list[list.length - 1]);
            return array;
        },

        allTask() {
            const {cacheTasks, projectId, milestoneFilterId, taskDateRange} = this;
            return cacheTasks.filter(task => {
                if (task.archived_at || !task.created_at) {
                    return false;
                }
                if (task.project_id != projectId) return false;
                if (Number(milestoneFilterId) && Number(task.milestone_id) !== Number(milestoneFilterId)) return false;
                if (Array.isArray(taskDateRange) && taskDateRange.length === 2 && taskDateRange[0] && taskDateRange[1]) {
                    const startAt = task.start_at || task.end_at;
                    const endAt = task.end_at || task.start_at;
                    if (!startAt || !endAt) return false;
                    if ($A.dayjs(endAt).isBefore($A.dayjs(taskDateRange[0]).startOf('day')) ||
                        $A.dayjs(startAt).isAfter($A.dayjs(taskDateRange[1]).endOf('day'))) return false;
                }
                return true;
            })
        },

        parentTask() {
            const {allTask} = this;
            return allTask.filter(task => task.parent_id == 0);
        },

        columnList() {
            const {projectId, cacheColumns, allTask} = this;
            const list = $A.cloneJSON(cacheColumns).filter(({project_id}) => {
                return project_id == projectId
            }).sort((a, b) => {
                if (a.sort != b.sort) {
                    return $A.sortFloat(a.sort, b.sort);
                }
                return a.id - b.id;
            });
            list.forEach((column) => {
                column.tasks = this.transforTasks(allTask.filter(task => {
                    return task.column_id == column.id;
                })).sort((a, b) => {
                    if (a.complete_at || b.complete_at) {
                        return $A.sortDay(a.complete_at, b.complete_at);
                    }
                    if (a.sort != b.sort) {
                        return $A.sortFloat(a.sort, b.sort);
                    }
                    return a.id - b.id;
                });
            })
            return list;
        },

        myList() {
            const {allTask, taskCompleteTemps, sortField, sortType} = this;
            let array = allTask.filter(task => this.myFilter(task));
            if (taskCompleteTemps.length > 0) {
                let tmps = allTask.filter(task => taskCompleteTemps.includes(task.id) && this.myFilter(task, false));
                if (tmps.length > 0) {
                    array = $A.cloneJSON(array)
                    array.push(...tmps);
                }
            }
            return array.sort((a, b) => {
                if (sortType == 'asc') {
                    [a, b] = [b, a];
                }
                if (sortField == 'level') {
                    return $A.sortFloat(a.p_level, b.p_level)
                } else if (sortField == 'end_at') {
                    if (a.end_at == b.end_at) {
                        return $A.sortFloat(a.p_level, b.p_level)
                    }
                    return $A.sortDay(a.end_at || "2099-12-31 23:59:59", b.end_at || "2099-12-31 23:59:59");
                }
            });
        },

        helpList() {
            const {allTask, taskCompleteTemps, sortField, sortType} = this;
            let array = allTask.filter(task => this.helpFilter(task));
            if (taskCompleteTemps.length > 0) {
                let tmps = allTask.filter(task => taskCompleteTemps.includes(task.id) && this.helpFilter(task, false));
                if (tmps.length > 0) {
                    array = $A.cloneJSON(array)
                    array.push(...tmps);
                }
            }
            return array.sort((a, b) => {
                if (sortType == 'asc') {
                    [a, b] = [b, a];
                }
                if (sortField == 'level') {
                    return $A.sortFloat(a.p_level, b.p_level)
                } else if (sortField == 'end_at') {
                    if (a.end_at == b.end_at) {
                        return $A.sortFloat(a.p_level, b.p_level)
                    }
                    return $A.sortDay(a.end_at || "2099-12-31 23:59:59", b.end_at || "2099-12-31 23:59:59");
                }
            });
        },

        unList() {
            const {allTask, searchText, sortField, sortType} = this;
            const array = allTask.filter(task => {
                if (task.parent_id > 0) {
                    return false;
                }
                if (this.flowTask(task)) {
                    return false;
                }
                if (searchText) {
                    if (task.id != searchText && !$A.strExists(task.name, searchText) && !$A.strExists(task.desc, searchText)) {
                        return false;
                    }
                }
                return !task.complete_at;
            });
            return array.sort((a, b) => {
                if (sortType == 'asc') {
                    [a, b] = [b, a];
                }
                if (sortField == 'level') {
                    return $A.sortFloat(a.p_level, b.p_level)
                } else if (sortField == 'end_at') {
                    if (a.end_at == b.end_at) {
                        return $A.sortFloat(a.p_level, b.p_level)
                    }
                    return $A.sortDay(a.end_at || "2099-12-31 23:59:59", b.end_at || "2099-12-31 23:59:59");
                }
            });
        },

        completedList() {
            const {allTask, searchText} = this;
            const array = allTask.filter(task => {
                if (task.parent_id > 0) {
                    return false;
                }
                if (this.flowTask(task)) {
                    return false;
                }
                if (searchText) {
                    if (task.id != searchText && !$A.strExists(task.name, searchText) && !$A.strExists(task.desc, searchText)) {
                        return false;
                    }
                }
                return task.complete_at;
            });
            return array.sort((a, b) => {
                return $A.sortDay(b.complete_at, a.complete_at);
            });
        },

        completedCount() {
            const {allTask} = this;
            return allTask.filter(task => {
                if (task.parent_id > 0) {
                    return false;
                }
                return task.complete_at;
            }).length;
        },

        tagList() {
            const tags = [];
            this.allTask.forEach(({task_tag}) => {
                task_tag.forEach(tag => {
                    if (!tags.find(item => item.name === tag.name)) {
                        tags.push(tag);
                    }
                });
            });
            return tags;
        },

        flowFilterTotal() {
            return projectFlowFilterCount(this.flowFilter);
        },

        flowFilterGroups() {
            const {flowList, allTask, cacheUserBasic, flowFilter} = this;
            const groups = [];
            // 状态（含未计划）
            const flowSections = [{
                items: [{
                    value: -1,
                    label: this.$L('未计划'),
                    count: allTask.filter(({start_at, complete_at}) => !start_at && !complete_at).length,
                }]
            }];
            flowList.forEach(flow => {
                flowSections.push({
                    title: flowList.length > 1 ? flow.name : '',
                    items: flow.project_flow_item.map(item => ({
                        value: item.id,
                        label: item.name,
                        count: allTask.filter(({flow_item_id}) => flow_item_id == item.id).length,
                        status: item.status,
                        style: $A.generateColorVarStyle(item.color, [10], 'flow-item-custom-color'),
                    }))
                });
            });
            groups.push({key: 'flows', title: '状态', sections: flowSections});
            // 标签
            if (this.tagList.length > 0) {
                groups.push({
                    key: 'tags',
                    title: '标签',
                    sections: [{
                        items: this.tagList.map(({name, color}) => ({
                            value: name,
                            label: name,
                            count: allTask.filter(({task_tag}) => task_tag.find(tag => tag.name === name)).length,
                            color,
                        }))
                    }]
                });
            }
            // 负责人
            const {project_user} = this.projectData;
            if ($A.isArray(project_user)) {
                const userItems = project_user.map(item => {
                    const userInfo = cacheUserBasic.find(({userid}) => userid === item.userid) || {}
                    const count = allTask.filter(({task_user, complete_at}) => {
                        if (!this.projectData.cacheParameter.completedTask && complete_at) {
                            return false;
                        }
                        return task_user.find(({userid, owner}) => userid === item.userid && owner);
                    }).length
                    return {
                        value: userInfo.userid || 0,
                        label: userInfo.nickname,
                        count,
                        userid: userInfo.userid || 0,
                    }
                }).filter(({userid, count}) => userid > 0 && (count > 0 || flowFilter.users.includes(userid)))
                if (userItems.length > 0) {
                    userItems.sort((a, b) => (b.userid == this.userId) - (a.userid == this.userId))
                    // 负责人超过 10 人时显示搜索框
                    groups.push({key: 'users', title: '负责人', searchable: userItems.length > 10, sections: [{items: userItems}]});
                }
            }
            return groups;
        },

        flowTrigger() {
            const {flowFilter, flowFilterTotal, flowFilterGroups, allTask} = this;
            if (flowFilterTotal === 0) {
                return {title: `${this.$L('全部')} (${allTask.length})`};
            }
            if (flowFilterTotal === 1) {
                const group = flowFilterGroups.find(({key}) => flowFilter[key].length > 0);
                const item = group?.sections.flatMap(({items}) => items).find(({value}) => value === flowFilter[group.key][0]);
                if (item) {
                    const title = `${item.label} (${item.count})`;
                    if (item.status) {
                        return {title, class: item.status, style: item.style};
                    }
                    if (item.color) {
                        return {title, class: 'active tag-dot', style: {'--bg-color': item.color}};
                    }
                    return {title, class: 'active'};
                }
            }
            const parts = flowFilterGroups
                .filter(({key}) => flowFilter[key].length > 0)
                .map(({key, title}) => `${this.$L(title)}·${flowFilter[key].length}`);
            const count = allTask.filter(task => taskMatchesProjectFlowFilter(task, flowFilter)).length;
            return {title: `${parts.join(' / ')} (${count})`, class: 'active'};
        },

        milestoneOverview() {
            const openMilestones = this.milestoneList.filter(item => item.status === 'open');
            const today = $A.dayjs().format('YYYY-MM-DD');
            const next = openMilestones.slice().sort((a, b) => {
                const aDate = a.target_at || '9999-12-31';
                const bDate = b.target_at || '9999-12-31';
                return aDate.localeCompare(bDate) || Number(a.id) - Number(b.id);
            }).find(item => item.target_at >= today) || openMilestones.find(item => item.target_at) || openMilestones[0] || null;
            const taskCount = this.milestoneList.reduce((total, item) => total + Number(item.task_count || 0), 0);
            const completedCount = this.milestoneList.reduce((total, item) => total + Number(item.completed_count || 0), 0);
            return {
                next,
                progress: taskCount > 0 ? Math.floor(completedCount / taskCount * 100) : 0,
                overdue: this.milestoneList.reduce((total, item) => total + Number(item.overdue_count || 0), 0),
            };
        },
    },

    watch: {
        tabTypeActive(type) {
            if (type !== 'table') {
                this.tableGroupsOpen = false;
                this.cancelTableGroupScroll();
            }
        },
        additionalTableGroups() {
            this.handleColumnDebounce();
        },
        visibleTableGroups() {
            if (this._tableGroupScroll && !this.visibleTableGroups.includes(this._tableGroupScroll.key)) this.cancelTableGroupScroll();
            this.handleColumnDebounce();
        },
        projectId: {
            handler(id) {
                this.capacityShow = false;
                this.cancelTableGroupScroll();
                this.tableGroupsOpen = false;
                this._taskViewRequestKey = (this._taskViewRequestKey || 0) + 1;
                this.taskViews = [];
                this.taskViewsLoading = false;
                this.taskViewsLoadFailed = false;
                this.selectedTaskViewId = '';
                this.flowFilter = emptyProjectFlowFilter();
                this.searchText = '';
                this.milestoneFilterId = null;
                this.taskDateRange = [];
                this.tableFields = ['column', 'priority', 'owner', 'due'];
                this.milestoneList = [];
                this.milestoneCanManage = false;
                this.milestoneLoadFailed = false;
                this.milestoneShow = false;
                if (id > 0) {
                    this.getFlowData();
                    this.loadMilestones().then(() => {
                        if (id === this.projectId) this.loadTaskViews();
                    });
                    this.handleColumnDebounce();
                } else {
                    this.taskViews = [];
                    this.selectedTaskViewId = '';
                }
            },
            immediate: true,
        },
        'columnList.length'() {
            this.handleColumnDebounce();
        },
        'parentTask.length'() {
            this.handleColumnDebounce();
        },
        'projectData.task_num'() {
            this.handleColumnDebounce();
        },
        searchText() {
            this.handleColumnDebounce(true);
        },
        taskDateRange() {
            this.handleColumnDebounce(true);
        },
        windowWidth() {
            this.handleColumnDebounce(100);
        },
        'userData.deputy_userids'(newDeputies) {
            // 项目管理员必须是项目成员：项目管理员行新增时自动并入成员行（罢免时不联动移除）
            if (!Array.isArray(newDeputies) || !Array.isArray(this.userData.userids)) {
                return;
            }
            const toAdd = newDeputies.filter(id => !this.userData.userids.includes(id));
            if (toAdd.length > 0) {
                this.userData.userids = [...this.userData.userids, ...toAdd];
            }
        },
        projectData(newData, oldData) {
            this.sortData = this.getSort();
            if (newData && newData.id && (!oldData || newData.id !== oldData.id)) {
                this.checkProjectFavoriteStatus();
            }
        },
        projectLoad(n) {
            this._loadTimeout && clearTimeout(this._loadTimeout)
            if (n > 0) {
                this._loadTimeout = setTimeout(() => {
                    this.loading = true;
                }, 1000)
            } else {
                this.loading = false;
            }
        },
    },

    methods: {
        transformEmojiToHtml,
        showName() {
            if (this.windowLandscape) {
                return;
            }
            $A.modalInfo({
                language: false,
                title: this.$L('项目名称'),
                content: this.projectData.name
            })
        },

        showDesc() {
            if (this.windowLandscape) {
                return;
            }
            $A.modalInfo({
                language: false,
                title: this.$L('项目描述'),
                content: this.$refs.descPreview.$el.innerHTML
            })
        },

        searchFocus() {
            this.$nextTick(() => {
                this.$refs.searchInput.focus({
                    cursor: "end"
                });
            })
        },

        getSort() {
            const sortData = [];
            this.columnList.forEach((column) => {
                sortData.push({
                    id: column.id,
                    task: column.tasks.map(({id}) => id)
                });
            })
            return sortData;
        },

        sortUpdate(only_column) {
            if (this.isDepartmentReadonly) {
                return;
            }
            const oldSort = this.sortData;
            const newSort = this.getSort();
            if (JSON.stringify(oldSort) === JSON.stringify(newSort)) {
                return;
            }
            this.sortData = newSort;
            //
            const data = {
                project_id: this.projectId,
                sort: this.sortData,
                only_column: only_column === true ? 1 : 0
            };
            this.sortDisabled = true;
            this.$store.dispatch("call", {
                url: 'project/sort',
                data,
                method: 'post',
            }).then(({msg}) => {
                $A.messageSuccess(msg);
                this.sortDisabled = false;
                //
                let sort,
                    upData = [];
                if (data.only_column) {
                    sort = -1;
                    data.sort.forEach((item) => {
                        sort++;
                        upData.push({
                            id: item.id,
                            sort,
                        })
                    })
                    this.$store.dispatch("saveColumn", upData)
                } else {
                    data.sort.forEach((item) => {
                        sort = -1;
                        upData.push(...item.task.map(id => {
                            sort++;
                            upData.push(...this.allTask.filter(task => {
                                return task.parent_id == id
                            }).map(({id}) => {
                                return {
                                    id,
                                    sort,
                                    column_id: item.id,
                                }
                            }))
                            return {
                                id,
                                sort,
                                column_id: item.id,
                            }
                        }))
                    })
                    this.$store.dispatch("saveTask", upData)
                }
            }).catch(({msg}) => {
                $A.modalError(msg);
                this.sortDisabled = false;
                this.$store.dispatch("getTaskForProject", this.projectId).catch(() => {})
            }).finally(_ => {
                this.handleColumnDebounce();
            });
        },

        addTopShow(id, show) {
            if (this.isDepartmentReadonly) {
                return;
            }
            this.$set(this.columnTopShow, id, show);
        },

        addTaskOpen(params) {
            if (this.isDepartmentReadonly) {
                return;
            }
            emitter.emit('addTask', params);
        },

        addColumnOpen() {
            if (this.isDepartmentReadonly) {
                return;
            }
            this.addColumnShow = true;
            this.$nextTick(() => {
                this.$refs.addColumnName.focus();
            })
        },

        addColumnBlur() {
            if (this.addColumnName === '') {
                this.addColumnShow = false
            }
        },

        onAddColumnKeydown(e) {
            if (e.isComposing || e.key === 'Process' || e.keyCode === 229) return;
            if (e.keyCode === 13) {
                this.addColumnSubmit();
            }
        },

        addColumnSubmit() {
            let name = this.addColumnName.trim();
            if (name === '' || this.addColumnLoad) {
                return;
            }
            this.addColumnLoad = true
            this.$store.dispatch("call", {
                url: 'project/column/add',
                data: {
                    project_id: this.projectId,
                    name: name,
                },
                spinner: 600,
            }).then(({data, msg}) => {
                $A.messageSuccess(msg);
                this.addColumnName = '';
                this.$store.dispatch("saveColumn", data);
            }).catch(({msg}) => {
                $A.modalError(msg);
            }).finally(() => {
                this.addColumnLoad = false;
            });
        },

        dropColumn(column, command) {
            if (this.isDepartmentReadonly) {
                return;
            }
            if (command === 'title') {
                this.titleColumn(column);
            }
            else if (command === 'archive_completed') {
                this.archiveColumnCompletedTasks(column);
            }
            else if (command === 'remove') {
                this.removeColumn(column);
            }
            else if (command.name) {
                this.updateColumn(column, {
                    color: command.color
                }).catch(e => {
                    $A.modalError(e)
                });
            }
        },

        titleColumn(column) {
            $A.modalInput({
                value: column.name,
                title: "修改列表",
                placeholder: "输入列表名称",
                onOk: (value) => {
                    if (!value) {
                        return '列表名称不能为空'
                    }
                    return this.updateColumn(column, {
                        name: value
                    })
                }
            });
        },

        updateColumn(column, updata) {
            return new Promise((resolve, reject) => {
                if (this.columnLoad[column.id] === true) {
                    resolve()
                    return;
                }
                this.$set(this.columnLoad, column.id, true);
                //
                Object.keys(updata).forEach(key => this.$set(column, key, updata[key]));
                //
                this.$store.dispatch("call", {
                    url: 'project/column/update',
                    data: Object.assign(updata, {
                        column_id: column.id,
                    }),
                }).then(({data}) => {
                    this.$set(this.columnLoad, column.id, false);
                    this.$store.dispatch("saveColumn", data);
                    resolve()
                }).catch(({msg}) => {
                    this.$set(this.columnLoad, column.id, false);
                    this.$store.dispatch("getColumns", this.projectId).catch(() => {})
                    reject(msg);
                });
            })
        },

        archiveColumnCompletedTasks(column) {
            const tasks = this.getColumnCompletedTasks(column);
            if (tasks.length === 0) {
                $A.messageWarning('当前列表没有可归档的已完成任务');
                return;
            }
            $A.modalConfirm({
                title: '归档已完成任务',
                content: `你确定将列表【${column.name}】中所有已完成的任务归档吗？`,
                loading: true,
                onOk: () => {
                    return new Promise((resolve, reject) => {
                        this.batchArchiveTasks(tasks).then(() => {
                            $A.messageSuccess('已归档列表中所有已完成任务');
                            resolve();
                        }).catch(({msg}) => {
                            $A.modalError(msg || '归档失败，请稍后再试');
                            reject();
                        });
                    })
                }
            });
        },

        getColumnCompletedTasks(column) {
            if (!column || !Array.isArray(column.tasks)) {
                return [];
            }
            return column.tasks.filter(task => task && task.complete_at && !task.archived_at);
        },

        batchArchiveTasks(tasks) {
            const archive = (index = 0) => {
                if (index >= tasks.length) {
                    return Promise.resolve();
                }
                const task = tasks[index];
                return this.$store.dispatch("archivedTask", {task_id: task.id}).then(() => {
                    this.$store.dispatch("saveTaskBrowse", task.id);
                    return archive(index + 1);
                });
            }
            return archive();
        },

        removeColumn(column) {
            $A.modalConfirm({
                title: '删除列表',
                content: '你确定要删除列表【' + column.name + '】及列表内的任务吗？',
                loading: true,
                onOk: () => {
                    if (this.columnLoad[column.id] === true) {
                        return;
                    }
                    this.$set(this.columnLoad, column.id, true);
                    //
                    return new Promise((resolve, reject) => {
                        this.$store.dispatch("removeColumn", column.id).then(({msg}) => {
                            resolve(msg);
                        }).catch(({msg}) => {
                            reject(msg);
                        }).finally(_ => {
                            this.$set(this.columnLoad, column.id, false);
                        });
                    })
                }
            });
        },

        onSort(field) {
            this.sortField = field;
            this.sortType = this.sortType == 'desc' ? 'asc' : 'desc';
            this.handleColumnDebounce();
        },

        async loadMilestones() {
            const requestKey = this._milestoneRequestKey = (this._milestoneRequestKey || 0) + 1;
            const projectId = this.projectId;
            this.milestoneLoading = true;
            this.milestoneLoadFailed = false;
            try {
                const {data} = await this.$store.dispatch("call", {
                    url: 'projectmilestone/lists',
                    data: {project_id: projectId},
                });
                if (requestKey !== this._milestoneRequestKey || projectId !== this.projectId) return;
                this.milestoneList = data.milestones || [];
                this.milestoneCanManage = !!data.can_manage;
                if (!this.milestoneList.some(item => Number(item.id) === Number(this.milestoneFilterId))) {
                    const missingMilestone = Number(this.milestoneFilterId) || 0;
                    this.milestoneFilterId = null;
                    if (missingMilestone && this.selectedTaskViewId) {
                        $A.messageWarning(this.$L('视图中的里程碑已不可用，已清除该筛选'));
                    }
                }
            } catch (e) {
                if (requestKey === this._milestoneRequestKey) this.milestoneLoadFailed = true;
            } finally {
                if (requestKey === this._milestoneRequestKey) this.milestoneLoading = false;
            }
        },

        onProjectMilestonesUpdate(projectId) {
            if (Number(projectId) !== Number(this.projectId)) return;
            this.loadMilestones();
        },

        openMilestoneOverview() {
            this.milestoneShow = true;
            this.loadMilestones();
        },

        openCapacityOverview() {
            this.capacityShow = true;
        },

        openMilestoneTask(task) {
            const cachedTask = this.cacheTasks.find(item => Number(item.id) === Number(task.id));
            this.openTask(cachedTask || {id: Number(task.id), project_id: this.projectId});
        },

        onUser() {
            this.userLoad++;
            // 项目管理员必须是项目成员：把 deputy 并入 userid 列表（前端归一化）
            const baseUserids = (this.userData.userids || []).slice();
            const deputyUserids = (this.userData.deputy_userids || []).slice();
            const mergedUserids = Array.from(new Set([...baseUserids, ...deputyUserids]));
            //
            const payload = {
                project_id: this.projectId,
                userid: mergedUserids,
            };
            // 仅项目负责人发送 deputy_userid；项目管理员/其他角色不发送（后端也会忽略）
            if (this.canManageDeputy) {
                payload.deputy_userid = deputyUserids;
            }
            //
            this.$store.dispatch("call", {
                url: 'project/user',
                data: payload,
            }).then(({msg}) => {
                $A.messageSuccess(msg);
                this.userShow = false;
                this.$store.dispatch("getProjectOne", this.projectId).catch(() => {});
                this.$store.dispatch("getTaskForProject", this.projectId).catch(() => {})
            }).catch(({msg}) => {
                $A.modalError(msg);
            }).finally(_ => {
                this.userLoad--;
            });
        },

        onTransfer() {
            this.transferLoad++;
            this.$store.dispatch("call", {
                url: 'project/transfer',
                data: {
                    project_id: this.projectId,
                    owner_userid: this.transferData.owner_userid[0],
                },
            }).then(({msg}) => {
                $A.messageSuccess(msg);
                this.transferShow = false;
                this.$store.dispatch("getProjectOne", this.projectId).catch(() => {});
                this.$store.dispatch("getTaskForProject", this.projectId).catch(() => {})
            }).catch(({msg}) => {
                $A.modalError(msg);
            }).finally(_ => {
                this.transferLoad--;
            });
        },

        onArchived() {
            $A.modalConfirm({
                title: '归档项目',
                content: '你确定要归档项目【' + this.projectData.name + '】吗？',
                loading: true,
                onOk: () => {
                    return new Promise((resolve, reject) => {
                        this.$store.dispatch("archivedProject", this.projectId).then(({msg}) => {
                            resolve(msg);
                        }).catch(({msg}) => {
                            reject(msg);
                        });
                    })
                }
            });
        },

        onDelete() {
            $A.modalConfirm({
                title: '删除项目',
                content: '你确定要删除项目【' + this.projectData.name + '】吗？',
                loading: true,
                onOk: () => {
                    return new Promise((resolve, reject) => {
                        this.$store.dispatch("removeProject", this.projectId).then(({msg}) => {
                            resolve(msg);
                        }).catch(({msg}) => {
                            reject(msg);
                        });
                    })
                }
            });
        },

        onExit() {
            $A.modalConfirm({
                title: '退出项目',
                content: '你确定要退出项目【' + this.projectData.name + '】吗？',
                loading: true,
                onOk: () => {
                    return new Promise((resolve, reject) => {
                        this.$store.dispatch("exitProject", this.projectId).then(({msg}) => {
                            resolve(msg);
                        }).catch(({msg}) => {
                            reject(msg);
                        });
                    })
                }
            });
        },

        projectDropdown(name) {
            if (this.isDepartmentReadonly && !['favorite', 'milestones', 'log', 'archived_task', 'deleted_task'].includes(name)) {
                return;
            }
            switch (name) {
                case "favorite":
                    this.toggleProjectFavorite();
                    break;

                case "milestones":
                    this.milestoneShow = true;
                    this.loadMilestones();
                    break;

                case "setting":
                    this.settingShow = true;
                    break;

                case "permissions":
                    this.permissionShow = true;
                    break;

                case "user":
                    if (this.isDepartmentReadonly || !this.isOwnerOrDeputy) {
                        return;
                    }
                    const userids = this.projectData.project_user.map(({userid}) => userid);
                    const deputyUserids = [...(this.projectData.deputy_userids || [])];
                    this.$set(this.userData, 'userids', userids);
                    this.$set(this.userData, 'useridbak', userids);
                    this.$set(this.userData, 'deputy_userids', deputyUserids);
                    this.$set(this.userData, 'deputy_useridbak', deputyUserids);
                    this.userShow = true;
                    break;

                case "invite":
                    this.inviteData = {};
                    this.inviteShow = true;
                    this.inviteGet()
                    break;

                case "task_template":
                    this.taskTemplateShow = true;
                    break;

                case "task_tag":
                    this.taskTagShow = true;
                    break;

                case "workflow":
                    this.workflowShow = true;
                    break;

                case "log":
                    this.logShow = true;
                    break;

                case "archived_task":
                    this.archivedTaskShow = true;
                    break;

                case "deleted_task":
                    this.deletedTaskShow = true;
                    break;

                case "transfer":
                    this.$set(this.transferData, 'owner_userid', []);
                    this.transferShow = true;
                    break;

                case "archived":
                    this.onArchived();
                    break;

                case "delete":
                    this.onDelete();
                    break;

                case "exit":
                    this.onExit();
                    break;
            }
        },

        isPrimaryOwnerUid(userid) {
            return userid === this.projectData.owner_userid;
        },

        isDeputyUid(userid) {
            return (this.projectData.deputy_userids || []).includes(userid);
        },

        openTask(task, receive) {
            this.$store.dispatch("openTask", task)
            if (this.isDepartmentReadonly) {
                return;
            }
            if (receive === true) {
                // 向任务窗口发送领取任务请求
                setTimeout(() => {
                    emitter.emit('receiveTask', true);
                }, 300)
            }
        },

        openMenu(event, task) {
            const el = this.$refs[`taskMenu_${task.id}`];
            if (el) {
                el[0].handleClick(event)
            }
        },

        taskIsHidden(task) {
            const {id, name, desc, complete_at} = task;
            const {searchText} = this;
            if (!this.projectData.cacheParameter.completedTask) {
                if (complete_at) {
                    return true;
                }
            }
            if (this.flowTask(task)) {
                return true;
            }
            if (searchText) {
                if (id != searchText && !$A.strExists(`${name} ${desc}`, searchText)) {
                    return true;
                }
            }
            return false;
        },

        ownerUser(list) {
            return list.filter(({owner}) => owner == 1).sort((a, b) => {
                return a.id - b.id;
            });
        },

        inviteGet(refresh) {
            this.inviteLoad++;
            this.$store.dispatch("call", {
                url: 'project/invite',
                data: {
                    project_id: this.projectId,
                    refresh: refresh === true ? 'yes' : 'no'
                },
            }).then(({data}) => {
                this.inviteData = data;
                this.inviteCopy();
            }).catch(({msg}) => {
                $A.modalError(msg);
            }).finally(_ => {
                this.inviteLoad--;
            });
        },

        getFlowData() {
            this.flowFilter = emptyProjectFlowFilter();
            this.flowFilterOpen = false;
            this.$store.dispatch("call", {
                url: 'project/flow/list',
                data: {
                    project_id: this.projectId
                },
            }).then(({data}) => {
                this.flowList = data;
            }).catch(() => {
                this.flowList = [];
            });
        },

        flowFilterChange(key, item, checked) {
            const values = this.flowFilter[key].filter(value => value !== item.value);
            if (checked) {
                values.push(item.value);
            }
            this.flowFilter = {...this.flowFilter, [key]: values};

            // 判断查看 "已完成流程" 但是没有显示已完成时切换显示已完成
            if (checked && item.status === 'end' && !this.projectData.cacheParameter.completedTask) {
                this.toggleParameter('completedTask')
            }

            this.handleColumnDebounce(true);
        },

        flowFilterClear(key) {
            this.flowFilter = key ? {...this.flowFilter, [key]: []} : emptyProjectFlowFilter();
            this.handleColumnDebounce(true);
        },

        async loadTaskViews() {
            const projectId = this.projectId;
            const requestKey = this._taskViewRequestKey = (this._taskViewRequestKey || 0) + 1;
            this.taskViewsLoading = true;
            this.taskViewsLoadFailed = false;
            try {
                const {data} = await this.$store.dispatch('call', {
                    url: 'projecttaskview/lists',
                    data: {project_id: projectId},
                });
                if (requestKey !== this._taskViewRequestKey || this.projectId !== projectId) return;
                this.taskViews = Array.isArray(data) ? data : [];
                const defaultView = this.taskViews.find(item => item.is_default);
                if (defaultView && this._taskViewAppliedProject !== projectId) {
                    this._taskViewAppliedProject = projectId;
                    this.applyTaskView(defaultView.id);
                } else if (this.selectedTaskViewId && !this.taskViews.some(item => Number(item.id) === Number(this.selectedTaskViewId))) {
                    this.selectedTaskViewId = '';
                }
            } catch (e) {
                console.warn(e);
                if (requestKey === this._taskViewRequestKey && this.projectId === projectId) this.taskViewsLoadFailed = true;
            } finally {
                if (requestKey === this._taskViewRequestKey) this.taskViewsLoading = false;
            }
        },

        captureTaskView() {
            const normalizedRange = Array.isArray(this.taskDateRange)
                ? $A.newDateString(this.taskDateRange, 'YYYY-MM-DD')
                : [];
            return {
                flow: JSON.parse(JSON.stringify(this.flowFilter)),
                milestone_id: Number(this.milestoneFilterId) || 0,
                date_range: normalizedRange[0] && normalizedRange[1]
                    ? {from: normalizedRange[0], to: normalizedRange[1]}
                    : {},
                keyword: this.searchText,
                completed: !!this.projectData.cacheParameter.completedTask,
                tab: this.tabTypeActive,
                sort: {field: this.sortField, type: this.sortType},
                table_groups: [...this.visibleTableGroups],
                table_fields: [...this.tableFields],
            };
        },

        toggleTaskTableField(field) {
            const available = this.taskTableFieldOptions.map(item => item.key);
            if (!available.includes(field)) return;
            const selected = new Set(this.tableFields);
            selected.has(field) ? selected.delete(field) : selected.add(field);
            this.tableFields = available.filter(key => selected.has(key));
            this.handleColumnDebounce(true);
        },

        applyTaskView(viewId) {
            const view = this.taskViews.find(item => Number(item.id) === Number(viewId));
            if (!view) {
                this.selectedTaskViewId = '';
                return;
            }
            this.selectedTaskViewId = view.id;
            this.applyTaskViewDefinition(view.definition || {});
        },

        async applyTaskViewPreset(preset) {
            if (preset === 'near_milestone') {
                const projectId = this.projectId;
                if (this.milestoneLoading) await this.loadMilestones();
                if (projectId !== this.projectId) return;
                const milestone = this.milestoneOverview.next;
                if (!milestone) {
                    $A.messageWarning(this.$L('暂无进行中的里程碑'));
                    return;
                }
                this.selectedTaskViewId = '';
                this.applyTaskViewDefinition({
                    flow: {flows: [], tags: [], users: []},
                    milestone_id: milestone.id,
                    keyword: '',
                    completed: false,
                    tab: 'table',
                    sort: {field: 'end_at', type: 'asc'},
                    table_groups: [],
                });
                return;
            }
            const groups = {
                my_undone: ['my'],
                overdue: ['overdue'],
                due_week: ['dueThisWeek'],
                unscheduled: ['unscheduled'],
                due_next_week: ['dueNextWeek'],
            };
            if (!groups[preset]) return;
            this.selectedTaskViewId = '';
            this.applyTaskViewDefinition({
                flow: {flows: [], tags: [], users: []},
                milestone_id: 0,
                keyword: '',
                completed: false,
                tab: 'table',
                sort: {field: 'end_at', type: 'asc'},
                table_groups: groups[preset],
            });
        },

        applyTaskViewDefinition(definition) {
            const flow = definition.flow || {};
            this.flowFilter = {
                flows: Array.isArray(flow.flows) ? flow.flows : [],
                tags: Array.isArray(flow.tags) ? flow.tags : [],
                users: Array.isArray(flow.users) ? flow.users : [],
            };
            const milestoneId = Number(definition.milestone_id) || 0;
            if (milestoneId && !this.milestoneLoading && !this.milestoneList.some(item => Number(item.id) === milestoneId)) {
                this.milestoneFilterId = null;
                $A.messageWarning(this.$L('视图中的里程碑已不可用，已清除该筛选'));
            } else {
                this.milestoneFilterId = milestoneId || null;
            }
            this.searchText = definition.keyword || '';
            const dateRange = definition.date_range || {};
            this.taskDateRange = typeof dateRange.from === 'string' && typeof dateRange.to === 'string' && dateRange.from <= dateRange.to
                ? [dateRange.from, dateRange.to]
                : [];
            const availableFields = this.taskTableFieldOptions.map(item => item.key);
            this.tableFields = Array.isArray(definition.table_fields)
                ? availableFields.filter(field => definition.table_fields.includes(field))
                : availableFields;
            this.sortField = definition.sort?.field === 'level' ? 'level' : 'end_at';
            this.sortType = definition.sort?.type === 'asc' ? 'asc' : 'desc';
            if (!!this.projectData.cacheParameter.completedTask !== !!definition.completed) {
                this.$store.dispatch('forgetTaskCompleteTemp', true);
            }
            const tableGroups = normalizeProjectTableGroups(definition.table_groups);
            const expandedGroups = Object.fromEntries(this.tableGroupOptions
                .filter(group => tableGroups.includes(group.key))
                .map(group => [group.expandedKey, true]));
            this.$store.dispatch('toggleProjectParameter', {
                project_id: this.projectId,
                key: {
                    completedTask: !!definition.completed,
                    menuType: ['column', 'table', 'gantt'].includes(definition.tab) ? definition.tab : 'column',
                    tableGroups,
                    ...expandedGroups,
                },
            });
            this.handleColumnDebounce(true);
        },

        taskViewCommand(command) {
            if (typeof command === 'string' && command.startsWith('preset_')) {
                this.applyTaskViewPreset(command.slice('preset_'.length));
            } else if (command === 'update' && this.selectedTaskView) {
                const view = this.selectedTaskView;
                const projectId = this.projectId;
                this.saveTaskView(view.name, view.visibility, this.captureTaskView(), view.id, projectId);
            } else if (command === 'save' || command === 'save_shared' || command === 'copy') {
                const source = command === 'copy' ? this.selectedTaskView : null;
                const visibility = command === 'save_shared' ? 'project' : (source?.visibility === 'project' && this.isOwnerOrDeputy ? 'project' : 'personal');
                const projectId = this.projectId;
                const definition = source ? source.definition : this.captureTaskView();
                $A.modalInput({
                    title: command === 'copy' ? this.$L('复制视图') : this.$L('保存任务视图'),
                    placeholder: this.$L('输入视图名称'),
                    value: source ? `${source.name} - ${this.$L('副本')}` : '',
                    onOk: name => {
                        if (Number(projectId) !== Number(this.projectId)) {
                            $A.messageWarning(this.$L('操作失败'));
                            return;
                        }
                        if (!name || !name.trim()) return this.$L('请输入视图名称');
                        return this.saveTaskView(name.trim(), visibility, definition, 0, projectId);
                    },
                });
            } else if (command === 'rename' && this.selectedTaskView) {
                const view = this.selectedTaskView;
                const projectId = this.projectId;
                $A.modalInput({
                    title: this.$L('重命名视图'),
                    value: view.name,
                    placeholder: this.$L('输入视图名称'),
                    onOk: name => {
                        if (Number(projectId) !== Number(this.projectId)) {
                            $A.messageWarning(this.$L('操作失败'));
                            return;
                        }
                        if (!name || !name.trim()) return this.$L('请输入视图名称');
                        return this.saveTaskView(name.trim(), view.visibility, view.definition, view.id, projectId);
                    },
                });
            } else if (command === 'default' && this.selectedTaskView) {
                const projectId = this.projectId;
                const viewId = this.selectedTaskView.id;
                this.$store.dispatch('call', {url: 'projecttaskview/setdefault', data: {project_id: projectId, view_id: viewId}, method: 'post'})
                    .then(({msg}) => {
                        if (this.projectId !== projectId) return;
                        $A.messageSuccess(msg);
                        this.loadTaskViews();
                    })
                    .catch(e => $A.modalError(e));
            } else if (command === 'delete' && this.selectedTaskView) {
                const view = this.selectedTaskView;
                const projectId = this.projectId;
                $A.modalConfirm({content: this.$L('确定删除视图“(*)”吗？', view.name), onOk: () => {
                    if (Number(projectId) !== Number(this.projectId)) return;
                    this.$store.dispatch('call', {url: 'projecttaskview/delete', data: {project_id: projectId, view_id: view.id}, method: 'post'})
                        .then(({msg}) => {
                            if (this.projectId !== projectId) return;
                            $A.messageSuccess(msg);
                            this.selectedTaskViewId = '';
                            this.loadTaskViews();
                        })
                        .catch(e => $A.modalError(e));
                }});
            }
        },

        saveTaskView(name, visibility, definition, viewId = 0, projectId = this.projectId) {
            return this.$store.dispatch('call', {
                url: 'projecttaskview/save',
                data: {project_id: projectId, view_id: viewId, name, visibility, definition},
                method: 'post',
            }).then(({data, msg}) => {
                if (this.projectId !== projectId) return;
                $A.messageSuccess(msg);
                this.loadTaskViews();
                this.selectedTaskViewId = data.id;
            }).catch(e => $A.modalError(e));
        },

        inviteCopy() {
            if (!this.inviteData.url) {
                return;
            }
            this.inviteFocus();
            this.copyText(this.inviteData.url);
        },

        inviteFocus() {
            this.$nextTick(_ => {
                this.$refs.inviteInput.focus({cursor:'all'});
            });
        },

        async taskTagBeforeClose() {
            this.$store.dispatch("getTaskForProject", this.projectId).catch(() => {})
        },

        workflowBeforeClose() {
            return new Promise(resolve => {
                if (!this.$refs.workflow) {
                    resolve();
                    return;
                }
                if (!this.$refs.workflow.existDiff()) {
                    resolve()
                    return
                }
                $A.modalConfirm({
                    content: '设置尚未保存，是否放弃修改？',
                    cancelText: '取消',
                    okText: '放弃',
                    onOk: () => {
                        resolve()
                    }
                });
            })
        },

        myFilter(task, chackCompleted = true) {
            if (!this.projectData.cacheParameter.completedTask && chackCompleted === true) {
                if (task.complete_at) {
                    return false;
                }
            }
            if (this.flowTask(task)) {
                return false;
            }
            if (this.searchText) {
                if (task.id != this.searchText && !$A.strExists(task.name, this.searchText) && !$A.strExists(task.desc, this.searchText)) {
                    return false;
                }
            }
            return task.owner == 1;
        },

        helpFilter(task, chackCompleted = true) {
            if (task.parent_id > 0) {
                return false;
            }
            if (!this.projectData.cacheParameter.completedTask && chackCompleted === true) {
                if (task.complete_at) {
                    return false;
                }
            }
            if (this.flowTask(task)) {
                return false;
            }
            if (this.searchText) {
                if (task.id != this.searchText && !$A.strExists(task.name, this.searchText) && !$A.strExists(task.desc, this.searchText)) {
                    return false;
                }
            }
            return task.task_user && task.task_user.find(({userid, owner}) => userid == this.userId && owner == 0);
        },

        flowTask(task) {
            return !taskMatchesProjectFlowFilter(task, this.flowFilter);
        },

        expiresFormat(date) {
            return $A.countDownFormat(this.nowTime, date)
        },

        setTableGroupVisible(group, visible) {
            const selected = this.visibleTableGroups;
            if (!visible && selected.length === 1 && selected.includes(group.key)) return;
            const tableGroups = visible
                ? this.tableGroupOptions.filter(item => item.key === group.key || selected.includes(item.key)).map(item => item.key)
                : selected.filter(key => key !== group.key);
            this.toggleParameter({
                project_id: this.projectId,
                key: visible ? {tableGroups, [group.expandedKey]: true} : {tableGroups},
            });
            if (visible) this.scrollToTableGroup(group.key);
            else this.cancelTableGroupScroll();
        },

        cancelTableGroupScroll() {
            this._tableGroupScroll?.cancel?.();
            this._tableGroupScroll = null;
        },

        scrollToTableGroup(key) {
            this.cancelTableGroupScroll();
            const projectId = this.projectId;
            const request = {key};
            this._tableGroupScroll = request;
            const isActive = () => this._tableGroupScroll === request && this.projectId === projectId && this.tabTypeActive === 'table' && this.visibleTableGroups.includes(key);
            this.$nextTick(() => {
                if (!isActive()) return;
                const scrollbar = this.$refs.projectTableScroll;
                const container = scrollbar?.scrollElement();
                const target = container?.querySelector(`[data-table-group="${key}"]`);
                if (!target) return;
                request.cancel = scrollToStableTarget({
                    container, target, inputRoot: scrollbar.$el, isActive,
                    render: async () => {
                        await this.handleTaskScroll({target: container});
                        await this.$nextTick();
                    },
                });
            });
        },

        tabTypeChange(type) {
            switch (type) {
                case "column":
                    this.toggleParameter({
                        project_id: this.projectId,
                        key: 'menuType',
                        value: 'column'
                    });
                    break;
                case "table":
                    this.toggleParameter({
                        project_id: this.projectId,
                        key: 'menuType',
                        value: 'table'
                    });
                    break;
                case "gantt":
                    this.toggleParameter({
                        project_id: this.projectId,
                        key: 'menuType',
                        value: 'gantt'
                    });
                    break;
            }
        },

        toggleParameter(data) {
            if (data === 'chat') {
                this.$store.dispatch('openDialog', this.projectData.dialog_id)
                return;
            }
            if (data === 'completedTask') {
                this.$store.dispatch("forgetTaskCompleteTemp", true);
            }
            this.$store.dispatch('toggleProjectParameter', data);
            this.handleColumnDebounce();
        },

        onBack() {
            const {name, params} = this.$store.state.routeHistoryLast;
            if (name === this.routeName && /^\d+$/.test(params.projectId)) {
                this.goForward({name: this.routeName, params: {projectId: 'all'}});
            } else {
                this.goBack();
            }
        },


        taskItemVisible({id, column_id}) {
            return this.columnVisibilitys[column_id] && this.taskVisibilitys[id]?.visible
        },

        taskItemStyle({id, column_id, complete_at, color}) {
            const style = {}
            if (color) {
                style.backgroundColor = color;
            }
            if (!this.taskItemVisible({id, column_id})) {
                style.height = (this.taskVisibilitys[id]?.height || (complete_at ? 46 :146)) + 'px';
            }
            return style;
        },

        handleColumnDebounce(wait = 10, needView = false) {
            if (typeof wait === "boolean") {
                needView = wait
                wait = 10
            }
            if (this.columnDebounceWait !== wait) {
                this.columnDebounceWait = wait;
                if (this.columnDebounceInvoke) {
                    this.columnDebounceInvoke.cancel();
                    this.columnDebounceInvoke = null;
                }
            }
            if (!this.columnDebounceInvoke) {
                this.columnDebounceInvoke = debounce(needView => {
                    this.$nextTick(_ => {
                        switch (this.tabTypeActive) {
                            case 'column':
                                this.handleColumnScroll()
                                if (needView === true) {
                                    // 滚动到第一个可见任务
                                    requestAnimationFrame(() => {
                                        const mainContainer = this.$refs.projectColumn;
                                        if (mainContainer && !mainContainer.querySelector('.task-head')) {
                                            $A.scrollIntoViewIfNeeded(mainContainer.querySelector('.task-draggable:not(.hidden)'))
                                        }
                                    })
                                }
                                break;
                            case 'table':
                                this.handleTaskScroll({target: this.$refs.projectTableScroll?.$el})
                                break;
                        }
                    })
                }, wait);
            }
            this.columnDebounceInvoke(needView);
        },

        handleColumnScroll() {
            this.columnList.forEach(({id}) => {
                const ref = this.$refs[`column_${id}`];
                ref && this.checkColumnVisibility(ref[0])
            })
        },

        checkColumnVisibility(columnContainer = null) {
            if (!columnContainer) {
                return
            }
            const dataId = columnContainer.getAttribute('data-id')
            if (!dataId) {
                return
            }
            const mainContainer = this.$refs.projectColumn;
            if (!mainContainer) {
                return false;
            }

            // 获取容器的边界信息
            const mainRect = mainContainer.getBoundingClientRect();
            const columnRect = columnContainer.getBoundingClientRect();

            // 检查元素是否在容器的可视区域内
            const visible = (
                columnRect.left < mainRect.right &&
                columnRect.right > mainRect.left &&
                columnRect.top < mainRect.bottom &&
                columnRect.bottom > mainRect.top
            )
            if (visible) {
                this.handleTaskScroll({target: columnContainer.querySelector('.task-scrollbar')})
            }
            this.$set(this.columnVisibilitys, dataId, visible)
        },

        async handleTaskScroll({target}) {
            if (!target) {
                return
            }
            const targetItem = target.querySelectorAll('.task-item');
            if (!targetItem.length) {
                return
            }
            let visibleType = null;
            switch (this.tabTypeActive) {
                case 'column':
                    visibleType = 'taskVisibilitys'
                    break;
                case 'table':
                    visibleType = 'taskRowVisibilitys'
                    break;
                default:
                    return
            }
            const targetRect = target.getBoundingClientRect();
            for (const item of targetItem) {
                const dataId = item.getAttribute('data-id');
                if (!dataId) {
                    continue;
                }
                const taskRect = item.getBoundingClientRect();
                const originalVisible = this[visibleType][dataId]?.visible || false;
                const currentVisible = (
                    taskRect.top >= targetRect.top - taskRect.height &&
                    taskRect.bottom <= targetRect.bottom + taskRect.height
                );
                if (currentVisible === originalVisible) {
                    continue;
                }
                const firstVisible = this[visibleType][dataId] === undefined && currentVisible;
                this.$set(this[visibleType], dataId, {
                    visible: currentVisible,
                    height: taskRect.height
                });
                if (firstVisible) {
                    await this.$nextTick();
                }
            }
        },

        /**
         * 切换项目收藏状态
         */
        toggleProjectFavorite() {
            if (!this.projectData.id) return;

            this.$store.dispatch("toggleFavorite", {
                type: 'project',
                id: this.projectData.id
            }).then(({data}) => {
                this.$set(this.projectData, 'favorited', data.favorited);
            });
        },

        /**
         * 检查项目收藏状态
         */
        checkProjectFavoriteStatus() {
            if (!this.projectData.id) return;

            this.$store.dispatch("checkFavoriteStatus", {
                type: 'project',
                id: this.projectData.id
            }).then(({data}) => {
                this.$set(this.projectData, 'favorited', data.favorited || false);
            }).catch(() => {
                this.$set(this.projectData, 'favorited', false);
            });
        },
    }
}
</script>
