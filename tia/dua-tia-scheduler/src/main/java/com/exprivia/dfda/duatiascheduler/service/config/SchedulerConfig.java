package com.exprivia.dfda.duatiascheduler.service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SchedulerConfig {
    @Value("${dua.tia.scheduler.polling-period-sec}")
    private Integer pollingPeriod;

    public Integer getPollingPeriod() {
        return pollingPeriod;
    }
}
