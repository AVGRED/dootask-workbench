package com.workbench.planning.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workbench.common.api.BusinessException;
import com.workbench.planning.entity.Template;
import com.workbench.planning.entity.TemplateRevision;
import com.workbench.planning.mapper.TemplateMapper;
import com.workbench.planning.mapper.TemplateRevisionMapper;
import com.workbench.planning.web.TemplateDtos.ApplyResult;
import com.workbench.planning.web.TemplateDtos.RevisionView;
import com.workbench.planning.web.TemplateDtos.SaveTemplateRequest;
import com.workbench.planning.web.TemplateDtos.TemplateView;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 任务模板：创建/更新时追加版本快照，可一键应用为任务。
 *
 * <p>快照存 JSON 文本，便于后续做差异对比与回滚（当前只读展示）。</p>
 */
@Service
public class TemplateService {

    private final TemplateMapper templateMapper;
    private final TemplateRevisionMapper revisionMapper;
    private final JdbcTemplate jdbcTemplate;

    public TemplateService(TemplateMapper templateMapper, TemplateRevisionMapper revisionMapper,
                           JdbcTemplate jdbcTemplate) {
        this.templateMapper = templateMapper;
        this.revisionMapper = revisionMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<TemplateView> list(long projectId) {
        List<Template> templates = templateMapper.selectList(new LambdaQueryWrapper<Template>()
                .eq(Template::getProjectId, projectId)
                .orderByDesc(Template::getId));
        if (templates.isEmpty()) {
            return List.of();
        }
        List<Long> ids = templates.stream().map(Template::getId).toList();
        Map<Long, List<TemplateRevision>> revisionsByTemplate = revisionMapper.selectList(
                        new LambdaQueryWrapper<TemplateRevision>().in(TemplateRevision::getTemplateId, ids))
                .stream()
                .collect(Collectors.groupingBy(TemplateRevision::getTemplateId));
        return templates.stream()
                .map(template -> {
                    List<TemplateRevision> revisions =
                            revisionsByTemplate.getOrDefault(template.getId(), List.of());
                    int latest = revisions.stream()
                            .mapToInt(revision -> revision.getRevisionNo() == null ? 0 : revision.getRevisionNo())
                            .max()
                            .orElse(0);
                    return new TemplateView(template.getId(), template.getName(), template.getContent(),
                            template.getMilestoneName(), latest, revisions.size(), template.getUpdatedAt());
                })
                .toList();
    }

    @Transactional
    public TemplateView create(long currentUserid, long projectId, SaveTemplateRequest request) {
        LocalDateTime now = LocalDateTime.now();
        Template template = new Template();
        template.setProjectId(projectId);
        template.setName(request.name());
        template.setContent(request.content() == null ? "" : request.content());
        template.setMilestoneName(request.milestoneName() == null ? "" : request.milestoneName());
        template.setCreatedBy(currentUserid);
        template.setCreatedAt(now);
        template.setUpdatedAt(now);
        templateMapper.insert(template);
        appendRevision(template, 1, currentUserid, now);
        return new TemplateView(template.getId(), template.getName(), template.getContent(),
                template.getMilestoneName(), 1, 1, now);
    }

    @Transactional
    public TemplateView update(long currentUserid, long id, SaveTemplateRequest request) {
        Template template = require(id);
        LocalDateTime now = LocalDateTime.now();
        template.setName(request.name());
        if (request.content() != null) template.setContent(request.content());
        if (request.milestoneName() != null) template.setMilestoneName(request.milestoneName());
        template.setUpdatedAt(now);
        templateMapper.updateById(template);

        int nextRevision = currentRevisionNo(id) + 1;
        appendRevision(template, nextRevision, currentUserid, now);
        long count = revisionMapper.selectCount(new LambdaQueryWrapper<TemplateRevision>()
                .eq(TemplateRevision::getTemplateId, id));
        return new TemplateView(template.getId(), template.getName(), template.getContent(),
                template.getMilestoneName(), nextRevision, count, now);
    }

    public List<RevisionView> revisions(long id) {
        require(id);
        return revisionMapper.selectList(new LambdaQueryWrapper<TemplateRevision>()
                        .eq(TemplateRevision::getTemplateId, id)
                        .orderByDesc(TemplateRevision::getRevisionNo))
                .stream()
                .map(revision -> new RevisionView(revision.getId(),
                        revision.getRevisionNo() == null ? 0 : revision.getRevisionNo(),
                        revision.getSnapshot(), revision.getCreatedAt()))
                .toList();
    }

    /** 应用模板：在当前项目下新建一个任务（名称取模板名，描述取模板内容）。 */
    @Transactional
    public ApplyResult apply(long currentUserid, long id) {
        Template template = require(id);
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                "insert into project_tasks (project_id, name, `desc`, userid, owner_userid, sort, "
                        + "created_at, updated_at) values (?, ?, ?, ?, 0, 0, ?, ?)",
                template.getProjectId(), template.getName(), template.getContent(), currentUserid, now, now);
        Long taskId = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        return new ApplyResult(taskId == null ? 0 : taskId, template.getName());
    }

    @Transactional
    public void delete(long id) {
        require(id);
        templateMapper.deleteById(id);
    }

    private Template require(long id) {
        Template template = templateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException("模板不存在");
        }
        return template;
    }

    private int currentRevisionNo(long templateId) {
        return revisionMapper.selectList(new LambdaQueryWrapper<TemplateRevision>()
                        .eq(TemplateRevision::getTemplateId, templateId))
                .stream()
                .mapToInt(revision -> revision.getRevisionNo() == null ? 0 : revision.getRevisionNo())
                .max()
                .orElse(0);
    }

    private void appendRevision(Template template, int revisionNo, long currentUserid, LocalDateTime now) {
        TemplateRevision revision = new TemplateRevision();
        revision.setTemplateId(template.getId());
        revision.setRevisionNo(revisionNo);
        revision.setSnapshot(snapshotOf(template));
        revision.setCreatedBy(currentUserid);
        revision.setCreatedAt(now);
        revisionMapper.insert(revision);
    }

    private static String snapshotOf(Template template) {
        return "{\"name\":\"" + escape(template.getName()) + "\",\"content\":\"" + escape(template.getContent())
                + "\",\"milestoneName\":\"" + escape(template.getMilestoneName()) + "\"}";
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    /** 供测试：判断两个模板编号是否连续的版本。 */
    public static boolean isNextRevision(int previous, int candidate) {
        return Objects.equals(candidate, previous + 1);
    }
}
