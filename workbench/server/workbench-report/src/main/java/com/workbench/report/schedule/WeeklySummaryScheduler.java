package com.workbench.report.schedule;

import com.workbench.report.service.WeeklySummaryService;
import java.time.LocalDate;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 每周一 02:00（服务器时区）汇总上一自然周。 */
@Component
public class WeeklySummaryScheduler {

    private static final Logger log = LoggerFactory.getLogger(WeeklySummaryScheduler.class);

    private final WeeklySummaryService weeklySummaryService;

    public WeeklySummaryScheduler(WeeklySummaryService weeklySummaryService) {
        this.weeklySummaryService = weeklySummaryService;
    }

    @Scheduled(cron = "0 0 2 * * MON")
    public void generateLastWeek() {
        LocalDate lastMonday = LocalDate.now(ZoneId.systemDefault()).minusWeeks(1)
                .with(java.time.DayOfWeek.MONDAY);
        int count = weeklySummaryService.generateWeek(lastMonday);
        log.info("weekly summaries generated for week {}: {} user(s)", lastMonday, count);
    }
}
