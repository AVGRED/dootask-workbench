package com.workbench.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workbench.report.entity.DailyReport;
import com.workbench.report.entity.WeeklySummary;
import com.workbench.report.mapper.DailyReportMapper;
import com.workbench.report.mapper.WeeklySummaryMapper;
import com.workbench.report.status.ReportStatus;
import com.workbench.report.web.ReportDtos.WeeklySummaryView;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 周报：按「用户 + 周一」唯一，重复生成只更新内容（幂等）。
 */
@Service
public class WeeklySummaryService {

    private final DailyReportMapper dailyReportMapper;
    private final WeeklySummaryMapper weeklySummaryMapper;

    public WeeklySummaryService(DailyReportMapper dailyReportMapper, WeeklySummaryMapper weeklySummaryMapper) {
        this.dailyReportMapper = dailyReportMapper;
        this.weeklySummaryMapper = weeklySummaryMapper;
    }

    public static LocalDate mondayOf(LocalDate date) {
        return date.with(DayOfWeek.MONDAY);
    }

    /** 生成指定周（从周一开始）的全部用户周报，返回生成条数。 */
    public int generateWeek(LocalDate weekStart) {
        LocalDate monday = mondayOf(weekStart);
        LocalDate sunday = monday.plusDays(6);

        List<DailyReport> weekReports = dailyReportMapper.selectList(new LambdaQueryWrapper<DailyReport>()
                .between(DailyReport::getReportDate, monday, sunday));
        Set<Long> userIds = new LinkedHashSet<>();
        for (DailyReport report : weekReports) {
            userIds.add(report.getUserid());
        }
        for (Long userid : userIds) {
            generateForUser(userid, monday);
        }
        return userIds.size();
    }

    public WeeklySummaryView generateForUser(long userid, LocalDate weekStart) {
        LocalDate monday = mondayOf(weekStart);
        LocalDate sunday = monday.plusDays(6);
        List<DailyReport> reports = dailyReportMapper.selectList(new LambdaQueryWrapper<DailyReport>()
                .eq(DailyReport::getUserid, userid)
                .between(DailyReport::getReportDate, monday, sunday)
                .orderByAsc(DailyReport::getReportDate));

        int workdays = ReportStatus.workdaysBetween(monday, sunday);
        int totalMinutes = reports.stream()
                .mapToInt(report -> report.getMinutes() == null ? 0 : report.getMinutes())
                .sum();
        int missing = Math.max(0, workdays - reports.size());

        StringBuilder content = new StringBuilder();
        content.append("本周提交 ").append(reports.size()).append(" 篇，缺失 ").append(missing)
                .append(" 天，累计工时 ").append(totalMinutes).append(" 分钟。\n");
        for (DailyReport report : reports) {
            content.append("- ").append(report.getReportDate()).append("：")
                    .append(abbreviate(report.getContentDone(), 80)).append('\n');
        }

        WeeklySummary summary = weeklySummaryMapper.selectOne(new LambdaQueryWrapper<WeeklySummary>()
                .eq(WeeklySummary::getUserid, userid)
                .eq(WeeklySummary::getWeekStart, monday)
                .last("limit 1"));
        LocalDateTime now = LocalDateTime.now();
        if (summary == null) {
            summary = new WeeklySummary();
            summary.setUserid(userid);
            summary.setWeekStart(monday);
            summary.setWeekEnd(sunday);
            summary.setCreatedAt(now);
        }
        summary.setReportCount(reports.size());
        summary.setMissingCount(missing);
        summary.setTotalMinutes(totalMinutes);
        summary.setContent(content.toString());
        summary.setGeneratedAt(now);
        summary.setUpdatedAt(now);
        if (summary.getId() == null) {
            weeklySummaryMapper.insert(summary);
        } else {
            weeklySummaryMapper.updateById(summary);
        }
        return toView(summary);
    }

    public List<WeeklySummaryView> list(long currentUserid) {
        return weeklySummaryMapper.selectList(new LambdaQueryWrapper<WeeklySummary>()
                        .eq(WeeklySummary::getUserid, currentUserid)
                        .orderByDesc(WeeklySummary::getWeekStart))
                .stream()
                .map(WeeklySummaryService::toView)
                .toList();
    }

    private static String abbreviate(String text, int max) {
        if (text == null) return "";
        String flat = text.replace('\n', ' ').trim();
        return flat.length() <= max ? flat : flat.substring(0, max) + "…";
    }

    private static WeeklySummaryView toView(WeeklySummary summary) {
        return new WeeklySummaryView(
                summary.getId(),
                summary.getWeekStart(),
                summary.getWeekEnd(),
                summary.getReportCount() == null ? 0 : summary.getReportCount(),
                summary.getMissingCount() == null ? 0 : summary.getMissingCount(),
                summary.getTotalMinutes() == null ? 0 : summary.getTotalMinutes(),
                summary.getContent());
    }
}
