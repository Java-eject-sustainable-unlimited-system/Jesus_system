package com.jesus.system.core;

import com.jesus.system.config.WatchdogProperties;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
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
                "java", List.of("/opt/egovframe/egov-app.war")))
                .isTrue();

        assertThat(ProcessLifecycleManager.matchesTarget(
                "java", new String[]{"tomcat"},
                "java", List.of("tomcat")))
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
                "JAVA", new String[]{"-jar", "EGOV-APP.war"}, "java", List.of("egov-app.war")))
                .isTrue();
    }

    @Test
    void rejectsArgumentSubstringsAndCanRequireAnExactExecutablePath() {
        assertThat(ProcessLifecycleManager.matchesTarget(
                "java", new String[]{"-Dcatalina.base=/opt/tomcat"}, "java", List.of("tomcat")))
                .isFalse();
        assertThat(ProcessLifecycleManager.matchesTarget(
                "/opt/jdk/bin/java", new String[]{"-jar", "/opt/egov/app.war"},
                "/usr/bin/java", List.of("/opt/egov/app.war")))
                .isFalse();
        assertThat(ProcessLifecycleManager.matchesTarget(
                "/usr/bin/java", new String[]{"-jar", "/opt/egov/app.war"},
                "/usr/bin/java", List.of("/opt/egov/app.war")))
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
    void serializesEjectionAndResurrectionAsOneLifecycleOperation() throws Exception {
        CountDownLatch launchStarted = new CountDownLatch(1);
        CountDownLatch allowLaunch = new CountDownLatch(1);
        Process process = mock(Process.class);
        when(process.onExit()).thenReturn(new CompletableFuture<>());
        ProcessLifecycleManager guardedManager = new ProcessLifecycleManager(properties, target -> {
            launchStarted.countDown();
            try {
                if (!allowLaunch.await(2, TimeUnit.SECONDS)) {
                    throw new IOException("Timed out waiting to release test launch");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(e);
            }
            return process;
        });
        FutureTask<ProcessLifecycleManager.EjectionReport> firstRecovery =
                new FutureTask<>(() -> guardedManager.ejectAndResurrect("first"));
        Thread recoveryThread = new Thread(firstRecovery);
        recoveryThread.start();

        assertThat(launchStarted.await(1, TimeUnit.SECONDS)).isTrue();
        ProcessLifecycleManager.EjectionReport overlapping =
                guardedManager.ejectAndResurrect("overlapping");

        assertThat(overlapping.operationAccepted()).isFalse();
        allowLaunch.countDown();
        ProcessLifecycleManager.EjectionReport first = firstRecovery.get(1, TimeUnit.SECONDS);
        assertThat(first.operationAccepted()).isTrue();
        assertThat(first.terminationSuccessful()).isTrue();
        assertThat(first.resurrectionTriggered()).isTrue();
    }

    @Test
    void doesNotResurrectWhenTargetTerminationIsUnconfirmed() {
        ProcessHandle target = mock(ProcessHandle.class);
        when(target.pid()).thenReturn(42L);
        when(target.destroyForcibly()).thenThrow(new UnsupportedOperationException("termination denied"));
        AtomicInteger launches = new AtomicInteger();
        ProcessLifecycleManager guardedManager = new ProcessLifecycleManager(properties, targetName -> {
            launches.incrementAndGet();
            return mock(Process.class);
        }) {
            @Override
            public List<ProcessHandle> findTargetProcesses() {
                return List.of(target);
            }
        };

        ProcessLifecycleManager.EjectionReport report = guardedManager.ejectAndResurrect("failed termination");

        assertThat(report.operationAccepted()).isTrue();
        assertThat(report.terminationSuccessful()).isFalse();
        assertThat(report.resurrectionTriggered()).isFalse();
        assertThat(launches).hasValue(0);
    }
}
