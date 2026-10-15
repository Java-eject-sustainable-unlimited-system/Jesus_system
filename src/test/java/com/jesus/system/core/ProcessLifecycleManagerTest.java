package com.jesus.system.core;

import com.jesus.system.config.WatchdogProperties;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
                "java", List.of("egov-app.war", "tomcat")))
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
                "java", List.of("egov-app.war")))
                .isFalse();

        assertThat(ProcessLifecycleManager.matchesTarget(
                "java", new String[]{"-jar", "/opt/not-egov-app.war"},
                "java", List.of("egov-app.war")))
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
                "JAVA", new String[]{"-jar", "/opt/EGOV-APP.war"},
                "java", List.of("egov-app.war")))
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
    void rejectsConcurrentRecoveryAndDirectEjectionWhileRecoveryIsRunning() throws Exception {
        CountDownLatch launchStarted = new CountDownLatch(1);
        CountDownLatch allowLaunch = new CountDownLatch(1);
        CompletableFuture<Process> processExit = new CompletableFuture<>();
        Process process = mock(Process.class);
        when(process.onExit()).thenReturn(processExit);
        ProcessLifecycleManager guardedManager = new ProcessLifecycleManager(properties, target -> {
            launchStarted.countDown();
            try {
                if (!allowLaunch.await(5, TimeUnit.SECONDS)) {
                    throw new IOException("Timed out waiting to continue test launch");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting to continue test launch", e);
            }
            return process;
        });

        CompletableFuture<ProcessLifecycleManager.EjectionReport> first =
                CompletableFuture.supplyAsync(() -> guardedManager.ejectAndResurrect("first"));
        try {
            assertThat(launchStarted.await(5, TimeUnit.SECONDS)).isTrue();
            ProcessLifecycleManager.EjectionReport duplicate =
                    guardedManager.ejectAndResurrect("duplicate");

            assertThat(duplicate.accepted()).isFalse();
            assertThat(guardedManager.ejectTargetProcesses("direct")).isZero();
        } finally {
            allowLaunch.countDown();
        }

        assertThat(first.get(5, TimeUnit.SECONDS).resurrectionTriggered()).isTrue();
    }
}
