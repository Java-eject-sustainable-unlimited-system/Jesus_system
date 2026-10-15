package com.jesus.system;

import com.jesus.system.api.WatchdogCommandController;
import com.jesus.system.config.WatchdogProperties;
import com.jesus.system.core.ProcessLifecycleManager;
import com.jesus.system.core.VitalsRegistry;
import com.jesus.system.health.WatchdogHealthIndicator;
import com.jesus.system.monitor.WatchdogAgent;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the watchdog is adoptable by an arbitrary Spring Boot 3 host:
 * with the JAR on the classpath, the auto-configuration alone must
 * register the whole watchdog stack — no @SpringBootApplication changes
 * required in the host.
 */
class JesusWatchdogAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JesusWatchdogAutoConfiguration.class));

    @Test
    void doesNotRegisterDestructiveWatchdogStackWithoutExplicitOptIn() {
        contextRunner.run(context -> assertThat(context)
                .doesNotHaveBean(ProcessLifecycleManager.class)
                .doesNotHaveBean(WatchdogAgent.class)
                .doesNotHaveBean(WatchdogCommandController.class));
    }

    @Test
    void registersFullWatchdogStackWhenExplicitlyEnabled() {
        contextRunner
                .withPropertyValues("jesus.watchdog.enabled=true")
                .run(context -> assertThat(context)
                .hasSingleBean(WatchdogProperties.class)
                .hasSingleBean(ProcessLifecycleManager.class)
                .hasSingleBean(VitalsRegistry.class)
                .hasSingleBean(ThreadPoolTaskScheduler.class)
                .hasSingleBean(WatchdogAgent.class)
                .hasSingleBean(WatchdogHealthIndicator.class)
                .hasSingleBean(WatchdogCommandController.class)
                .satisfies(ignored -> assertThat(context.getBean(ThreadPoolTaskScheduler.class).getPoolSize())
                        .isEqualTo(5)));
    }

    @Test
    void canBeDisabledViaProperty() {
        contextRunner
                .withPropertyValues("jesus.watchdog.enabled=false")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(ProcessLifecycleManager.class)
                        .doesNotHaveBean(WatchdogAgent.class));
    }
}
