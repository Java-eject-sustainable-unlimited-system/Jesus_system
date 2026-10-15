package com.jesus.system.core;

import com.jesus.system.config.WatchdogProperties;
import org.junit.jupiter.api.Test;

import java.io.IOException;
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
    void preventsDuplicateRecoveryRequestsUntilSpawnedProcessExits() {
        CompletableFuture<Process> processExit = new CompletableFuture<>();
        Process process = mock(Process.class);
        when(process.onExit()).thenReturn(processExit);
        AtomicInteger launches = new AtomicInteger();
        AtomicInteger processScans = new AtomicInteger();
        ProcessLifecycleManager guardedManager = new ProcessLifecycleManager(properties, target -> {
            launches.incrementAndGet();
            return process;
        }) {
            @Override
            public List<ProcessHandle> findTargetProcesses() {
                processScans.incrementAndGet();
                return List.of();
            }
        };

        assertThat(guardedManager.ejectAndResurrect("test").accepted()).isTrue();
        var duplicate = guardedManager.ejectAndResurrect("duplicate");
        assertThat(duplicate.accepted()).isFalse();
        assertThat(duplicate.processesTerminated()).isZero();
        assertThat(guardedManager.resurrect()).isFalse();
        assertThat(launches).hasValue(1);
        assertThat(processScans).hasValue(1);

        processExit.complete(process);

        assertThat(guardedManager.resurrect()).isTrue();
        assertThat(launches).hasValue(2);
    }

    @Test
    void releasesRecoveryGuardWhenProcessCannotStart() {
        AtomicInteger launches = new AtomicInteger();
        ProcessLifecycleManager retryingManager = new ProcessLifecycleManager(properties, target -> {
            if (launches.getAndIncrement() == 0) {
                throw new IOException("Nix unavailable");
            }
            Process process = mock(Process.class);
            when(process.onExit()).thenReturn(new CompletableFuture<>());
            return process;
        }) {
            @Override
            public List<ProcessHandle> findTargetProcesses() {
                return List.of();
            }
        };

        var failed = retryingManager.ejectAndResurrect("test");
        assertThat(failed.accepted()).isTrue();
        assertThat(failed.resurrectionTriggered()).isFalse();
        assertThat(retryingManager.ejectAndResurrect("retry").resurrectionTriggered()).isTrue();
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
}
