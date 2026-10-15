package com.jesus.system.core;

import com.jesus.system.config.WatchdogProperties;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProcessLifecycleManagerTest {

    private final WatchdogProperties properties = new WatchdogProperties();
    private final ProcessLifecycleManager manager = new ProcessLifecycleManager(properties);

    @Test
    void matchesLegacyJavaProcess() {
        assertThat(ProcessLifecycleManager.matchesTarget(
                "/usr/bin/java", new String[]{"-jar", "/opt/egovframe/egov-app.war"},
                "java", List.of("egov", "tomcat")))
                .isTrue();

        assertThat(ProcessLifecycleManager.matchesTarget(
                "java", new String[]{"-Dcatalina.base=/opt/tomcat"},
                "java", List.of("egov", "tomcat")))
                .isTrue();
    }

    @Test
    void rejectsUnrelatedProcesses() {
        // Not a JVM process at all
        assertThat(ProcessLifecycleManager.matchesTarget(
                "/sbin/launchd", new String[]{}, "java", List.of("egov")))
                .isFalse();

        // A JVM, but not the legacy target (e.g. the watchdog itself)
        assertThat(ProcessLifecycleManager.matchesTarget(
                "java", new String[]{"-jar", "jesus-watchdog-agent.jar"},
                "java", List.of("egov")))
                .isFalse();
    }

    @Test
    void rejectsMissingOrBlankTargetKeywords() {
        assertThat(ProcessLifecycleManager.matchesTarget(
                "java", new String[]{"-Dapp=unrelated"}, "java", Arrays.asList("", "  ", null)))
                .isFalse();
        assertThat(ProcessLifecycleManager.matchesTarget(
                "java", new String[]{"-Dapp=egov"}, "java", null))
                .isFalse();
        assertThat(ProcessLifecycleManager.matchesTarget(
                "java", new String[]{"-Dapp=egov"}, "  ", List.of("egov")))
                .isFalse();
    }

    @Test
    void matchingIsCaseInsensitive() {
        assertThat(ProcessLifecycleManager.matchesTarget(
                "JAVA", new String[]{"-jar", "EGOV-APP.war"}, "java", List.of("egov")))
                .isTrue();
    }

    @Test
    void neverTargetsOwnJvm() {
        // The self-protection guard must hold on any machine, under any config.
        assertThat(manager.findTargetProcesses())
                .noneMatch(process -> process.pid() == ProcessHandle.current().pid());
    }

    @Test
    void preventsDuplicateResurrectionsUntilSpawnedProcessExits() {
        CompletableFuture<Process> processExit = new CompletableFuture<>();
        Process process = mock(Process.class);
        when(process.onExit()).thenReturn(processExit);
        AtomicInteger launches = new AtomicInteger();
        ProcessLifecycleManager guardedManager = new ProcessLifecycleManager(properties, target -> {
            launches.incrementAndGet();
            return process;
        });

        assertThat(guardedManager.resurrect()).isTrue();
        assertThat(guardedManager.resurrect()).isFalse();
        assertThat(launches).hasValue(1);

        processExit.complete(process);

        assertThat(guardedManager.resurrect()).isTrue();
        assertThat(launches).hasValue(2);
    }

    @Test
    void releasesResurrectionGuardWhenProcessCannotStart() {
        AtomicInteger launches = new AtomicInteger();
        ProcessLifecycleManager retryingManager = new ProcessLifecycleManager(properties, target -> {
            if (launches.getAndIncrement() == 0) {
                throw new IOException("Nix unavailable");
            }
            Process process = mock(Process.class);
            when(process.onExit()).thenReturn(new CompletableFuture<>());
            return process;
        });

        assertThat(retryingManager.resurrect()).isFalse();
        assertThat(retryingManager.resurrect()).isTrue();
    }

    @Test
    void throttlesRecoveriesWithinCooldownAndAfterWindowLimit() {
        MutableClock clock = new MutableClock();
        AtomicInteger launches = new AtomicInteger();
        WatchdogProperties props = new WatchdogProperties();
        props.setTargetProcessKeywords(List.of("no-such-target-" + System.nanoTime()));
        props.setRecoveryCooldown(Duration.ofSeconds(30));
        props.setRecoveryWindow(Duration.ofMinutes(10));
        props.setMaxRecoveriesPerWindow(2);
        ProcessLifecycleManager limited = new ProcessLifecycleManager(props, target -> {
            launches.incrementAndGet();
            Process process = mock(Process.class);
            when(process.onExit()).thenReturn(CompletableFuture.completedFuture(process));
            return process;
        }, clock);

        assertThat(limited.ejectAndResurrect("one").throttled()).isFalse();
        clock.advance(Duration.ofSeconds(5));
        assertThat(limited.ejectAndResurrect("inside cooldown").throttled()).isTrue();
        clock.advance(Duration.ofSeconds(60));
        assertThat(limited.ejectAndResurrect("two").throttled()).isFalse();
        clock.advance(Duration.ofSeconds(60));
        var blocked = limited.ejectAndResurrect("limit reached");
        assertThat(blocked.throttled()).isTrue();
        assertThat(blocked.resurrectionTriggered()).isFalse();
        assertThat(launches).hasValue(2);

        clock.advance(Duration.ofMinutes(11));
        assertThat(limited.ejectAndResurrect("window elapsed").throttled()).isFalse();
        assertThat(launches).hasValue(3);
    }

    @Test
    void refusesToKillWhenTooManyProcessesMatch() {
        WatchdogProperties props = new WatchdogProperties();
        props.setMaxTargetProcesses(1);
        AtomicInteger launches = new AtomicInteger();
        ProcessHandle a = mock(ProcessHandle.class);
        ProcessHandle b = mock(ProcessHandle.class);
        ProcessLifecycleManager broad = new ProcessLifecycleManager(props, t -> {
            launches.incrementAndGet();
            return mock(Process.class);
        }) {
            @Override
            public List<ProcessHandle> findTargetProcesses() {
                return List.of(a, b);
            }
        };

        var report = broad.ejectAndResurrect("test");

        assertThat(report.throttled()).isTrue();
        assertThat(report.processesTerminated()).isZero();
        assertThat(launches).hasValue(0);
        org.mockito.Mockito.verify(a, org.mockito.Mockito.never()).destroyForcibly();
        org.mockito.Mockito.verify(b, org.mockito.Mockito.never()).destroyForcibly();
    }

    @Test
    void dryRunKillsAndRespawnsNothing() {
        WatchdogProperties props = new WatchdogProperties();
        props.setDryRun(true);
        AtomicInteger launches = new AtomicInteger();
        ProcessHandle a = mock(ProcessHandle.class);
        ProcessLifecycleManager dry = new ProcessLifecycleManager(props, t -> {
            launches.incrementAndGet();
            return mock(Process.class);
        }) {
            @Override
            public List<ProcessHandle> findTargetProcesses() {
                return List.of(a);
            }
        };

        var report = dry.ejectAndResurrect("test");

        assertThat(report.processesTerminated()).isZero();
        assertThat(report.resurrectionTriggered()).isFalse();
        assertThat(launches).hasValue(0);
        org.mockito.Mockito.verify(a, org.mockito.Mockito.never()).destroyForcibly();
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
