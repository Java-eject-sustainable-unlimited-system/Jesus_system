package com.jesus.system.monitor;

import com.jesus.system.config.WatchdogProperties;
import com.jesus.system.core.ProcessLifecycleManager;
import com.jesus.system.core.VitalsRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RemoteJvmDiagnosticsMonitorTest {

    private static final String URL = "service:jmx:rmi:///jndi/rmi://127.0.0.1:9010/jmxrmi";

    private final WatchdogProperties props = new WatchdogProperties();
    private final VitalsRegistry vitals = new VitalsRegistry();
    private final ProcessLifecycleManager lifecycle = mock(ProcessLifecycleManager.class);

    private RemoteJvmDiagnosticsMonitor monitor(RemoteJvmDiagnosticsMonitor.Sampler s) {
        props.setRemoteJmxUrl(URL);
        props.setRemoteJmxTimeout(Duration.ofMillis(200));
        return new RemoteJvmDiagnosticsMonitor(props, vitals, lifecycle, s);
    }

    @Test
    void blankUrlDoesNothing() {
        AtomicInteger calls = new AtomicInteger();
        var m = new RemoteJvmDiagnosticsMonitor(props, vitals, lifecycle, u -> {
            calls.incrementAndGet();
            return null;
        });
        m.diagnoseRemoteJvm();
        assertEquals(0, calls.get());
        verifyNoInteractions(lifecycle);
    }

    @Test
    void ejectsAfterThresholdOfUnreachableFailures() {
        var m = monitor(u -> { throw new java.io.IOException("refused"); });
        for (int i = 0; i < props.getFailureThreshold() - 1; i++) {
            m.diagnoseRemoteJvm();
        }
        verifyNoInteractions(lifecycle);
        m.diagnoseRemoteJvm();
        verify(lifecycle).ejectAndResurrect("REMOTE_JVM_UNHEALTHY");
    }

    @Test
    void hungTargetCountsAsFailure() {
        var m = monitor(u -> { Thread.sleep(2000); return null; });
        for (int i = 0; i < props.getFailureThreshold(); i++) {
            m.diagnoseRemoteJvm();
        }
        verify(lifecycle).ejectAndResurrect("REMOTE_JVM_UNHEALTHY");
    }

    @Test
    void healthyTargetNeverEjects() {
        var m = monitor(u -> new RemoteJvmDiagnosticsMonitor.Snapshot(10, 100, 0));
        for (int i = 0; i < 5; i++) {
            m.diagnoseRemoteJvm();
        }
        verifyNoInteractions(lifecycle);
    }

    @Test
    void deadlockedTargetEjectsAfterThreshold() {
        var m = monitor(u -> new RemoteJvmDiagnosticsMonitor.Snapshot(10, 100, 2));
        for (int i = 0; i < props.getFailureThreshold(); i++) {
            m.diagnoseRemoteJvm();
        }
        verify(lifecycle).ejectAndResurrect("REMOTE_JVM_UNHEALTHY");
    }

    @Test
    void nonLoopbackHostIsRejected() {
        assertFalse(RemoteJvmDiagnosticsMonitor.isLoopback("service:jmx:rmi://127.0.0.1/jndi/rmi://203.0.113.5:9010/x"));
        assertTrue(RemoteJvmDiagnosticsMonitor.isLoopback(URL));
        assertFalse(RemoteJvmDiagnosticsMonitor.isLoopback(
                "service:jmx:rmi:///jndi/rmi://203.0.113.5:9010/jmxrmi"));
    }
}
