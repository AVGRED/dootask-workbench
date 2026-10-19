package com.workbench.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workbench.common.api.BusinessException;
import com.workbench.report.entity.DailyReport;
import com.workbench.report.mapper.DailyReportMapper;
import com.workbench.report.status.ReportStatus;
import com.workbench.report.web.ReportDtos.DailyReportView;
import com.workbench.report.web.ReportDtos.SaveDailyReportRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * 日报：按「用户 + 日期」唯一提交（重复提交即覆盖），7 天内可补交。
 */
@Service
public class DailyReportService {

    private final DailyReportMapper dailyReportMapper;

    public DailyReportService(DailyReportMapper dailyReportMapper) {
        this.dailyReportMapper = dailyReportMapper;
    }

    public DailyReportView save(long currentUserid, LocalDate reportDate, SaveDailyReportRequest request) {
        LocalDateTime now = LocalDateTime.now();
        if (reportDate == null) {
            throw new BusinessException("请选择日报日期");
        }
        if (reportDate.isAfter(now.toLocalDate())) {
            throw new BusinessException("不能提交未来日期的日报");
        }
        if (!ReportStatus.withinBackfillWindow(reportDate, now)) {
            throw new BusinessException("超过 " + ReportStatus.BACKFILL_DAYS + " 天补交窗口");
        }
        int status = ReportStatus.evaluate(reportDate, now);

        DailyReport existing = dailyReportMapper.selectOne(new LambdaQueryWrapper<DailyReport>()
                .eq(DailyReport::getUserid, currentUserid)
                .eq(DailyReport::getReportDate, reportDate)
                .last("limit 1"));
        if (existing != null) {
            existing.setContentDone(request.contentDone());
            existing.setContentBlocker(request.contentBlocker() == null ? "" : request.contentBlocker());
            existing.setContentPlan(request.contentPlan() == null ? "" : request.contentPlan());
            existing.setMinutes(request.minutes());
            existing.setStatus(status);
            existing.setSubmittedAt(now);
            existing.setUpdatedAt(now);
            dailyReportMapper.updateById(existing);
            return toView(existing);
        }

        DailyReport report = new DailyReport();
        report.setUserid(currentUserid);
        report.setReportDate(reportDate);
        report.setContentDone(request.contentDone());
        report.setContentBlocker(request.contentBlocker() == null ? "" : request.contentBlocker());
        report.setContentPlan(request.contentPlan() == null ? "" : request.contentPlan());
        report.setMinutes(request.minutes());
        report.setStatus(status);
        report.setSubmittedAt(now);
        report.setCreatedAt(now);
        report.setUpdatedAt(now);
        dailyReportMapper.insert(report);
        return toView(report);
    }

    public DailyReportView byDate(long currentUserid, LocalDate reportDate) {
        DailyReport report = dailyReportMapper.selectOne(new LambdaQueryWrapper<DailyReport>()
                .eq(DailyReport::getUserid, currentUserid)
                .eq(DailyReport::getReportDate, reportDate)
                .last("limit 1"));
        return report == null ? null : toView(report);
    }

    public List<DailyReportView> range(long currentUserid, LocalDate from, LocalDate to) {
        LocalDate safeFrom = from == null ? LocalDate.now().minusDays(30) : from;
        LocalDate safeTo = to == null ? LocalDate.now() : to;
        return dailyReportMapper.selectList(new LambdaQueryWrapper<DailyReport>()
                        .eq(DailyReport::getUserid, currentUserid)
                        .between(DailyReport::getReportDate, safeFrom, safeTo)
                        .orderByDesc(DailyReport::getReportDate))
                .stream()
                .map(DailyReportService::toView)
                .toList();
    }

    public void delete(long currentUserid, long id) {
        DailyReport report = dailyReportMapper.selectById(id);
        if (report == null || !Objects.equals(report.getUserid(), currentUserid)) {
            throw new BusinessException("日报不存在或无权删除");
        }
        dailyReportMapper.deleteById(id);
    }

    private static DailyReportView toView(DailyReport report) {
        return new DailyReportView(
                report.getId(),
                report.getReportDate(),
                report.getContentDone(),
                report.getContentBlocker(),
                report.getContentPlan(),
                report.getMinutes(),
                report.getStatus() == null ? ReportStatus.ON_TIME : report.getStatus(),
                ReportStatus.text(report.getStatus() == null ? ReportStatus.ON_TIME : report.getStatus()));
    }
}
