package com.jesus.system;

import com.jesus.system.config.WatchdogProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Spring Boot starter entry point: makes J.E.S.U.S adoptable by any
 * Spring Boot 3 application (e.g. a modernized eGovFrame service).
 *
 * <p>Adding this JAR to the classpath is enough — all watchdog monitors,
 * the lifecycle core, the health indicator and the REST API are registered
 * automatically when {@code jesus.watchdog.enabled=true}; destructive
 * monitoring is disabled unless the host explicitly opts in.
 *
 * <p>Note for javax-era eGovFrame (Spring Boot 2.x) hosts: the Jakarta
 * namespace makes direct embedding impossible — keep running the watchdog
 * in standalone (sidecar) mode for those systems.
 */
@AutoConfiguration
@EnableScheduling
@EnableConfigurationProperties(WatchdogProperties.class)
@ComponentScan(basePackages = {
        "com.jesus.system.core",
        "com.jesus.system.monitor",
        "com.jesus.system.api",
        "com.jesus.system.health"
})
@ConditionalOnProperty(prefix = "jesus.watchdog", name = "enabled", havingValue = "true")
public class JesusWatchdogAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(JesusWatchdogAutoConfiguration.class);

    @Bean(name = "taskScheduler")
    @ConditionalOnMissingBean(TaskScheduler.class)
    ThreadPoolTaskScheduler watchdogTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5);
        scheduler.setThreadNamePrefix("jesus-watchdog-");
        scheduler.setErrorHandler(error -> log.error("Scheduled watchdog task failed", error));
        return scheduler;
    }
}
